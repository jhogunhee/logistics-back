# WMS Backend

입고 → 재고 → 출고 전 구간을 다루는 창고관리시스템(WMS) 백엔드.

물류 실무에서 결과로만 받던 입출고·재고를 만드는 쪽에서 설계했다. 상태 전이, 재고 이력 모델,
할당 동시성 같은 프로세스 판단에 무게를 뒀고, 각 판단의 근거는 [docs/design.md](docs/design.md)에 적어 뒀다.

- 데모: https://wareflow-27p.pages.dev/ (프론트엔드)
- 프론트엔드 레포: https://github.com/jhogunhee/logistics-front

> 백엔드가 무료 플랜이라 유휴 시 잠든다. 첫 접속은 서버 기동에 1분 정도 걸린다.

## 기술 스택

- Java 17, Spring Boot 3.5
- Spring Data JPA + QueryDSL 5
- PostgreSQL (Supabase)
- p6spy (SQL 로그)

## 실행

PostgreSQL이 필요하다. [docker-compose.yml](docker-compose.yml)로 로컬 인스턴스를 띄우면
[docs/schema.sql](docs/schema.sql)과 [docs/seed-dev.sql](docs/seed-dev.sql)이 최초 기동 때 함께 적용된다.

```bash
docker compose up -d          # PostgreSQL (스키마 · 시드 자동 적용)
./mvnw spring-boot:run        # http://localhost:8080
```

Supabase 같은 원격 DB를 쓰려면 스키마와 시드를 직접 적용하고 접속 정보를 환경변수로 넘긴다.
기본값은 `application.properties`에 있다.

| 환경변수 | 기본값 |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/postgres` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | (빈 값) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` 외 |

`spring.jpa.hibernate.ddl-auto=none` — **Hibernate는 테이블을 만들지도 바꾸지도 않는다.**
스키마의 주인은 [docs/schema.sql](docs/schema.sql)이고 엔티티를 거기에 맞춘다. 이미 만들어 둔 DB는
`docs/migration-*.sql`로 따라잡는다.

QueryDSL `Q*` 타입은 애노테이션 프로세서가 만든다. 엔티티를 추가하거나 바꿨으면 `./mvnw compile`을 먼저 돌린다.

```bash
./mvnw compile                # Q-class 재생성
./mvnw test                   # 테스트
./mvnw clean package          # jar 빌드
```

## 프로젝트 구조

한 레포에 네 층이 있고 의존은 한 방향으로만 흐른다.

```
common  ←  mdm  ←  wmsback  ←  omsback
            ↖─────────────────────┘
```

| 패키지 | 내용 |
|---|---|
| `com.project.common` | 감사 엔티티 · 설정 · 공통 예외 핸들러. 어느 앱도 import하지 않는다 |
| `com.project.mdm` | 두 앱이 공유하는 마스터 — 상품 · 거래처 · 점포 · 공통코드 · 채번 |
| `com.project.wmsback` | 창고 작업 — 로케이션/존/Lot · 입고 · 재고 · 출고 · 전략 |
| `com.project.omsback` | 창고 작업문서를 발생시키는 주문 원장 |

`mdm`은 `wmsback`·`omsback`을 모르고 `wmsback`은 `omsback`을 모른다. 나중에 앱을 떼어낼 수 있게 한 구성이다.
아래 층이 위 층의 데이터를 봐야 하면 포트를 두고 뒤집는다.

각 도메인 패키지는 `controller / dto / entity / repository / service`를 따른다. 동적 쿼리는
`XxxRepository` · `XxxRepositoryCustom` · `XxxRepositoryImpl` 세 파일로 나눠 QueryDSL로 쓴다.

## 구현 범위

테이블 44개, 전략 4종.

| 영역 | 내용 |
|---|---|
| 기준정보 | 상품 · 계량단위/환산 · 거래처 · 점포 · 존 · 로케이션 · 고정 로케이션 · 공통코드 · 채번 |
| 주문(OMS) | 입고주문 · 출고주문 등록/확정, 반품 입고 |
| 입고 | 입고예정(ASN) · 검수 · 적치지시 · 적치 · 입고확정 |
| 재고 | 현재고 · 재고이력 · 이동지시 · 보류 · 실사 · Lot 속성변경/로트변경 · 정기보충 |
| 출고 | 웨이브 편성 · 할당 · 피킹지시 · 수시보충 · 피킹 · 출고확정 |
| 전략 | 검수 · 적치 · 웨이브 · 할당 + 리비전 이력 · 실행 로그 |

API는 도메인별로 묶여 있다 — `/master`, `/oms`, `/inbound`, `/inventory`, `/outbound`, `/strategy`.

설계에서 특히 신경 쓴 것들:

- **재고 키는 상품 + Location + Lot.** `inv`가 현재고 스냅샷, `inv_hist`가 모든 물리 변동을 ±수량으로
  남기는 append-only 원장이고, 이력 합계는 항상 스냅샷과 같다. 재고를 건드리는 모든 코드가
  `InvStore` 한 창구를 지나며 이력과 스냅샷을 한 트랜잭션에서 함께 쓴다.
- **정정은 지우지 않는다.** 검수 취소는 원본을 삭제하지 않고 `ADJUST(-수량)` 행을 더한다.
  재고 수량 정정 경로는 재고실사 하나뿐이다.
- **헤더 상태는 워크플로 단계만 표현한다.** 부분입고·부분할당 같은 부분 상태는 저장하지 않고 라인 수량
  비교로 파생시킨다.
- **예약수량은 출처가 몇이든 식 하나로 검증된다** — 할당 · 이동지시 · 피킹된 스테이징 재고 · 보충지시가
  모두 「아직 쓰지 않은 잔량의 합」으로 설명된다.
- **FK가 하나도 없다.** 참조 무결성은 애플리케이션 책임이고, DB는 `CHECK`와 `UNIQUE`로만 막는다.
- **락 순서를 전역으로 하나만 둔다** — 문서 헤더 → prod → lot → inv → inv_hld/inv_mov_task → nbr_seq.
  재고 행 락도 `InvStore`를 지나 재고 키 오름차순으로 잡는다.

## 배포

```
Cloudflare Pages  →  Render  →  Supabase
    (프론트)         (백엔드)    (PostgreSQL)
```

`main`에 push하면 GitHub Actions가 Render 배포 훅을 호출한다([.github/workflows/deploy.yml](.github/workflows/deploy.yml)).
빌드는 [Dockerfile](Dockerfile)의 2단계 빌드를 쓰고, DB 접속정보와 CORS 허용 도메인은 환경변수로
분리해 로컬과 배포가 같은 빌드를 쓴다.

## 문서

| 문서 | 내용 |
|---|---|
| [docs/design.md](docs/design.md) | 각 판단을 왜 그렇게 했는지 — 상태 전이 · 재고 모델 · 할당 동시성 |
| [docs/schema.sql](docs/schema.sql) | 스키마의 주인. 컬럼마다 근거 주석이 붙어 있다 |
| [docs/naming-dictionary.md](docs/naming-dictionary.md) | 표준 단어 사전과 이름 조합 규칙 |
| [docs/screen-list.html](docs/screen-list.html) | 화면 목록과 구현 현황 |
| `docs/migration-*.sql` | 이미 만들어진 DB에 적용할 증분 |

## 한계

- 인증·권한 모델이 없다. 감사 컬럼의 작성자는 `admin` 고정이다.
