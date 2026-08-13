# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 명령어

```bash
./mvnw spring-boot:run                    # 애플리케이션 실행
./mvnw compile                            # QueryDSL Q-class도 함께 재생성
./mvnw test                               # 전체 테스트
./mvnw test -Dtest=WmsBackApplicationTests            # 클래스 하나
./mvnw test -Dtest=WmsBackApplicationTests#contextLoads  # 메서드 하나
./mvnw clean package                      # jar 빌드
```

QueryDSL `Q*` 타입은 애노테이션 프로세서가 `target/generated-sources/annotations`에 생성한다. `@Entity`를 추가하거나 바꿨으면 `./mvnw compile`을 먼저 돌려야 `*RepositoryImpl`의 `Q*` import가 풀린다.

## 데이터베이스

PostgreSQL(Supabase). 접속 정보는 환경변수로 받고, 로컬 기본값은 `application.properties`에 있다.

- `DB_URL` (기본 `jdbc:postgresql://localhost:5432/postgres`)
- `DB_USERNAME` (기본 `postgres`)
- `DB_PASSWORD` (기본 빈 값)

`spring.jpa.hibernate.ddl-auto=none` — **Hibernate는 테이블을 만들지도 바꾸지도 않는다.** 스키마의 주인은 `docs/schema.sql`이고, 엔티티를 거기에 맞춰 쓴다(반대 방향이 아니다). 신규 DB는 `docs/schema.sql` 적용 후 `docs/seed-dev.sql`, 기존 DB는 `docs/migration-catchup-to-schema.sql`을 적용한다(재실행 안전).

SQL은 Supabase 대시보드가 아니라 **DBeaver**로 돌린다. 그래서 마이그레이션은 `BEGIN;`/`COMMIT;`으로 감싸지 않고 **전체를 `DO $tag$ … $tag$` 블록 하나로** 쓴다 — `BEGIN;`을 쓰면 실패 시 연결에 죽은 트랜잭션이 남아 이후 모든 쿼리가 `25P02`를 뱉는다.

**Oracle → PostgreSQL 전환 이전 상태로 남아 있는 파일이 둘 있다** — `docker-compose.yml`(아직 Oracle)과 `README.md`의 기술 스택·실행 절차. 현재 설정과 맞지 않으니 그대로 따르면 안 된다.

## 아키텍처

### 한 레포에 네 층, 의존은 한 방향

- `com.project.common` — 감사 엔티티 · 설정 · 공통 예외 핸들러. **어느 앱도 import하지 않는다.**
- `com.project.mdm` — 두 앱이 공유하는 마스터 데이터: `prod`(상품 · 포장 · 온도대) · `vendor`(거래처) · `store`(점포) · `code`(공통코드) · `nbr`(채번)
- `com.project.wmsback` — 창고 작업: `warehouse`(로케이션 · 존 · Lot) · `inbound` · `inventory` · `outbound` · `strategy`
- `com.project.omsback` — 창고 작업문서를 발생시키는 주문 원장

```
common  ← mdm ← wmsback ← omsback
             ↖──────────────┘
```

**의존은 이 방향으로만 흐른다.** `mdm`은 `wmsback`·`omsback`을 모르고, `wmsback`은 `omsback`을 모른다. 나중에 앱을 떼어낼 수 있게 하려는 의도다.

- `omsback → wmsback`은 **주문 확정이 만드는 작업문서 둘**로 제한한다 — 입고예정 ASN(`IbOrder` · `IbLine` · `IbStatus` · `IbOrderRepository`)과 창고 출고주문(`OutbOrder` · `OutbLine` · `OutbStatus` · `OutbOrderRepository`). 그 외 `omsback`이 쓰는 `Prod` · `Vendor` · `Store` · `NbrService` · `CodeDetail`은 전부 `mdm`이다 — 마스터를 `wmsback` 안에 두면 이 구분이 보이지 않아 밖으로 뺐다.
- 이 규칙을 실제로 떠받치는 지점이 `IbOrder.omsIbOrderId` · `OutbOrder.omsOutbOrderId`인데, `@ManyToOne`이 아니라 **평범한 `Long` 스칼라**로 매핑돼 있다. 도메인 간 참조를 건드릴 때 이 형태를 유지할 것.

**아래 층이 위 층의 데이터를 봐야 하면 포트로 뒤집는다.** 상품 삭제 가드가 그 사례다 — `mdm`의 `ProdService`가 두 앱의 참조를 모두 확인해야 하는데(FK 0건이라 DB가 안 막아준다) 직접 조회하면 의존이 거꾸로 생긴다. 그래서 `mdm.prod.service.ProdRefChecker` 인터페이스를 두고 `WmsProdRefChecker`(재고 · 이력 · 입고예정 · 출고주문 · Lot) · `OmsIbProdRefChecker`(입고주문) · `OmsOutbProdRefChecker`(출고주문)가 각자 구현해 빈으로 등록하며, `ProdService`가 `@Order` 순으로 순회한다.

같은 이유로 **`@RestControllerAdvice`도 두 개다** — 공통은 `common.exception.GlobalExceptionHandler`, 검수 위반은 `wmsback.strategy.inspection.exception.InspectionExceptionHandler`. 후자를 공통으로 올리면 `common`이 `wmsback`을 import하게 된다.

각 도메인 패키지는 `controller / dto / entity / repository / service` 구성을 따른다. `strategy`는 예외다 — 전략 커널이라 `condition / field / component / exception`이 추가로 있고(`component`는 유형별 구성요소 enum과 그 입출력이 사는 자리 — 검수 규칙·적치 방식·할당 구현체), `InspectionQueryRepository` · `PutawayQueryRepository` · `AlocQueryRepository`는 Spring Data 인터페이스 없이 `JPAQueryFactory`만 드는 **읽기 전용 조회 포트**로 아래 「QueryDSL 리포지토리 패턴」의 3파일 삼각형을 따르지 않는다.

### FK가 하나도 없다

`docs/schema.sql`은 FK를 하나도 선언하지 않고, 라이브 DB에도 FK가 0건이다(`docs/migration-catchup-to-schema.sql`이 카탈로그를 훑어 전량 제거했다 — 예외 없음). 참조 무결성은 애플리케이션 책임이다. DB가 여전히 막아주는 것은 `CHECK`와 `UNIQUE`뿐이므로 이것들을 약화시키면 안 된다 — `ck_inv_qty`(재고 음수·과할당 금지), `ck_ib_line_qty`, `uq_inv`, `uq_lot` 등.

원래부터 의도적으로 FK가 아니었던 느슨한 참조 컬럼들도 있다: `inv_hist.ib_line_id`, `inv_hist.rfn_doc_no`, `inv_hist.from_loc_id` / `to_loc_id`, `ib_order.oms_ib_order_id`.

### 재고 모델 (핵심 불변식)

- 재고 키는 **상품 + Location + Lot**. `inv`가 현재고 스냅샷이고, `inv_hist`는 모든 물리 변동을 ±수량으로 기록하는 append-only 원장이다.
- **`inv_hist` 합계 = `inv` 스냅샷.** 재고를 건드리는 코드는 이력 1건 기록과 스냅샷 갱신을 **한 트랜잭션에서 함께** 한다. 둘 중 하나만 하는 코드를 쓰지 말 것.
- **그 짝을 묶는 자리가 `inventory.service.InvStore`다.** 스냅샷 증감 · 이력 기록 · 수량이 모두 0이 된 행 삭제 셋을 한 메서드로 처리한다(`increase` / `decrease` / `move` / `reserve` / `release` / `hold` / `releaseHold`, 이력의 문서 참조 컬럼은 `InvDocRef`). **서비스에서 `Inv`의 증감 메서드나 `invRepository.save` · `delete`를 직접 부르지 말 것** — 그 셋을 손으로 맞추다 조건이 갈라진 적이 있어 포트로 모았다. **`inv` 행 락도 이 창구다**(2026-08-09 — 「락은 서비스 책임」 번복): 다건은 `lockAll`/`lockAllByIds`, 단건은 `lock`을 지나며, 순서(재고 키 오름차순)는 InvStore 내부에 하나뿐이다. 서비스에서 `invRepository.findByKeyForUpdate`를 직접 부르지 말 것. 전역 계층(문서 헤더 → prod → lot → inv → inv_hld · inv_mov_task → nbr_seq — 특히 **채번은 재고 락을 전부 잡은 뒤**)은 `docs/design.md` 「락 순서」 참고.
- **수량이 모두 0이 된 `inv` 행은 삭제한다** — 재고 테이블엔 실물 · 예약 · 보류가 있는 행만 남긴다(이력 SUM=0 ↔ 행 없음). 판정은 `Inv#isEmpty()` 하나뿐이고 보유 · 예약 · 보류를 **모두** 본다. `ck_inv_qty`로 커밋 시점엔 보유수량 하나와 동치지만 그 제약은 flush 때만 평가되므로, 트랜잭션 중간 상태에서 예약이 남은 행을 지우지 않으려면 셋을 다 봐야 한다.
- `MOVE`는 **`inv_hist` 2행**이다(출발지 −, 도착지 +). 두 행 모두 같은 `from_loc_id`/`to_loc_id`를 가져서 한 행만 봐도 이동 전체를 알 수 있다.
- 정정은 append-only다 — 검수 취소는 원본을 지우지 않고 `ADJUST(-수량)` 행을 추가한다.
- `RCV-STAGE`는 입고 스테이징 로케이션이다. 코드값이 `IbLineRepositoryImpl` · `ReceivingService` · `PutawayService` · `PutawayTaskService` 네 곳에 private 상수로 중복돼 있으니 바꿀 때 넷을 같이 고칠 것.

### 상태와 수량의 분담

헤더의 상태 컬럼은 **워크플로 단계만** 표현한다. 부분 상태(부분입고 · 부분할당)는 저장하지 않고 **라인 수량 비교로 파생**시킨다. `PARTIALLY_*` 같은 상태를 추가하지 말 것.

### QueryDSL 리포지토리 패턴

동적 쿼리는 세 파일로 나눈다 — `XxxRepository`(Spring Data)가 `XxxRepositoryCustom`을 상속하고, `XxxRepositoryImpl`이 `JPAQueryFactory`(`common/config/QuerydslConfig`의 빈)를 들고 구현한다. 기존 스타일을 따를 것: `Q*` static import, 선택 조건은 `BooleanExpression` 헬퍼 메서드, DTO 변환은 `Projections`.

`BaseEntity` / `BaseTimeEntity`가 감사 컬럼 4종을 제공한다. 작성자 값은 `JpaConfig`의 `AuditorAware`가 채우는데, 인증 모델이 아직 없어서 `admin` 고정이다.

## 네이밍

약어 사전의 주인은 `docs/naming-dictionary.md`이고 `docs/schema.sql` 머리말에 발췌가 있다. **새 컬럼도 반드시 이걸 따라야 한다** — 자주 쓰는 것만 추리면:

```
expected→expct  received→rcvd  rejected→rjct  location→loc  history→hist
product→prod  putaway→ptawy  allocated/allocation→aloc  picking→pikng
type→typ  zone→zon  temperature→tmp  group→grp  use→us  description→dscr
reference→rfn  order→odr  wave→wav  sort+sequence→srt_seq  person in charge→pic
date→de(일자)  datetime→dt(일시)  cancel→cncl  close→clos  complete→cmpl  shipment→shmt
unit of measure→uom(계량단위)  each→ea(낱개)  weight→wgt(중량)
```

**사전의 주인은 `docs/naming-dictionary.md`(237단어) 하나이고, `schema.sql` 머리말의 것은 그 발췌다.** 예전에 두 벌이 충돌하던 항목(`ptwy` vs `PTAWY`, `alloc` vs `ALOC` 등)은 `docs/migration-catchup-to-schema.sql`의 컬럼 개명 루프가 사전 쪽으로 통일했다. 새 이름은 반드시 `docs/naming-dictionary.md`에서 단어를 찾아 조합하고, **사전에 없는 단어는 사전에 먼저 추가한 뒤 쓴다.**

의도적으로 사전을 따르지 않는 예외가 셋 있다. 바꾸지 말 것:

- **`status`** (사전은 `ST`) — 두 글자로는 state/street/start 중 무엇인지 알 수 없다.
- **`code_cd` · `code_nm`** (사전은 `CD`) — `cd_cd`가 되어 같은 단어가 겹친다.
- **감사 컬럼 4종** `created_at`/`created_by`/`updated_at`/`updated_by` — 「생성자」가 사전에 없고 `CRTR`은 이미 「기준(Criteria)」이 쓰고 있어 이름을 지을 수 없다. `BaseEntity`·`AuditorAware`와도 묶여 있다.

또한 테이블명·PK·FK 컬럼은 사전보다 위 규칙이 우선한다 — 그래서 `inv`는 `invn`이 되지 않았고 `prod_id`·`loc_id` 등도 그대로다.

상품 마스터는 원래 `sku`였고 `prod`로 개명했다(`docs/migration-sku-to-prod.sql`). 업무 용어를 「상품」으로 통일하면서 `docs/naming-dictionary.md`의 `상품 = PROD`를 따른 것이다. **코드·컬럼·화면 라벨 어디에도 SKU를 다시 쓰지 않는다.**

테이블 접두는 주문 `OMS_*`(`oms_ib_*` 입고주문 · `oms_outb_*` 출고주문) / 입고 `IB_*` / 출고 `OUTB_*` / 재고 `INV*`이고 마스터는 접두가 없다. PK는 `{테이블명}_id`, FK 컬럼은 참조 테이블 PK명을 그대로 쓴다.

**전략은 유형별 도메인 접두**를 쓴다 — `insp_`(검수) / `ptawy_`(적치) / `wav_`(웨이브) / `aloc_`(할당) + 유형 공용 `stgy_`(리비전 · 실행로그). 유형을 하나의 `stgy_` 아래 모으지 않은 이유는 네 유형의 입력·출력이 본질적으로 다르기 때문이다 — 같은 테이블에 담으면 컬럼 하나가 유형마다 다른 뜻을 갖게 된다.

## 문서

- `docs/design.md` — 각 판단을 **왜** 그렇게 했는지(상태 전이 · 재고 모델 · 할당 동시성). 프로세스 설계를 바꾸기 전에 읽을 것.
- `docs/schema.sql` — 스키마의 주인. 컬럼마다 근거 주석이 붙어 있다.
- `docs/migration-*.sql` — 이미 만들어진 DB에 적용할 증분. **순서대로 재생할 수 있다고 가정하지 말 것** — 라이브 DB는 `migration-catchup-to-schema.sql`까지 적용된 상태이고, 실행 불가가 된 옛 증분들은 삭제했다(이력은 git에 있다). 새 증분은 "현재 라이브 상태 → `schema.sql` 상태"로 쓰고, 존재 확인을 걸어 재실행 가능하게 만든다.
- `docs/screen-list.html` — 화면 목록과 구현 현황.
- `docs/naming-dictionary.md` — 표준 단어 사전(한글 · 약어 · 영문명)과 이름 조합 규칙.

`docs/design.md`에는 **코드가 아직 따르지 않는 의도**가 섞여 있다. 예를 들어 수량 컬럼은 오직 `IbLine#recalcQty`를 거쳐서만 갱신한다고 못박았지만 그 메서드는 존재하지 않고 `accept()` · `cancelAccept()` · `putaway()`가 전부 직접 증분한다. 문서와 코드가 어긋나면 **어느 한쪽으로 조용히 맞추지 말고 물어볼 것.**


## 명명규칙

`docs/naming-dictionary.md`의 표준 단어 사전(237개)을 기준으로 변수명·필드명을 생성한다. 이름의 재료는 **약어**이고(`예정 EXPCT` + `수량 QTY` → `expctQty` / `expct_qty`), 사전에 없는 단어는 사전에 먼저 추가한 뒤 쓴다.

**두 벌이던 약어 사전은 통일됐다.** 예전에 `적치`(`ptwy` vs `PTAWY`)·`할당`(`alloc` vs `ALOC`) 등에서 충돌하던 것을 `docs/migration-catchup-to-schema.sql`의 컬럼 개명 루프가 사전 쪽으로 개명했다. 남은 예외 셋(`status` · `code_cd`/`code_nm` · 감사 컬럼)은 위 「네이밍」에 이유와 함께 적어뒀고, **이 셋은 사전을 따르지 않는 것이 결정된 사항이다.**
