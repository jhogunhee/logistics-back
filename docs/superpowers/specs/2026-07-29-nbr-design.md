# 채번관리(Number Generation) 시스템 설계

- 작성일: 2026-07-29
- 근거: `채번관리(Number Generation) 시스템 PRD` (2026-07-29, 기존 ict_lodis `TCO_CF_NBR`/`TCO_CF_NBR_CMPS`/`TCO_CF_NBR_DYNC` 단순화 버전)을 이 프로젝트(PostgreSQL + JPA, `docs/naming-dictionary.md` · `docs/schema.sql` 컨벤션)에 맞게 재설계
- 브레인스토밍 세션에서 확정한 결정 사항을 반영함 (아래 §7 참고)

## 1. 목적 / 배경

여러 도메인(상품·벤더 코드, 입고주문·입고·출고·웨이브 번호 등)에서 쓰는 "규칙 기반 자동 번호 발급"을 공통 모듈로 제공한다.

이 프로젝트는 이미 채번을 하고 있다 — 다만 도메인마다 따로:

| 대상 | 현재 방식 | 형식 |
|---|---|---|
| `prod.prod_cd` | `prod_cd_seq` (PostgreSQL SEQUENCE) | `PROD-0001` |
| `vendor.vndr_cd` | `vndr_cd_seq` | `VD-0001` |
| `oms_ib_order.oms_ib_no` | `oms_ib_no_seq` | `PO-20260723-001` (일자 리셋 없음) |
| `ib_order.ib_no` | `ib_no_seq` | `IB-20260714-001` (일자 리셋 없음) |
| `outb_order.outb_no` | `outb_no_seq` | `OB-20260729-001` (일자 리셋 없음) |
| `outb_wave.wav_no` | `outb_wave_no_seq` | `WV-20260729-001` (일자 리셋 없음) |
| `lot.lot_no` | 상품 로우 락 + `COUNT` (시퀀스 아님) | `LOT-260729-001` (상품+입고일자 리셋) |

각 서비스 메서드 안에 `String.format("PREFIX-%s-%03d", ...)`가 흩어져 있고, 시퀀스 6개가 각각 전용 DDL을 갖는다. 이번 설계는 이 여섯 개를 테이블 기반 공통 모듈로 통합한다. `lot_no`는 상품+일자 복합 리셋이라는 특수성 때문에 이번 범위에서 제외한다(§7).

## 2. 범위

- **포함**: 채번 규칙 관리(등록/수정/조회), 규칙 기반 번호 발급(Java 서비스 + 보조 REST), 동적 키(일자) 기준 카운터 분리, 기존 6개 시퀀스 마이그레이션
- **제외**: 발번 이력/감사 로그, 패턴 조립 UI, 번호 회수/재사용, 다국어, `lot_no` 이관, `CUSTOM`(자유 문자열) 동적키 타입 — 실제 수요가 생기면 추가

## 3. 네이밍

`docs/naming-dictionary.md`에 두 단어를 추가한다 (사전 규칙 6 — 새 단어는 등재 후 사용):

| 한글 | 약어 | 영문명 |
|---|---|---|
| 규칙 | `RULE` | Rule |
| 패턴 | `PTRN` | Pattern |

이름 조합 (전부 기존 사전 단어 + 위 2개로 구성, 새 접두 계열이 아니라 `code_group`/`code_detail`처럼 사전 단어를 이어붙인 것):

| 대상 | 이름 | 근거 |
|---|---|---|
| 테이블 1 | `nbr_rule` | 채번(`NBR`)+규칙(`RULE`) |
| PK | `rule_cd` | `code_group`이 만든 선례 — 코드성 테이블은 `{테이블명}_id`가 아니라 자연키(`grp_cd`)를 PK로 쓴다. `nbr_rule`도 항상 코드로 조회하는 동일 성격 |
| | `rule_nm` | 규칙+명 |
| | `ptrn` | 패턴 (단독, 테이블 맥락상 모호하지 않음) |
| | `dync_ky_typ` | 동적+키+유형 |
| | `us_yn` | 사용+여부 (PRD의 `use_yn`은 사전 위반 — `US`가 맞음, `vendor.us_yn`/`code_detail.us_yn`과 동일) |
| 테이블 2 | `nbr_seq` | 채번(`NBR`)+순서(`SEQ`) — "카운터"라는 새 단어 없이 표현 |
| | `dync_ky` | 동적+키 |
| | `seq` | 순서 (테이블 안에서 "이 규칙·이 동적키의 현재값"이라는 문맥이 이미 있어 접두 불필요) |

## 4. 패키지 배치

`nbr_rule`은 `code_group`/`code_detail`과 같은 "다른 도메인이 참조하는 설정성 기준정보 + CRUD 화면" 성격이라 `com.project.wmsback.master`에 둔다(`common`은 config/entity/exception 같은 비도메인 인프라 전용이라 결이 안 맞음). `master` 패키지는 엔티티별 서브패키지 없이 `controller`/`dto`/`entity`/`repository`/`service` 안에 파일명으로만 구분하는 방식이라 그대로 따른다.

```
master/entity/NbrRule.java, NbrSeq.java, NbrSeqId.java, DyncKyTyp.java
master/repository/NbrRuleRepository.java, NbrSeqRepository.java, NbrSeqRepositoryCustom.java, NbrSeqRepositoryImpl.java
master/service/NbrRuleService.java (CRUD), NbrService.java (발급/미리보기)
master/dto/NbrRuleResponse.java, NbrRuleSaveRequest.java, NbrSeqResponse.java, NbrIssueResponse.java, NbrPreviewRequest.java, NbrPreviewResponse.java
master/controller/NbrRuleController.java
```

`omsback`은 `wmsback`을 import할 수 있으므로(반대는 불가), `omsback`의 `OmsIbOrderService`가 `NbrService`를 그대로 주입받아 쓴다.

## 5. 테이블 설계

### `nbr_rule`

```sql
CREATE TABLE nbr_rule (
    rule_cd     VARCHAR(30)     NOT NULL,
    rule_nm     VARCHAR(100)    NOT NULL,
    ptrn        VARCHAR(200)    NOT NULL,
    dync_ky_typ VARCHAR(10)     NOT NULL,
    us_yn       CHAR(1)         DEFAULT 'Y' NOT NULL,
    created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by  VARCHAR(30)     DEFAULT 'admin' NOT NULL,
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(30),
    CONSTRAINT pk_nbr_rule PRIMARY KEY (rule_cd),
    CONSTRAINT ck_nbr_rule_dync_ky_typ CHECK (dync_ky_typ IN ('NONE', 'DATE')),
    CONSTRAINT ck_nbr_rule_us_yn CHECK (us_yn IN ('Y', 'N'))
);
```

- `dync_ky_typ`은 `zon.tmp_zon` 등과 달리 `code_detail` 공통코드로 빼지 않고 `CHECK`로 고정한다. `zon`의 코드값은 늘어나도 로직 분기가 없는 표시값이지만, `dync_ky_typ`은 값 자체가 발급 엔진의 분기(고정키 `'-'` vs 서버 일자 강제산출)와 1:1로 묶여 있어 `status`류(`ck_ib_order_status`)와 같은 성격이다. 새 타입 추가는 배포를 요구한다.
- `ptrn` 저장 시 서버 검증: `{SEQ:n}` 정확히 1개(n=1~9), `dync_ky_typ='DATE'`면 날짜 토큰(`{yyyyMMdd}`/`{yyyy}`/`{MM}`/`{dd}`) 1개 이상, 그 외 알 수 없는 `{...}` 토큰은 저장 거부.
- `dync_ky_typ`은 등록 후 변경 불가 — `NbrRule.update()`가 `ruleNm`/`ptrn`/`usYn`만 받는다.
- FK 없음(프로젝트 전역 정책).

### `nbr_seq`

```sql
CREATE TABLE nbr_seq (
    rule_cd     VARCHAR(30)     NOT NULL,
    dync_ky     VARCHAR(30)     NOT NULL,
    seq         BIGINT          DEFAULT 0 NOT NULL,
    created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by  VARCHAR(30)     DEFAULT 'admin' NOT NULL,
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(30),
    CONSTRAINT pk_nbr_seq PRIMARY KEY (rule_cd, dync_ky)
);
```

- FK 없음. `rule_cd`는 `ib_hist.ib_line_id`류의 느슨한 참조.
- "최종 발번 시각"은 별도 컬럼 없이 `updated_at`(감사컬럼)을 그대로 쓴다 — 발급마다 이 행이 갱신되므로 의미가 정확히 일치한다.
- `dync_ky_typ='NONE'` 규칙은 `dync_ky='-'` 고정값 한 행만 갖는다.
- 관리자가 만들지 않는다 — 최초 발급 요청 시 없으면 자동 생성(upsert).

## 6. 엔티티 / 리포지토리 / 서비스

- `NbrRule` — `Zon`과 동일한 모양(`@Id String ruleCd`, `NoArgsConstructor(PROTECTED)` + `@Builder`). `DyncKyTyp` enum(`NONE`, `DATE`)을 `EnumType.STRING`으로 매핑.
- `NbrSeq` — `CodeDetail`과 동일한 복합키 패턴(`@IdClass(NbrSeqId.class)`). `NbrSeqId`는 `CodeDetailId`를 본뜬 `(ruleCd, dyncKy)` 값 클래스.
- `NbrSeqRepositoryCustom.findByIdForUpdate(String ruleCd, String dyncKy)` — `ProdRepositoryImpl.findByIdForUpdate`와 동일하게 QueryDSL `setLockMode(PESSIMISTIC_WRITE)`.

### 발급 흐름 (동시성)

**`DATE` 타입의 동적키는 서버가 강제 산출하지 않는다.** 원래 PRD의 "서버 현재일자 강제 산출(클라이언트 값 무시)"은 REST로 들어오는 값을 못 믿는다는 위변조 방지 취지였는데, 실제 마이그레이션 대상 4곳 중 3곳(`oms_ib_no`/`ib_no`/`outb_no`)은 `LocalDate.now()`가 아니라 **호출자가 이미 들고 있는 업무 일자**(예정일 `expctDe`, 주문일 `odrDe`)를 쓴다 — 이게 리셋 키이기도 해서, 서버가 오늘 날짜로 덮어쓰면 미래 예정 ASN 번호가 오늘 날짜로 나오고 리셋 단위 자체가 달라진다. 신뢰된 서버 내부 Java 호출에는 위변조 우려가 없으므로, `LocalDate`를 호출자가 넘기는 오버로드를 둔다:

```
NbrService.issue(String ruleCd)                  // NONE 규칙 전용. DATE 규칙에 쓰면 IllegalStateException
NbrService.issue(String ruleCd, LocalDate de)     // DATE 규칙 전용. de가 리셋 키이자 토큰 렌더링 기준

내부 흐름 (issue(ruleCd, de) 기준):
  1. rule = nbrRuleRepository.findById(ruleCd)
     없으면 IllegalArgumentException (400)
  2. rule.usYn == 'N' 이면 IllegalStateException (409)
  3. rule.dyncKyTyp과 호출한 오버로드가 안 맞으면 IllegalStateException
     (NONE 규칙에 issue(ruleCd, de) 호출 / DATE 규칙에 issue(ruleCd) 호출)
  4. dyncKy 결정 — NONE이면 "-", DATE면 de를 yyyyMMdd로 고정 포맷
  5. row = nbrSeqRepository.findByIdForUpdate(ruleCd, dyncKy)
     비어 있으면: nbrSeqRepository.insertIfAbsent(ruleCd, dyncKy) (네이티브 INSERT ... ON CONFLICT DO NOTHING)
                 후 findByIdForUpdate 재조회
     (DataIntegrityViolationException을 catch하는 방식은 쓰지 않는다 — PK 충돌 시 PostgreSQL이
      트랜잭션을 즉시 abort 상태로 만들어(25P02) 같은 트랜잭션의 후속 조회까지 실패한다.
      CLAUDE.md가 마이그레이션에서 BEGIN;을 금지하는 것과 같은 이유. ON CONFLICT DO NOTHING은
      예외를 던지지 않으므로 이 문제 자체가 없다)
  6. row.increment(); (seq += 1, JPA dirty checking으로 반영)
  7. 패턴 렌더링(de + row.seq) 후 문자열 반환

NbrService.preview(String ptrn, DyncKyTyp dyncKyTyp):
  DB 접근 없이 오늘 날짜 + seq=1로 렌더링만 (패턴 검증 로직 재사용)
```

`DyncKyTyp`은 날짜 계산을 갖지 않는다(어떤 오버로드를 써야 하는지만 구분) — 실제 `LocalDate` 결정은 호출부 책임이다. `outb_wave_no`(유일하게 진짜 "오늘"인 규칙)는 호출부에서 `LocalDate.now()`를 그대로 넘긴다. 보조 REST `/{ruleCd}/issue` 엔드포인트만 원래 PRD 취지대로 항상 `LocalDate.now()`를 고정 사용해 위변조 우려를 막는다(외부 HTTP 호출은 신뢰된 서버 내부 호출이 아니므로).

`CUSTOM`(자유 문자열) 동적키 타입은 여전히 두지 않는다(YAGNI) — 지금 필요한 건 "서버 시각이 아니라 호출자가 정한 날짜"뿐이고, 부서 같은 임의 문자열 키가 필요한 도메인은 아직 없다.

**기존 방식과의 차이**: PostgreSQL SEQUENCE의 `nextval`은 트랜잭션 롤백에 영향받지 않는(non-transactional) 반면, `nbr_seq` 갱신은 일반 UPDATE라 같은 트랜잭션이 롤백되면 채번도 함께 롤백된다 — 결번이 안 생기는 대신, 락 보유 시간이 "발급 순간"이 아니라 "그 트랜잭션 전체 길이"로 늘어난다. 지금 6개 호출부는 모두 번호 발급 직후 바로 저장하는 짧은 트랜잭션이라 실질 영향은 적지만, 향후 채번을 트랜잭션 앞부분에 두고 뒤에 무거운 작업을 넣는 코드가 생기지 않도록 주의가 필요하다(PRD 5.2절의 "발번 전용 짧은 트랜잭션 원칙"과 동일한 취지).

## 7. 기존 6곳 마이그레이션

브레인스토밍에서 확정: **`lot_no`를 제외한 6개 전부 이관**하고, 일자가 들어가는 4개는 이번 기회에 **진짜 일자별 리셋으로 전환**한다(현재는 시퀀스가 절대 리셋되지 않음 — 스키마 주석 "일자 리셋 없음"·"전역 유일성만 보장").

| rule_cd | ptrn | dync_ky_typ | 비고 |
|---|---|---|---|
| `PROD_CD` | `PROD-{SEQ:4}` | NONE | 동작 동일 유지 |
| `VNDR_CD` | `VD-{SEQ:4}` | NONE | 동작 동일 유지 |
| `OMS_IB_NO` | `PO-{yyyyMMdd}-{SEQ:3}` | DATE | **동작 변경**: 일자별 001부터 재시작 |
| `IB_NO` | `IB-{yyyyMMdd}-{SEQ:3}` | DATE | **동작 변경** |
| `OUTB_NO` | `OB-{yyyyMMdd}-{SEQ:3}` | DATE | **동작 변경** |
| `OUTB_WAV_NO` | `WV-{yyyyMMdd}-{SEQ:3}` | DATE | **동작 변경** |

`lot_no`는 상품+입고일자 복합 리셋이라 `dync_ky` 단일 값으로 표현할 수 없어(현재 `NONE`/`DATE` 2종으로는 불가) 이번 범위에서 제외, 지금 방식(상품 로우 락 + `COUNT`) 유지.

마이그레이션 스크립트(`docs/migration-*.sql`, 재실행 가능한 `DO $tag$ … $tag$` 단일 블록, `docs/migration-catchup-to-schema.sql` 이후 상태 → 이 설계 상태로 작성)가 처리할 것:

1. `nbr_rule` 6행 INSERT (위 표).
2. `PROD_CD`/`VNDR_CD`: `nbr_seq(rule_cd, '-', seq)`를 각 시퀀스의 현재값(`last_value`)으로 시딩 — 번호가 끊기지 않고 이어지게 함.
3. `OMS_IB_NO`/`IB_NO`/`OUTB_NO`/`OUTB_WAV_NO`: **오늘 날짜분만이 아니라, 기존 번호에 실제로 박혀 있는 날짜마다 각각** `nbr_seq` 행을 시딩한다 — 네 개 다 같은 방식. `oms_ib_no`/`ib_no`는 예정일(`expctDe`), `outb_no`는 주문일(`odrDe`) 기준이라 아직 안 끝난 미래예정 주문이나 소급 등록된 과거 주문이 여러 날짜에 걸쳐 있을 수 있다(`outb_wave_no`는 항상 생성 당일이라 이론적으로는 "오늘"만 있으면 되지만, 굳이 특례를 두지 않고 나머지 셋과 같은 방식으로 전체 날짜를 훑는 편이 스크립트가 더 단순하고 견고하다). 예:

   ```sql
   INSERT INTO nbr_seq (rule_cd, dync_ky, seq)
   SELECT 'IB_NO', split_part(ib_no, '-', 2), max(split_part(ib_no, '-', 3)::bigint)
     FROM ib_order GROUP BY split_part(ib_no, '-', 2)
   ON CONFLICT (rule_cd, dync_ky) DO NOTHING;
   ```

   `right(ib_no, 3)` 대신 `split_part(..., '-', 3)`을 쓰는 이유: 채번값이 언젠가 999를 넘으면 자릿수가 늘어나 마지막 3자리 고정 추출이 깨진다 — `-`로 분리하면 자릿수와 무관하게 안전하다. 이걸 건너뛰면 이미 번호가 나간 날짜에서 마이그레이션 후 중복 채번이 난다.
4. 옛 시퀀스 6개(`prod_cd_seq`, `vndr_cd_seq`, `oms_ib_no_seq`, `ib_no_seq`, `outb_no_seq`, `outb_wave_no_seq`) `DROP SEQUENCE`.

애플리케이션 코드 변경(실행 계획 단계에서 구체화):

- `ProdRepository`/`VendorRepository`/`IbOrderRepository`/`OutbOrderRepository`/`OutbWaveRepository`의 `nextval` 네이티브 쿼리 메서드 제거.
- `ProdService.create`, `VendorService.create` — `nbrService.issue("PROD_CD")` / `issue("VNDR_CD")` (NONE, 인자 없는 오버로드).
- `OmsIbOrderService`(주문번호·입고번호 2곳), `OutbOrderService.create` — `nbrService.issue("OMS_IB_NO", req.getExpctDe())` 등 **기존에 쓰던 그 날짜 값을 그대로 두 번째 인자로 전달** (DATE, `LocalDate` 오버로드). `String.format(...)` + `nextXxxSeq()` 조합을 대체.
- `OutbWaveService.create` — `nbrService.issue("OUTB_WAV_NO", LocalDate.now())`.
- `docs/schema.sql`의 해당 시퀀스 정의 및 주석 제거, `nbr_rule`/`nbr_seq` 테이블 정의 추가.

## 8. API 표면

내부 호출(같은 트랜잭션 안에서 번호 발급 직후 저장)이 1차 사용 방식이고, REST는 보조 창구다.

경로는 이 프로젝트 컨트롤러 컨벤션(`/api` 접두 없음, `/{도메인}/{리소스 복수형}`, 액션은 `/{id}/동사`, id 변수는 필드명 그대로, 다단어는 kebab-case)을 따른다 — `ZonController`(`/master/zons`), `IbOrderController`(`/inbound/asns`, `/{ibOrderId}/receive`) 등과 동일한 톤.

실제로는 `Zon`/`Prod`/`Vendor` 등 기존 마스터 화면 전부가 개별 등록/수정/삭제 엔드포인트가 아니라 **그리드 일괄 저장 하나**(`GET` 목록 + `POST /bulk`, 행마다 `_status`: C/U/D)로 되어 있다 — 별도 상세 조회 GET도, PUT도 없다. 이 컨벤션을 그대로 따른다:

| 메서드 | 경로 | 용도 |
|---|---|---|
| GET | `/master/nbr-rules` | 목록 조회 |
| POST | `/master/nbr-rules/bulk` | 신규(C)/수정(U)/삭제(D) 행 일괄 저장. 등록 시 패턴 검증, 수정 시 `dyncKyTyp` 변경 거부 |
| GET | `/master/nbr-rules/{ruleCd}/seqs` | `nbr_seq` 읽기전용 목록 |
| POST | `/master/nbr-rules/{ruleCd}/issue` | 발급 (테스트/외부 호출용). 항상 `LocalDate.now()` 기준 — 내부 Java 호출(`issue(ruleCd, LocalDate)`)과 달리 클라이언트가 날짜를 넘길 수 없다 |
| POST | `/master/nbr-rules/preview` | 미리보기 (`{ ptrn, dyncKyTyp }` → `{ number }`, DB 미접근, 오늘 날짜 + seq=1로 렌더링) |

## 9. 에러 처리

이 프로젝트의 `GlobalExceptionHandler` 컨벤션(존재하지 않는 참조는 대부분 `IllegalArgumentException`→400, 상태 충돌은 `IllegalStateException`→409)을 그대로 따른다. PRD가 제시한 404/409는 이 컨벤션에 맞춰 조정한다.

- 규칙 없음 → `IllegalArgumentException` (400)
- 비활성 규칙(`us_yn='N'`)으로 발급 시도 → `IllegalStateException` (409)
- 패턴 검증 실패(저장 시) → `IllegalArgumentException` (400)
- `dyncKyTyp` 변경 시도 → `IllegalArgumentException` (400)
- `rule_cd` 중복 등록 → PK 위반은 `DataIntegrityViolationException`으로 이미 전역 처리(409, 공통 메시지)

## 10. 화면

화면 1개(채번규칙 관리): 목록 그리드(다른 마스터 화면과 같은 행추가/삭제/일괄저장 편집 — 별도 상세/편집 폼 없음), 패턴 미리보기(`/master/nbr-rules/preview` 호출, 프론트에 파싱 로직 복제하지 않음), 선택 규칙의 `nbr_seq` 읽기전용 목록. `docs/screen-list.html`에 행 추가.

## 11. 비범위

- 발급 이력/로그 테이블 없음 — 카운터 현재값만 정확히 관리
- 채번 규칙 구성요소를 화면에서 조립하는 UI 없음
- DB 함수(`FN_GET_SEQ`) 방식 없음 — 애플리케이션(JPA 서비스) 레벨에서 전부 처리
- `CUSTOM`(자유 문자열) 동적키 타입 — 실 수요 생기면 추가
- `lot_no` 이관 — 상품+일자 복합 리셋 특수성 때문에 제외, 기존 방식 유지