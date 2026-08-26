# 반품입고 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 점포가 돌려보내는 물건을 입고주문의 「반품입고」 구분으로 받아, 검수에서 양품/불량을 갈라 양품은 정상 적치, 불량은 반품존에 보류로 쌓는다.

**Architecture:** 새 문서 없이 `oms_ib_order`·`ib_order`가 「상대가 점포일 수 있다」를 받아들이고(`vendor_id`/`store_id` 둘 중 하나), `ib_line`에 `rjct_qty`가 붙는다. 불량은 스테이징을 거치지 않고 반품존 로케이션(`RtngsLocResolver`가 해석)에 `RECEIVE`된 뒤 같은 트랜잭션에서 보류된다. `rcvd_qty`는 양품만이라 입고확정·적치 경로는 그대로다.

**Tech Stack:** Spring Boot 3 · JPA/QueryDSL · PostgreSQL(Supabase, DBeaver) · JUnit 5 + Mockito · React 19 + ag-grid (`C:\wms-front`)

**Spec:** `docs/superpowers/specs/2026-08-25-rtngs-inbound-design.md`

## Global Constraints

- 스키마의 주인은 `docs/schema.sql`. Hibernate는 DDL을 만들지 않는다(`ddl-auto=none`). 마이그레이션은 `BEGIN;` 없이 **`DO $mig$ … $mig$` 블록 하나**, 존재 확인으로 재실행 안전.
- FK 없음. 참조 무결성은 애플리케이션 책임. `CHECK`·`UNIQUE`는 약화하지 않는다.
- 재고 증감·이력은 `InvStore`만 부른다. 서비스에서 `Inv` 증감 메서드나 `invRepository.save/delete`를 직접 부르지 않는다.
- 이름은 `docs/naming-dictionary.md`의 약어로 — 이 작업에 새 단어는 없다(반품 `RTNGS` · 사유 `RSN` · 참조 `REF` · 판정 `DCSN` · 불합격 `rjct`는 CLAUDE.md 약어표).
- 의존 방향 `common ← mdm ← wmsback ← omsback`. `omsback`이 `wmsback`에서 쓰는 것은 `IbOrder`·`IbLine`·`IbOrderRepository`뿐 — `RsnValidator`(wmsback.inventory)를 omsback에서 쓰지 않는다.
- 사용자 메시지는 한국어. 주석은 꼭 필요한 자리에 한 줄.
- 테스트: `./mvnw test -Dtest=클래스명` (Windows Git Bash에서 `./mvnw` 그대로). QueryDSL Q-class는 `./mvnw compile`이 만든다 — 엔티티를 바꾼 뒤 첫 테스트 전에 한 번 돌린다.
- **커밋은 사용자가 요청할 때만.** 이 계획의 태스크에는 커밋 단계가 없다.
- 프론트 경로는 `C:\wms-front\src`. 그리드는 ag-grid, 라벨은 공통코드(`useCodes`)에서.

---

## 파일 지도

| 영역 | 파일 | 책임 |
|---|---|---|
| 스키마 | `docs/schema.sql` · `docs/migration-rtngs.sql` · `docs/seed-dev.sql` | 컬럼·코드·반품존 |
| 엔티티 | `omsback/inbound/entity/OmsIbOrder.java` · `OmsIbLine.java` · `wmsback/inbound/entity/IbOrder.java` · `IbLine.java` | 구분-상대 검증, `rjctQty`, 단위 선택 |
| 반품존 | `wmsback/inbound/service/RtngsLocResolver.java` (신규) | 불량 도착지 해석 · 반품존 판정 |
| 보류 | `wmsback/inventory/service/InvHldService.java` | `holdOn` 추출 |
| 검수 규칙 | `strategy/inspection/component/InspectionContext.java` · `InspectionRule.java` · `service/InspectionService.java` | 반품 skip |
| 검수 | `wmsback/inbound/dto/ReceiveRequest.java` · `ReceiptResponse.java` · `service/ReceivingService.java` | 양품/불량 분기 · 취소 · 판정 |
| 조회 | `wmsback/inbound/repository/IbOrderRepositoryImpl.java` · `IbLineRepositoryImpl.java` · dto 5종 · `IbOrderSearchCond.java` | 상대처 · `rjct` 반영 |
| OMS | `omsback/inbound/dto/OmsIbOrderSaveRequest.java` · `OmsIbLineSaveRequest.java` · `OmsIbOrderResponse.java` · `OmsIbLineResponse.java` · `service/OmsIbOrderService.java` · `repository/OmsIbOrderRepositoryImpl.java` | 점포·원출고·사유·환산 |
| 가드 | `wmsback/inbound/service/WmsIbStoreRefChecker.java` · `omsback/inbound/service/OmsIbStoreRefChecker.java` (신규) | 점포 삭제 가드 |
| 전략 | `strategy/core/service/StrategyOptionService.java` · `strategy/putaway/service/PtawyStgyService.java` · `strategy/allocation/repository/AlocQueryRepository.java` | RTNGS 재도입 · 할당 제외 |
| 프론트 | `pages/oms/InboundOrder.jsx` · `InboundOrderList.jsx` · `pages/inbound/AsnList.jsx` · `Receiving.jsx` · `InboundConfirm.jsx` · `PutawayOrderRegister.jsx` · `components/common/OutbOrderPickerModal.jsx`(신규) · `api/omsIbOrderApi.js` · `api/ibOrderApi.js` | 화면 |
| 문서 | `docs/design.md` · `docs/screen-list.html` · `CLAUDE.md` | 근거 |

---

### Task 1: 스키마 · 마이그레이션 · 시드

**Files:**
- Modify: `docs/schema.sql` (oms_ib_order · oms_ib_line · ib_order · ib_line · code_group/code_detail)
- Create: `docs/migration-rtngs.sql`
- Modify: `docs/seed-dev.sql` (반품존 3 + 로케이션 3)

**Interfaces:**
- Produces: 컬럼 `oms_ib_order.store_id` · `ref_outb_no`, `oms_ib_line.rsn_cd` · `rsn_dscr`, `ib_order.store_id`, `ib_line.rjct_qty`; 코드 그룹 `RTNGS_RSN`; 존 `RTN-DRY/CHL/FRZ`(biz_dvsn RTNGS) + 로케이션 `RTN-DRY-01/RTN-CHL-01/RTN-FRZ-01`(STORAGE)

- [ ] **Step 1: `docs/schema.sql` — `oms_ib_order`**

`CREATE TABLE oms_ib_order`에서 `vendor_id BIGINT NOT NULL,` → 아래로 바꾸고 CHECK 추가:

```sql
    vendor_id    BIGINT,
    store_id     BIGINT,
    ref_outb_no  VARCHAR(30),
```
`CONSTRAINT ck_oms_ib_order_status …` 줄 뒤에:
```sql
    ,CONSTRAINT ck_oms_ib_order_vndr_store CHECK ((vendor_id IS NOT NULL) <> (store_id IS NOT NULL))
```
(기존 마지막 제약 뒤 콤마 규칙에 맞춰 붙인다 — 결과적으로 `status` CHECK, `vndr_store` CHECK 둘 다 `);` 앞에 온다.)

COMMENT 블록(532~540행 근처)에 추가·수정:
```sql
COMMENT ON COLUMN oms_ib_order.vendor_id    IS '납품 벤더. 반품입고(odr_dvsn=RTNGS)는 NULL';
COMMENT ON COLUMN oms_ib_order.store_id     IS '반품 점포. 반품입고만, 벤더와 둘 중 하나';
COMMENT ON COLUMN oms_ib_order.ref_outb_no  IS '원 출고번호. 반품입고만, 선택 (느슨한 참조)';
```

- [ ] **Step 2: `docs/schema.sql` — `oms_ib_line`**

`odr_qty BIGINT NOT NULL,` 뒤에:
```sql
    rsn_cd          VARCHAR(10),
    rsn_dscr        VARCHAR(200),
```
COMMENT:
```sql
COMMENT ON COLUMN oms_ib_line.odr_qty  IS '발주 수량 (정상 입고단위 · 반품 출고단위)';
COMMENT ON COLUMN oms_ib_line.rsn_cd   IS '반품사유. RTNGS_RSN. 반품 라인만';
COMMENT ON COLUMN oms_ib_line.rsn_dscr IS '반품사유 상세. ETC일 때만';
```

- [ ] **Step 3: `docs/schema.sql` — `ib_order` · `ib_line`**

`ib_order`: `vendor_id BIGINT NOT NULL,` → `vendor_id BIGINT,` + `store_id BIGINT,` 추가, 제약 끝에
```sql
    ,CONSTRAINT ck_ib_order_vndr_store CHECK ((vendor_id IS NOT NULL) <> (store_id IS NOT NULL))
```
COMMENT:
```sql
COMMENT ON COLUMN ib_order.vendor_id IS '납품 벤더. 반품입고는 NULL';
COMMENT ON COLUMN ib_order.store_id  IS '반품 점포. 반품입고만';
```

`ib_line`: `ptawy_qty BIGINT DEFAULT 0 NOT NULL,` 뒤에 `rjct_qty BIGINT DEFAULT 0 NOT NULL,` 추가. `ck_ib_line_qty`를:
```sql
    CONSTRAINT ck_ib_line_qty CHECK (
        expct_qty > 0 AND rcvd_qty >= 0 AND rjct_qty >= 0
        AND ptawy_qty >= 0 AND ptawy_qty <= rcvd_qty
    )
```
COMMENT:
```sql
COMMENT ON COLUMN ib_line.rcvd_qty  IS '검수 양품 수량 누계 (스테이징 입)';
COMMENT ON COLUMN ib_line.rjct_qty  IS '검수 불량 수량 누계 (반품존 입). 반품입고만';
```

- [ ] **Step 4: `docs/schema.sql` — 공통코드**

`INSERT INTO code_group … ('HLD_RSN', …)` 뒤에:
```sql
INSERT INTO code_group (grp_cd, grp_nm, dscr) VALUES
    ('RTNGS_RSN', '반품사유', '반품입고 라인의 반품 사유 (oms_ib_line.rsn_cd). ETC(기타)일 때만 자유 텍스트 rsn_dscr를 받는다');
```
`HLD_RSN` detail 4줄 뒤에:
```sql
INSERT INTO code_detail (grp_cd, code_cd, code_nm, srt_seq) VALUES ('RTNGS_RSN', 'MISDLV', '오배송', 1);
INSERT INTO code_detail (grp_cd, code_cd, code_nm, srt_seq) VALUES ('RTNGS_RSN', 'DAMG', '파손', 2);
INSERT INTO code_detail (grp_cd, code_cd, code_nm, srt_seq) VALUES ('RTNGS_RSN', 'EXPIRY', '유통기한임박', 3);
INSERT INTO code_detail (grp_cd, code_cd, code_nm, srt_seq) VALUES ('RTNGS_RSN', 'UNSOLD', '미판매', 4);
INSERT INTO code_detail (grp_cd, code_cd, code_nm, srt_seq) VALUES ('RTNGS_RSN', 'ETC', '기타', 5);
```

- [ ] **Step 5: `docs/seed-dev.sql` — 반품존**

피킹 로케이션 4건 뒤(192행 근처)에:
```sql
-- 반품존 (biz_dvsn = RTNGS). 반품 검수의 불량분이 바로 들어가 보류로 묶이는 자리 — 보류가 보관(STORAGE)
-- 로케이션만 받으므로 STORAGE다. 할당 후보에서는 존 업무구분으로 제외된다.
INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('RTN-DRY', '상온 반품존', 'DRY', 'FLAT', 'RTNGS') ON CONFLICT (zon_cd) DO NOTHING;
INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('RTN-CHL', '냉장 반품존', 'CHL', 'FLAT', 'RTNGS') ON CONFLICT (zon_cd) DO NOTHING;
INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('RTN-FRZ', '냉동 반품존', 'FRZ', 'FLAT', 'RTNGS') ON CONFLICT (zon_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, ptawy_prty, max_qty)
SELECT 'RTN-DRY-01', zon_id, 'DRY', 'STORAGE', 99, 99, 5000 FROM zon WHERE zon_cd = 'RTN-DRY' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, ptawy_prty, max_qty)
SELECT 'RTN-CHL-01', zon_id, 'CHL', 'STORAGE', 99, 99, 5000 FROM zon WHERE zon_cd = 'RTN-CHL' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, ptawy_prty, max_qty)
SELECT 'RTN-FRZ-01', zon_id, 'FRZ', 'STORAGE', 99, 99, 5000 FROM zon WHERE zon_cd = 'RTN-FRZ' ON CONFLICT (loc_cd) DO NOTHING;
```

- [ ] **Step 6: `docs/migration-rtngs.sql`**

```sql
-- =====================================================================
-- 반품입고 — 입고주문·입고예정의 상대처 일반화(벤더 또는 점포) + 반품사유 + 불량수량 + 반품존.
--
-- 현재 라이브 상태 → schema.sql 상태.
-- 근거: docs/design.md 「반품입고」. 새 문서를 만들지 않고 입고주문의 구분(odr_dvsn=RTNGS)으로 받는다.
--   - oms_ib_order · ib_order: vendor_id NULL 허용 + store_id, 둘 중 하나 CHECK.
--   - oms_ib_order.ref_outb_no: 원 출고번호(느슨한 참조, 선택).
--   - oms_ib_line.rsn_cd · rsn_dscr: 반품사유 (공통코드 RTNGS_RSN).
--   - ib_line.rjct_qty: 검수 불량 누계. rcvd_qty는 양품만이라 ptawy<=rcvd 제약은 그대로.
--   - 반품존 3 + 로케이션 3 (STORAGE — 보류가 보관 로케이션만 받는다).
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — 존재 확인을 걸어 이미 적용된 항목은 건너뛴다
-- =====================================================================
DO $mig$
BEGIN
    -- 1. oms_ib_order ---------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'oms_ib_order' AND column_name = 'store_id') THEN
        ALTER TABLE oms_ib_order ALTER COLUMN vendor_id DROP NOT NULL;
        ALTER TABLE oms_ib_order ADD COLUMN store_id BIGINT;
        ALTER TABLE oms_ib_order ADD COLUMN ref_outb_no VARCHAR(30);
        ALTER TABLE oms_ib_order ADD CONSTRAINT ck_oms_ib_order_vndr_store
            CHECK ((vendor_id IS NOT NULL) <> (store_id IS NOT NULL));
        COMMENT ON COLUMN oms_ib_order.vendor_id   IS '납품 벤더. 반품입고(odr_dvsn=RTNGS)는 NULL';
        COMMENT ON COLUMN oms_ib_order.store_id    IS '반품 점포. 반품입고만, 벤더와 둘 중 하나';
        COMMENT ON COLUMN oms_ib_order.ref_outb_no IS '원 출고번호. 반품입고만, 선택 (느슨한 참조)';
        RAISE NOTICE 'oms_ib_order.store_id · ref_outb_no 추가, vendor_id NULL 허용';
    ELSE
        RAISE NOTICE 'oms_ib_order.store_id 이미 존재 — 건너뜀';
    END IF;

    -- 2. oms_ib_line ----------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'oms_ib_line' AND column_name = 'rsn_cd') THEN
        ALTER TABLE oms_ib_line ADD COLUMN rsn_cd VARCHAR(10);
        ALTER TABLE oms_ib_line ADD COLUMN rsn_dscr VARCHAR(200);
        COMMENT ON COLUMN oms_ib_line.odr_qty  IS '발주 수량 (정상 입고단위 · 반품 출고단위)';
        COMMENT ON COLUMN oms_ib_line.rsn_cd   IS '반품사유. RTNGS_RSN. 반품 라인만';
        COMMENT ON COLUMN oms_ib_line.rsn_dscr IS '반품사유 상세. ETC일 때만';
        RAISE NOTICE 'oms_ib_line.rsn_cd · rsn_dscr 추가';
    ELSE
        RAISE NOTICE 'oms_ib_line.rsn_cd 이미 존재 — 건너뜀';
    END IF;

    -- 3. ib_order -------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'ib_order' AND column_name = 'store_id') THEN
        ALTER TABLE ib_order ALTER COLUMN vendor_id DROP NOT NULL;
        ALTER TABLE ib_order ADD COLUMN store_id BIGINT;
        ALTER TABLE ib_order ADD CONSTRAINT ck_ib_order_vndr_store
            CHECK ((vendor_id IS NOT NULL) <> (store_id IS NOT NULL));
        COMMENT ON COLUMN ib_order.vendor_id IS '납품 벤더. 반품입고는 NULL';
        COMMENT ON COLUMN ib_order.store_id  IS '반품 점포. 반품입고만';
        RAISE NOTICE 'ib_order.store_id 추가, vendor_id NULL 허용';
    ELSE
        RAISE NOTICE 'ib_order.store_id 이미 존재 — 건너뜀';
    END IF;

    -- 4. ib_line.rjct_qty -----------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'ib_line' AND column_name = 'rjct_qty') THEN
        ALTER TABLE ib_line ADD COLUMN rjct_qty BIGINT DEFAULT 0 NOT NULL;
        ALTER TABLE ib_line DROP CONSTRAINT IF EXISTS ck_ib_line_qty;
        ALTER TABLE ib_line ADD CONSTRAINT ck_ib_line_qty CHECK (
            expct_qty > 0 AND rcvd_qty >= 0 AND rjct_qty >= 0
            AND ptawy_qty >= 0 AND ptawy_qty <= rcvd_qty
        );
        COMMENT ON COLUMN ib_line.rcvd_qty IS '검수 양품 수량 누계 (스테이징 입)';
        COMMENT ON COLUMN ib_line.rjct_qty IS '검수 불량 수량 누계 (반품존 입). 반품입고만';
        RAISE NOTICE 'ib_line.rjct_qty 추가, ck_ib_line_qty 재정의';
    ELSE
        RAISE NOTICE 'ib_line.rjct_qty 이미 존재 — 건너뜀';
    END IF;

    -- 5. 공통코드 RTNGS_RSN ----------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM code_group WHERE grp_cd = 'RTNGS_RSN') THEN
        INSERT INTO code_group (grp_cd, grp_nm, dscr) VALUES
            ('RTNGS_RSN', '반품사유', '반품입고 라인의 반품 사유 (oms_ib_line.rsn_cd). ETC(기타)일 때만 자유 텍스트 rsn_dscr를 받는다');
        INSERT INTO code_detail (grp_cd, code_cd, code_nm, srt_seq) VALUES
            ('RTNGS_RSN', 'MISDLV', '오배송', 1),
            ('RTNGS_RSN', 'DAMG', '파손', 2),
            ('RTNGS_RSN', 'EXPIRY', '유통기한임박', 3),
            ('RTNGS_RSN', 'UNSOLD', '미판매', 4),
            ('RTNGS_RSN', 'ETC', '기타', 5);
        RAISE NOTICE '공통코드 RTNGS_RSN 그룹 + 코드 5건 시드';
    ELSE
        RAISE NOTICE '공통코드 RTNGS_RSN 이미 존재 — 건너뜀';
    END IF;

    -- 6. 반품존 + 로케이션 ------------------------------------------------------
    INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('RTN-DRY', '상온 반품존', 'DRY', 'FLAT', 'RTNGS') ON CONFLICT (zon_cd) DO NOTHING;
    INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('RTN-CHL', '냉장 반품존', 'CHL', 'FLAT', 'RTNGS') ON CONFLICT (zon_cd) DO NOTHING;
    INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('RTN-FRZ', '냉동 반품존', 'FRZ', 'FLAT', 'RTNGS') ON CONFLICT (zon_cd) DO NOTHING;
    INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, ptawy_prty, max_qty)
    SELECT 'RTN-DRY-01', zon_id, 'DRY', 'STORAGE', 99, 99, 5000 FROM zon WHERE zon_cd = 'RTN-DRY' ON CONFLICT (loc_cd) DO NOTHING;
    INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, ptawy_prty, max_qty)
    SELECT 'RTN-CHL-01', zon_id, 'CHL', 'STORAGE', 99, 99, 5000 FROM zon WHERE zon_cd = 'RTN-CHL' ON CONFLICT (loc_cd) DO NOTHING;
    INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, ptawy_prty, max_qty)
    SELECT 'RTN-FRZ-01', zon_id, 'FRZ', 'STORAGE', 99, 99, 5000 FROM zon WHERE zon_cd = 'RTN-FRZ' ON CONFLICT (loc_cd) DO NOTHING;
    RAISE NOTICE '반품존 3 + 로케이션 3 (이미 있으면 유지)';
END
$mig$;

-- 확인:
--   SELECT column_name FROM information_schema.columns WHERE table_name IN ('oms_ib_order','ib_order') AND column_name = 'store_id';
--   SELECT z.zon_cd, l.loc_cd, l.loc_typ FROM loc l JOIN zon z ON z.zon_id = l.zon_id WHERE z.biz_dvsn = 'RTNGS';
```

- [ ] **Step 7: 문법 확인**

Run: `grep -c "ck_ib_order_vndr_store\|ck_oms_ib_order_vndr_store\|RTNGS_RSN\|rjct_qty" docs/schema.sql docs/migration-rtngs.sql`
Expected: 두 파일 모두 0이 아닌 수. `docs/schema.sql`의 `CREATE TABLE ib_line` 블록에서 `rjct_qty`가 컬럼과 CHECK 양쪽에 있는지 눈으로 확인.

---

### Task 2: 엔티티 — 상대처 · 반품사유 · 불량수량

**Files:**
- Modify: `src/main/java/com/project/omsback/inbound/entity/OmsIbOrder.java`
- Modify: `src/main/java/com/project/omsback/inbound/entity/OmsIbLine.java`
- Modify: `src/main/java/com/project/wmsback/inbound/entity/IbOrder.java`
- Modify: `src/main/java/com/project/wmsback/inbound/entity/IbLine.java`
- Test: `src/test/java/com/project/wmsback/inbound/entity/IbOrderTest.java` (확장)
- Test: `src/test/java/com/project/omsback/inbound/entity/OmsIbOrderTest.java` (신규)

**Interfaces:**
- Produces:
  - `IbOrder.RTNGS = "RTNGS"`, `boolean isRtngs()`, `Store getStore()`, `String rcvUomCd(Prod)` (반품이면 `outbUomCd`, 아니면 `inbUomCd`), 빌더에 `store(Store)`
  - `OmsIbOrder.isRtngs()`, `getStore()`, `getRefOutbNo()`, `odrUomCd(Prod)`, 빌더 `store` · `refOutbNo`, `update(Vendor, Store, String refOutbNo, LocalDate, String odrDvsn, String picNm, String rmk, List<OmsIbLine>)`
  - `OmsIbLine.getRsnCd()` · `getRsnDscr()`, 빌더 `rsnCd` · `rsnDscr`
  - `IbLine.getRjctQty()`, `reject(long)`, `cancelReject(long)`, `progressStatus()`가 `rjct` 반영

- [ ] **Step 1: 실패 테스트 — `IbOrderTest`에 Nested 추가**

`IbOrderTest` 맨 아래 `RequireRevertible` 클래스 뒤에:

```java
    @Nested
    @DisplayName("상대처 — 구분과 짝이 맞아야 한다")
    class Partner {

        private IbOrder.IbOrderBuilder base() {
            return IbOrder.builder().ibNo("IB-1").omsIbOrderId(1L).expctDe(LocalDate.of(2026, 8, 25));
        }

        @Test
        @DisplayName("정상 입고는 벤더, 반품입고는 점포")
        void vendorForNormalStoreForRtngs() {
            assertDoesNotThrow(() -> base().vendor(mock(Vendor.class)).odrDvsn("NRML").build());
            IbOrder rtngs = base().store(mock(Store.class)).odrDvsn("RTNGS").build();
            assertTrue(rtngs.isRtngs());
        }

        @Test
        @DisplayName("반품인데 벤더 / 정상인데 점포 / 둘 다 / 둘 다 아님은 거부")
        void rejectsMismatch() {
            assertThrows(IllegalArgumentException.class, () -> base().vendor(mock(Vendor.class)).odrDvsn("RTNGS").build());
            assertThrows(IllegalArgumentException.class, () -> base().store(mock(Store.class)).odrDvsn("NRML").build());
            assertThrows(IllegalArgumentException.class, () -> base().vendor(mock(Vendor.class)).store(mock(Store.class)).odrDvsn("NRML").build());
            assertThrows(IllegalArgumentException.class, () -> base().odrDvsn("NRML").build());
        }

        @Test
        @DisplayName("검수 단위 — 정상은 입고단위, 반품은 출고단위")
        void rcvUomCd() {
            Prod prod = mock(Prod.class);
            when(prod.getInbUomCd()).thenReturn("BOX");
            when(prod.getOutbUomCd()).thenReturn("EA");
            assertEquals("BOX", base().vendor(mock(Vendor.class)).odrDvsn("NRML").build().rcvUomCd(prod));
            assertEquals("EA", base().store(mock(Store.class)).odrDvsn("RTNGS").build().rcvUomCd(prod));
        }
    }

    @Nested
    @DisplayName("IbLine — 불량(rjct)")
    class LineReject {

        @Test
        @DisplayName("불량만 온 라인은 예정이 아니라 검수중이고, 양품+불량이 예정에 닿으면 적치 축으로 넘어간다")
        void rjctCountsForProgress() {
            IbOrder order = order(100, 100);
            order.startReceiving();
            line(order, 0).reject(30);                 // 불량만
            line(order, 1).receive(60);
            line(order, 1).reject(40);                 // 양품 60 + 불량 40 = 예정

            assertEquals(IbPrgr.RECEIVING, line(order, 0).progressStatus());
            assertEquals(IbPrgr.PTAWY_DRCT, line(order, 1).progressStatus());
            assertEquals(30L, line(order, 0).getRjctQty());
        }

        @Test
        @DisplayName("불량은 적치 대상이 아니다 — 양품만 적치되면 확정된다")
        void confirmIgnoresRjct() {
            IbOrder order = order(100);
            order.startReceiving();
            line(order, 0).receive(60);
            line(order, 0).reject(40);
            line(order, 0).putaway(60);

            assertDoesNotThrow(order::confirm);
        }

        @Test
        @DisplayName("불량 취소는 rjct만 줄인다")
        void cancelReject() {
            IbOrder order = order(100);
            order.startReceiving();
            line(order, 0).reject(40);
            line(order, 0).cancelReject(40);

            assertEquals(0L, line(order, 0).getRjctQty());
            assertEquals(IbPrgr.SCHEDULED, line(order, 0).progressStatus());
        }
    }
```

import 추가: `import com.project.mdm.store.entity.Store;` · `import static org.junit.jupiter.api.Assertions.assertTrue;` · `import static org.mockito.Mockito.when;`

- [ ] **Step 2: 실패 테스트 — `OmsIbOrderTest` 신규**

`src/test/java/com/project/omsback/inbound/entity/OmsIbOrderTest.java`:

```java
package com.project.omsback.inbound.entity;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.store.entity.Store;
import com.project.mdm.vendor.entity.Vendor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 입고주문의 상대처 — 구분이 반품이면 점포, 아니면 벤더. 그 짝은 엔티티가 지킨다(DB CHECK는 둘 중 하나까지만). */
class OmsIbOrderTest {

    private OmsIbOrder.OmsIbOrderBuilder base() {
        return OmsIbOrder.builder().omsIbNo("PO-1").expctDe(LocalDate.of(2026, 8, 25));
    }

    @Test
    @DisplayName("정상은 벤더, 반품은 점포 + 원 출고번호(선택)")
    void partnerByDvsn() {
        assertDoesNotThrow(() -> base().vendor(mock(Vendor.class)).odrDvsn("NRML").build());
        OmsIbOrder rtngs = base().store(mock(Store.class)).odrDvsn("RTNGS").refOutbNo("OB-20260820-001").build();
        assertEquals("OB-20260820-001", rtngs.getRefOutbNo());
        assertNull(rtngs.getVendor());
    }

    @Test
    @DisplayName("짝이 어긋나면 거부 — 생성도 수정도")
    void rejectsMismatch() {
        assertThrows(IllegalArgumentException.class, () -> base().vendor(mock(Vendor.class)).odrDvsn("RTNGS").build());
        assertThrows(IllegalArgumentException.class, () -> base().store(mock(Store.class)).odrDvsn("NRML").build());

        OmsIbOrder order = base().vendor(mock(Vendor.class)).odrDvsn("NRML").build();
        assertThrows(IllegalArgumentException.class, () ->
                order.update(mock(Vendor.class), null, null, LocalDate.of(2026, 8, 26), "RTNGS", null, null, List.of()));
    }

    @Test
    @DisplayName("정상 발주에는 원 출고번호를 두지 않는다")
    void refOutbNoOnlyForRtngs() {
        OmsIbOrder order = base().vendor(mock(Vendor.class)).odrDvsn("NRML").refOutbNo("OB-1").build();
        assertNull(order.getRefOutbNo());
    }

    @Test
    @DisplayName("발주 단위 — 정상은 입고단위, 반품은 출고단위")
    void odrUomCd() {
        Prod prod = mock(Prod.class);
        when(prod.getInbUomCd()).thenReturn("BOX");
        when(prod.getOutbUomCd()).thenReturn("EA");
        assertEquals("BOX", base().vendor(mock(Vendor.class)).odrDvsn("NRML").build().odrUomCd(prod));
        assertEquals("EA", base().store(mock(Store.class)).odrDvsn("RTNGS").build().odrUomCd(prod));
    }
}
```

- [ ] **Step 3: 실패 확인**

Run: `./mvnw test -Dtest=IbOrderTest,OmsIbOrderTest -q`
Expected: 컴파일 실패 (`store`, `reject`, `rcvUomCd` 없음)

- [ ] **Step 4: `IbOrder` 구현**

import에 `com.project.mdm.store.entity.Store;` · `com.project.mdm.prod.entity.Prod;` 추가. `vendor` 필드를:

```java
    /** 납품 벤더. 반품입고(odr_dvsn=RTNGS)는 null — 그때는 store가 상대다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    /** 반품 점포. 반품입고만. 벤더와 둘 중 정확히 하나 — 생성자가 검증한다 (DB CHECK는 「둘 중 하나」까지) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;
```

`odrDvsn` 필드 위 상수:
```java
    /** 반품입고 발주구분 (공통코드 ODR_DVSN). 상대처·검수 단위·검수 판정을 가른다 */
    public static final String RTNGS = "RTNGS";
```

생성자:
```java
    @Builder
    private IbOrder(String ibNo, Long omsIbOrderId, Vendor vendor, Store store, LocalDate expctDe, String odrDvsn) {
        requirePartnerMatches(odrDvsn, vendor, store, ibNo);
        this.ibNo = ibNo;
        this.omsIbOrderId = omsIbOrderId;
        this.vendor = vendor;
        this.store = store;
        this.expctDe = expctDe;
        this.odrDvsn = odrDvsn;
        this.status = IbStatus.SCHEDULED;
    }

    /** 반품이면 점포, 아니면 벤더 — 둘 다거나 둘 다 아니면 거부 */
    static void requirePartnerMatches(String odrDvsn, Vendor vendor, Store store, String no) {
        boolean rtngs = RTNGS.equals(odrDvsn);
        if (rtngs && (store == null || vendor != null)) {
            throw new IllegalArgumentException("반품입고는 점포만 상대처로 둘 수 있습니다: " + no);
        }
        if (!rtngs && (vendor == null || store != null)) {
            throw new IllegalArgumentException("정상 입고는 벤더만 상대처로 둘 수 있습니다: " + no);
        }
    }

    public boolean isRtngs() {
        return RTNGS.equals(odrDvsn);
    }

    /** 검수 입력 단위 — 정상은 입고단위(벤더 납품 단위), 반품은 출고단위(점포가 받은 단위로 돌아온다) */
    public String rcvUomCd(Prod prod) {
        return isRtngs() ? prod.getOutbUomCd() : prod.getInbUomCd();
    }
```

- [ ] **Step 5: `IbLine` 구현**

`ptawyQty` 필드 뒤:
```java
    /** 검수 불량 수량 누계 — 반품존에 받아 보류된 분. 반품입고만 0보다 크다. 적치 대상이 아니다 */
    @Column(name = "rjct_qty", nullable = false)
    private Long rjctQty;
```
생성자에 `this.rjctQty = 0L;`. `cancelReceive` 뒤:
```java
    /** 불량 반영 (증분 누적). 반품존에 받아 보류된다 — rcvdQty와 별개 축이라 적치·확정 조건에 끼지 않는다 */
    public void reject(long qty) {
        this.rjctQty += qty;
    }

    public void cancelReject(long qty) {
        this.rjctQty -= qty;
    }
```
`progressStatus()`의 두 줄을:
```java
        long arrived = rcvdQty + rjctQty;
        if (arrived == 0) return IbPrgr.SCHEDULED;         // 아직 안 옴 (불량만 와도 온 것이다)
        if (arrived < expctQty) return IbPrgr.RECEIVING;   // 덜 옴
```

- [ ] **Step 6: `OmsIbOrder` 구현**

import `com.project.mdm.store.entity.Store;` · `com.project.mdm.prod.entity.Prod;`. `vendor`를 nullable로, `store` · `refOutbNo` 추가:

```java
    /** 납품 벤더. 반품입고(odr_dvsn=RTNGS)는 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    /** 반품 점포. 반품입고만 — 벤더와 둘 중 정확히 하나 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    /** 원 출고번호 (느슨한 참조, 선택). 반품입고만 — 라인 미리채움의 출처를 남긴다 */
    @Column(name = "ref_outb_no", length = 30)
    private String refOutbNo;
```
`odrDvsn` javadoc을 한 줄로 교체: `/** 발주구분 (공통코드 ODR_DVSN: NRML 정상 / URGT 긴급 / RTNGS 반품입고). 반품은 상대처(점포)·수량 단위(출고단위)·검수 판정(양품/불량)을 가른다 */`

상수·생성자·update:
```java
    public static final String RTNGS = "RTNGS";

    @Builder
    private OmsIbOrder(String omsIbNo, Vendor vendor, Store store, String refOutbNo, LocalDate expctDe,
                       String odrDvsn, String picNm, String rmk) {
        requirePartnerMatches(odrDvsn, vendor, store, omsIbNo);
        this.omsIbNo = omsIbNo;
        this.vendor = vendor;
        this.store = store;
        this.refOutbNo = RTNGS.equals(odrDvsn) ? refOutbNo : null;
        this.expctDe = expctDe;
        this.odrDvsn = odrDvsn;
        this.picNm = picNm;
        this.rmk = rmk;
        this.status = OmsIbStatus.CREATED;
    }

    private static void requirePartnerMatches(String odrDvsn, Vendor vendor, Store store, String no) {
        boolean rtngs = RTNGS.equals(odrDvsn);
        if (rtngs && (store == null || vendor != null)) {
            throw new IllegalArgumentException("반품입고는 점포만 상대처로 둘 수 있습니다: " + no);
        }
        if (!rtngs && (vendor == null || store != null)) {
            throw new IllegalArgumentException("정상 입고는 벤더만 상대처로 둘 수 있습니다: " + no);
        }
    }

    public boolean isRtngs() {
        return RTNGS.equals(odrDvsn);
    }

    /** 발주 수량의 단위 — 정상은 입고단위, 반품은 출고단위(점포가 받은 단위로 돌아온다) */
    public String odrUomCd(Prod prod) {
        return isRtngs() ? prod.getOutbUomCd() : prod.getInbUomCd();
    }
```
`update` 시그니처와 본문:
```java
    public void update(Vendor vendor, Store store, String refOutbNo, LocalDate expctDe, String odrDvsn,
                       String picNm, String rmk, List<OmsIbLine> newLines) {
        if (status != OmsIbStatus.CREATED) {
            throw new IllegalStateException(
                    "작성 상태의 주문만 수정할 수 있습니다. 확정된 주문은 확정취소가 먼저입니다 ("
                            + status.getLabel() + "): " + omsIbNo);
        }
        requirePartnerMatches(odrDvsn, vendor, store, omsIbNo);
        this.vendor = vendor;
        this.store = store;
        this.refOutbNo = RTNGS.equals(odrDvsn) ? refOutbNo : null;
        this.expctDe = expctDe;
        this.odrDvsn = odrDvsn;
        this.picNm = picNm;
        this.rmk = rmk;
        lines.clear();
        addLines(newLines);
    }
```

- [ ] **Step 7: `OmsIbLine` 구현**

```java
    /** 반품사유 (공통코드 RTNGS_RSN). 반품 라인만, 정상 발주는 null */
    @Column(name = "rsn_cd", length = 10)
    private String rsnCd;

    /** 반품사유 상세. ETC일 때만 */
    @Column(name = "rsn_dscr", length = 200)
    private String rsnDscr;

    @Builder
    private OmsIbLine(Prod prod, Long odrQty, String rsnCd, String rsnDscr) {
        this.prod = prod;
        this.odrQty = odrQty;
        this.rsnCd = rsnCd;
        this.rsnDscr = rsnDscr;
    }
```

- [ ] **Step 8: 컴파일 + 테스트**

Run: `./mvnw compile -q && ./mvnw test -Dtest=IbOrderTest,OmsIbOrderTest -q`
Expected: PASS. (`OmsIbOrderService.confirm`이 `IbOrder.builder()`에 vendor만 넘기는 건 Task 8 전까지 정상 발주에서 그대로 통과한다.)

---

### Task 3: `RtngsLocResolver`

**Files:**
- Create: `src/main/java/com/project/wmsback/inbound/service/RtngsLocResolver.java`
- Modify: `src/main/java/com/project/wmsback/warehouse/repository/LocRepository.java` (쿼리 1개)
- Test: `src/test/java/com/project/wmsback/inbound/service/RtngsLocResolverTest.java`

**Interfaces:**
- Produces: `@Component RtngsLocResolver { Loc resolve(Prod prod); static boolean inRtngsZon(Loc loc); }`
- Produces: `LocRepository.findRtngsLocs(TmpZon tmpZon, LocTyp storage, BizDvsn rtngs)` — `ptawy_prty asc, loc_cd asc`

- [ ] **Step 1: 실패 테스트**

```java
package com.project.wmsback.inbound.service;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.TmpZon;
import com.project.wmsback.warehouse.entity.BizDvsn;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Zon;
import com.project.wmsback.warehouse.repository.LocRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 불량 도착지는 상수가 아니라 해석이다 — 상품 온도대와 같은 반품존의 첫 STORAGE 로케이션. */
class RtngsLocResolverTest {

    private final LocRepository locRepository = mock(LocRepository.class);
    private final RtngsLocResolver resolver = new RtngsLocResolver(locRepository);

    private Prod prod(TmpZon tmpZon) {
        Prod prod = mock(Prod.class);
        when(prod.getTmpZon()).thenReturn(tmpZon);
        when(prod.getProdCd()).thenReturn("PROD-0001");
        return prod;
    }

    @Test
    @DisplayName("온도대가 같은 반품존의 첫 로케이션")
    void resolvesFirstLocOfMatchingZone() {
        Loc first = mock(Loc.class);
        when(locRepository.findRtngsLocs(TmpZon.CHL, LocTyp.STORAGE, BizDvsn.RTNGS)).thenReturn(List.of(first, mock(Loc.class)));

        assertSame(first, resolver.resolve(prod(TmpZon.CHL)));
    }

    @Test
    @DisplayName("반품존이 없으면 예외 — 불량을 받을 자리가 없다")
    void throwsWhenNoZone() {
        when(locRepository.findRtngsLocs(TmpZon.FRZ, LocTyp.STORAGE, BizDvsn.RTNGS)).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> resolver.resolve(prod(TmpZon.FRZ)));
    }

    @Test
    @DisplayName("반품존 판정 — 존이 없거나 업무구분이 다르면 아니다")
    void inRtngsZon() {
        Zon rtngs = mock(Zon.class);
        when(rtngs.getBizDvsn()).thenReturn(BizDvsn.RTNGS);
        Zon storage = mock(Zon.class);
        when(storage.getBizDvsn()).thenReturn(BizDvsn.STRG);
        Loc a = mock(Loc.class); when(a.getZon()).thenReturn(rtngs);
        Loc b = mock(Loc.class); when(b.getZon()).thenReturn(storage);
        Loc c = mock(Loc.class); when(c.getZon()).thenReturn(null);

        assertTrue(RtngsLocResolver.inRtngsZon(a));
        assertFalse(RtngsLocResolver.inRtngsZon(b));
        assertFalse(RtngsLocResolver.inRtngsZon(c));
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./mvnw test -Dtest=RtngsLocResolverTest -q` → 컴파일 실패

- [ ] **Step 3: 구현**

`LocRepository`에 `findAllByTmpZonAndLocTypOrderByPtawyPrtyAsc` 위:
```java
    /** 반품 검수의 불량 도착지 후보 — 상품 온도대와 같은 반품존의 보관 로케이션 (적치 우선순위 순) */
    @Query("select l from Loc l join l.zon z"
            + " where l.locTyp = :storage and z.bizDvsn = :rtngs and l.tmpZon = :tmpZon"
            + " order by l.ptawyPrty asc, l.locCd asc")
    List<Loc> findRtngsLocs(@Param("tmpZon") TmpZon tmpZon,
                            @Param("storage") LocTyp storage, @Param("rtngs") BizDvsn rtngs);
```

`RtngsLocResolver.java`:
```java
package com.project.wmsback.inbound.service;

import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.warehouse.entity.BizDvsn;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.repository.LocRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 반품 검수의 불량 도착지 — 상품 온도대와 같은 반품존(biz_dvsn=RTNGS)의 첫 보관 로케이션.
 * RCV-STAGE처럼 코드값을 상수로 박지 않는다 — 온도대마다 자리가 다르고, 판정(inRtngsZon)도 여기 한 곳이다.
 */
@Component
@RequiredArgsConstructor
public class RtngsLocResolver {

    private final LocRepository locRepository;

    /** 반품존 판정. 검수 취소·검수 이력의 판정 열·원천 대사가 쓴다 */
    public static boolean inRtngsZon(Loc loc) {
        return loc.getZon() != null && loc.getZon().getBizDvsn() == BizDvsn.RTNGS;
    }

    public Loc resolve(Prod prod) {
        List<Loc> locs = locRepository.findRtngsLocs(prod.getTmpZon(), LocTyp.STORAGE, BizDvsn.RTNGS);
        if (locs.isEmpty()) {
            throw new IllegalStateException("온도대 " + prod.getTmpZon() + " 반품존 로케이션이 없습니다: " + prod.getProdCd());
        }
        return locs.get(0);
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./mvnw test -Dtest=RtngsLocResolverTest -q` → PASS

---

### Task 4: `InvHldService.holdOn` 추출

**Files:**
- Modify: `src/main/java/com/project/wmsback/inventory/service/InvHldService.java:84-158`
- Test: `src/test/java/com/project/wmsback/inventory/service/InvHldServiceTest.java` (신규)

**Interfaces:**
- Produces: `public String holdOn(Inv inv, long qty, String rsnCd, String rsnDscr)` — 검증(보관 로케이션 · 가용 이내 · 사유) → `invStore.hold` → `InvHld` + `InvHldAcrst` 저장 → 보류번호 반환. 호출자가 `inv` 행 락을 이미 잡고 있어야 한다.

- [ ] **Step 1: 실패 테스트**

```java
package com.project.wmsback.inventory.service;

import com.project.mdm.nbr.service.NbrService;
import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvHld;
import com.project.wmsback.inventory.repository.InvHldAcrstRepository;
import com.project.wmsback.inventory.repository.InvHldRepository;
import com.project.wmsback.inventory.repository.InvHldRlzAcrstRepository;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 보류 한 건의 등록 단위(holdOn) — 화면 등록과 반품 검수가 같이 쓴다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvHldServiceTest {

    @Mock InvStore invStore;
    @Mock InvHldRepository invHldRepository;
    @Mock InvHldAcrstRepository invHldAcrstRepository;
    @Mock InvHldRlzAcrstRepository invHldRlzAcrstRepository;
    @Mock RsnValidator rsnValidator;
    @Mock NbrService nbrService;

    private InvHldService service;
    private Inv inv;
    private Loc loc;

    @BeforeEach
    void setUp() {
        service = new InvHldService(invStore, invHldRepository, invHldAcrstRepository, invHldRlzAcrstRepository,
                rsnValidator, nbrService);
        Prod prod = mock(Prod.class);
        when(prod.getProdCd()).thenReturn("PROD-0001");
        loc = mock(Loc.class);
        when(loc.getLocTyp()).thenReturn(LocTyp.STORAGE);
        when(loc.getLocCd()).thenReturn("RTN-DRY-01");
        inv = mock(Inv.class);
        when(inv.getProd()).thenReturn(prod);
        when(inv.getLoc()).thenReturn(loc);
        when(inv.getLot()).thenReturn(mock(Lot.class));
        when(inv.avalQty()).thenReturn(100L);
        when(rsnValidator.validate(eq("HLD_RSN"), any(), eq("DAMG"), any())).thenReturn(null);
        when(nbrService.issue(eq("HLD_NO"), any())).thenReturn("HD-20260825-001");
    }

    @Test
    @DisplayName("가용 이내면 hld 증가 + 보류 건 + 실적 저장, 보류번호 반환")
    void holdsAndRecords() {
        String hldNo = service.holdOn(inv, 40, "DAMG", null);

        assertEquals("HD-20260825-001", hldNo);
        verify(invStore).hold(inv, 40);
        ArgumentCaptor<InvHld> captor = ArgumentCaptor.forClass(InvHld.class);
        verify(invHldRepository).save(captor.capture());
        assertEquals(40L, captor.getValue().getHldQty());
        assertEquals("DAMG", captor.getValue().getRsnCd());
        verify(invHldAcrstRepository).save(any());
    }

    @Test
    @DisplayName("가용을 넘으면 거부하고 아무것도 저장하지 않는다")
    void rejectsOverAval() {
        assertThrows(IllegalArgumentException.class, () -> service.holdOn(inv, 101, "DAMG", null));
        verify(invStore, never()).hold(any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("보관 로케이션이 아니면 거부")
    void rejectsNonStorage() {
        when(loc.getLocTyp()).thenReturn(LocTyp.STAGE);
        assertThrows(IllegalArgumentException.class, () -> service.holdOn(inv, 1, "DAMG", null));
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./mvnw test -Dtest=InvHldServiceTest -q` → 컴파일 실패 (`holdOn` 없음)

- [ ] **Step 3: 구현 — `registerOne`을 `holdOn`으로 쪼갠다**

기존 `registerOne(InvHldRegisterRequest.Item item, Inv inv)`를:
```java
    private String registerOne(InvHldRegisterRequest.Item item, Inv inv) {
        if (item.getQty() == null || item.getQty() < 1) {
            throw new IllegalArgumentException("보류수량은 1 이상이어야 합니다.");
        }
        return holdOn(inv, item.getQty(), item.getRsnCd(), item.getRsnDscr());
    }

    /**
     * 재고 행 하나에 보류 한 건 — 검증 · hld 증가 · 보류 건 · 등록 실적. 화면 등록과 반품 검수(불량분)가 같이 쓴다.
     * 호출자가 그 재고 행의 락을 이미 잡고 있어야 하고, 채번(HLD_NO)이 여기서 일어나므로
     * 잡을 재고 락이 더 남았을 때 부르면 안 된다 (락 순서 — 채번은 재고 락을 전부 잡은 뒤).
     */
    @Transactional
    public String holdOn(Inv inv, long qty, String rsnCd, String rsnDscr) {
        if (qty < 1) {
            throw new IllegalArgumentException("보류수량은 1 이상이어야 합니다.");
        }
        String dscr = rsnValidator.validate(HLD_RSN_GRP_CD, "보류사유", rsnCd, rsnDscr);

        Prod prodEntity = inv.getProd();
        Lot lotEntity = inv.getLot();
        Loc locEntity = inv.getLoc();

        // v1 보류 대상은 보관 재고만 — 스테이징까지 허용하면 적치·출고확정의 수량 체크에도 파급이 생긴다
        if (locEntity.getLocTyp() != LocTyp.STORAGE) {
            throw new IllegalArgumentException("보관 로케이션의 재고만 보류할 수 있습니다: " + locEntity.getLocCd());
        }
        // 예약과 보류는 배타 — 보류는 가용재고에서만 잡는다 (예약 잔량이 있어도 남은 가용분은 보류 가능)
        if (qty > inv.avalQty()) {
            throw new IllegalArgumentException("보류수량이 가용재고를 초과했습니다 (가용 " + inv.avalQty() + "): "
                    + prodEntity.getProdCd() + " @ " + locEntity.getLocCd());
        }

        invStore.hold(inv, qty);
        InvHld hld = InvHld.builder()
                .hldNo(nbrService.issue(HLD_NO_RULE_CD, LocalDate.now()))
                .prod(prodEntity).loc(locEntity).lot(lotEntity)
                .hldQty(qty)
                .rsnCd(rsnCd).rsnDscr(dscr)
                .build();
        invHldRepository.save(hld);
        // 실적은 자기완결 로그 — 건이 갱신돼도 등록 시점 기록이 보존된다
        invHldAcrstRepository.save(InvHldAcrst.builder()
                .hldNo(hld.getHldNo())
                .prod(prodEntity).loc(locEntity).lot(lotEntity)
                .hldQty(qty)
                .rsnCd(rsnCd).rsnDscr(dscr)
                .build());
        return hld.getHldNo();
    }
```

- [ ] **Step 4: 통과 확인**

Run: `./mvnw test -Dtest=InvHldServiceTest -q` → PASS

---

### Task 5: 검수 규칙 — 반품 skip

**Files:**
- Modify: `src/main/java/com/project/wmsback/strategy/inspection/component/InspectionContext.java`
- Modify: `src/main/java/com/project/wmsback/strategy/inspection/component/InspectionRule.java` (두 `skipReason`, `SHELF_LIFE_PCT.minMfgDt`의 컨텍스트 생성)
- Modify: `src/main/java/com/project/wmsback/strategy/inspection/service/InspectionService.java` (`checkReceive` · `evaluateOne` 오버로드 · `minMfgDts`)
- Test: `src/test/java/com/project/wmsback/strategy/inspection/component/InspectionRuleTest.java` (케이스 추가)

**Interfaces:**
- Produces: `InspectionContext(Prod prod, LocalDate receiptDt, LocalDate mfgDt, InspectionQueryRepository lotQuery, boolean rtngs, boolean rjctOnly)`
- Produces: `InspectionService.evaluateOne(ruleDefs, prod, receiptDt, mfgDt, boolean rtngs, boolean rjctOnly)`; 기존 4-인자 오버로드는 `(…, false, false)`로 위임(`InspPlcyService` 미리보기가 쓴다)
- Consumes: `IbOrder.isRtngs()` (Task 2)

- [ ] **Step 1: 실패 테스트 — `InspectionRuleTest`**

`ctx(LocalDate mfgDt)` 헬퍼를 6-인자 생성자로 바꾸고, 반품용 헬퍼 추가:
```java
    private InspectionContext ctx(LocalDate mfgDt) {
        return new InspectionContext(prod, RECEIPT_DT, mfgDt, lotQuery, false, false);
    }

    private InspectionContext rtngsCtx(LocalDate mfgDt, boolean rjctOnly) {
        return new InspectionContext(prod, RECEIPT_DT, mfgDt, lotQuery, true, rjctOnly);
    }
```
`ShelfLifePct` Nested 안에:
```java
        @Test
        @DisplayName("반품에서 양품이 0인 라인(불량만)은 판정하지 않는다 — 불량으로 받는 물건에 잔여수명 하한을 걸 이유가 없다")
        void skipsRjctOnlyLine() {
            when(prod.getShelfLifeDays()).thenReturn(100);
            assertEquals("양품 없음 (불량만 입고)", rule.skipReason(rtngsCtx(RECEIPT_DT.minusDays(90), true)).orElseThrow());
            assertTrue(rule.skipReason(rtngsCtx(RECEIPT_DT.minusDays(1), false)).isEmpty());
        }
```
`LotDateReverse` Nested 안에 (기존 클래스명을 파일에서 확인해 그 안에 넣는다):
```java
        @Test
        @DisplayName("반품은 역순 제한 대상이 아니다 — 오래된 Lot이 FEFO 앞으로 가는 것이 반품에서는 맞다")
        void skipsForRtngs() {
            when(prod.getShelfLifeDays()).thenReturn(100);
            assertEquals("반품은 역순 제한 대상이 아님", rule.skipReason(rtngsCtx(RECEIPT_DT.minusDays(30), false)).orElseThrow());
        }
```

- [ ] **Step 2: 실패 확인**

Run: `./mvnw test -Dtest=InspectionRuleTest -q` → 컴파일 실패

- [ ] **Step 3: 구현**

`InspectionContext`:
```java
public record InspectionContext(
        Prod prod,
        LocalDate receiptDt,
        LocalDate mfgDt,
        InspectionQueryRepository lotQuery,
        /** 반품입고(odr_dvsn=RTNGS)인가 — 역순 제한이 빠진다 */
        boolean rtngs,
        /** 이 라인이 불량만 받는가(양품 0) — 잔여수명 하한이 빠진다. 힌트(minMfgDt) 계산에서는 false */
        boolean rjctOnly
) {
}
```

`InspectionRule.SHELF_LIFE_PCT.skipReason` — `제조일자 없음` 검사 앞에:
```java
            if (ctx.rjctOnly()) {
                return Optional.of("양품 없음 (불량만 입고)");
            }
```
`SHELF_LIFE_PCT.minMfgDt`의 `new InspectionContext(ctx.prod(), ctx.receiptDt(), candidate, ctx.lotQuery())` → `new InspectionContext(ctx.prod(), ctx.receiptDt(), candidate, ctx.lotQuery(), ctx.rtngs(), false)`.

`LOT_DATE_REVERSE.skipReason` — 맨 앞에:
```java
            if (ctx.rtngs()) {
                return Optional.of("반품은 역순 제한 대상이 아님");
            }
```
`LOT_DATE_REVERSE.minMfgDt` — 맨 앞에 `if (ctx.rtngs()) return Optional.empty();` (반품엔 하한이 없다).

`InspectionService`:
- `checkReceive`의 루프 안 `evaluateOne(ruleDefs, prod, receiptDt, line.getMfgDt())` →
```java
            boolean rjctOnly = (line.getInspectQty() == null || line.getInspectQty() == 0)
                    && line.getRjctQty() != null && line.getRjctQty() > 0;
            List<InspRuleResult> results = evaluateOne(ruleDefs, prod, receiptDt, line.getMfgDt(), order.isRtngs(), rjctOnly);
```
  (`ReceiveRequest.Line.getRjctQty()`는 Task 6에서 생긴다 — Task 5와 6을 같은 컴파일 단위로 진행한다. Task 5 단독으로 컴파일하려면 이 두 줄만 Task 6 뒤로 미룬다.)
- `evaluateOne` 오버로드:
```java
    public List<InspRuleResult> evaluateOne(List<InspPlcyDefinition.RuleDef> ruleDefs,
                                            Prod prod, LocalDate receiptDt, LocalDate mfgDt) {
        return evaluateOne(ruleDefs, prod, receiptDt, mfgDt, false, false);
    }

    public List<InspRuleResult> evaluateOne(List<InspPlcyDefinition.RuleDef> ruleDefs,
                                            Prod prod, LocalDate receiptDt, LocalDate mfgDt,
                                            boolean rtngs, boolean rjctOnly) {
        InspectionContext ctx = new InspectionContext(prod, receiptDt, mfgDt, inspectionQueryRepository, rtngs, rjctOnly);
        … (기존 본문 그대로)
    }
```
- `minMfgDts`: `InspMinMfgDtRequest.Item`에 `rtngs`가 없으므로 `new InspectionContext(prod, receiptDt, null, inspectionQueryRepository, false, false)`. (반품 화면의 하한 힌트는 역순 규칙 하한이 빠져야 맞지만, 힌트는 안내이고 판정이 최종이라 v1에서는 그대로 둔다 — Task 13에서 반품 문서는 하한 힌트를 부르지 않는다.)

- [ ] **Step 4: 통과 확인**

Run: `./mvnw test -Dtest=InspectionRuleTest,InspectionServiceTest -q` → PASS

---

### Task 6: `ReceivingService` — 양품/불량 분기 · 취소 · 판정

**Files:**
- Modify: `src/main/java/com/project/wmsback/inbound/dto/ReceiveRequest.java`
- Modify: `src/main/java/com/project/wmsback/inbound/dto/ReceiptResponse.java`
- Modify: `src/main/java/com/project/wmsback/inbound/service/ReceivingService.java`
- Test: `src/test/java/com/project/wmsback/inbound/service/ReceivingServiceTest.java` (setUp 생성자 갱신 + 케이스 추가)

**Interfaces:**
- Consumes: `RtngsLocResolver.resolve/inRtngsZon` (Task 3), `InvHldService.holdOn` (Task 4), `IbOrder.isRtngs/rcvUomCd` · `IbLine.reject/cancelReject` (Task 2)
- Produces: `ReceiveRequest.Line { Long rjctQty; String rjctRsnCd; String rjctRsnDscr; }`, `ReceiptResponse.dcsn` (`"GOOD"`/`"RJCT"`), `ReceiptResponse.from(InvHist, boolean cancelled, String rcvUomCd, boolean rjct)`
- `ReceivingService` 생성자: `(IbOrderRepository, IbLineRepository, LotIssuer, LocRepository, InvHistRepository, InvStore, ProdRepository, InspectionService, RtngsLocResolver, InvHldService)`

- [ ] **Step 1: `ReceiveRequest.Line` 필드**

```java
        /** 이번 불량수량 — 입고단위. 반품입고만. 반품존에 받아 즉시 보류된다 */
        private Long rjctQty;
        /** 불량사유 (공통코드 HLD_RSN) — rjctQty > 0이면 필수 */
        private String rjctRsnCd;
        /** 불량사유 상세 — ETC일 때만 */
        private String rjctRsnDscr;
```

- [ ] **Step 2: 실패 테스트 — `ReceivingServiceTest`**

setUp의 생성자 호출을:
```java
        receivingService = new ReceivingService(ibOrderRepository, ibLineRepository, new LotIssuer(lotRepository),
                locRepository, invHistRepository, new InvStore(invRepository, invHistRepository),
                prodRepository, inspectionService, rtngsLocResolver, invHldService);
```
필드 추가: `@Mock RtngsLocResolver rtngsLocResolver;` `@Mock InvHldService invHldService;` 그리고 setUp에
```java
        when(order.isRtngs()).thenReturn(false);
        when(order.rcvUomCd(prod)).thenReturn("BOX");
        when(ibLine.getRjctQty()).thenReturn(0L);
```
(기존 `when(prod.getInbUomCd())`는 그대로 둔다 — `toEaQty(any, any)` 스텁이 단위를 안 본다.)

헬퍼 추가:
```java
    private ReceiveRequest.Line rtngsLine(long ibLineId, Long inspectQty, Long rjctQty, String rsnCd) {
        ReceiveRequest.Line line = line(ibLineId, inspectQty != null ? inspectQty : 0);
        line.setInspectQty(inspectQty);
        line.setRjctQty(rjctQty);
        line.setRjctRsnCd(rsnCd);
        return line;
    }

    private Loc rtngsLoc;
    private Inv rtngsInv;

    private void stubRtngs() {
        when(order.isRtngs()).thenReturn(true);
        when(order.rcvUomCd(prod)).thenReturn("EA");
        rtngsLoc = mock(Loc.class);
        when(rtngsLoc.getId()).thenReturn(9L);
        when(rtngsLocResolver.resolve(prod)).thenReturn(rtngsLoc);
        rtngsInv = mock(Inv.class);
        when(invRepository.findByKeyForUpdate(1L, 9L, 7L)).thenReturn(Optional.of(rtngsInv));
    }
```

케이스(클래스 끝에):
```java
    @Test
    @DisplayName("반품: 양품은 스테이징 RECEIVE, 불량은 반품존 RECEIVE + 보류 — 이력 2건, rcvd/rjct 각각 누계")
    void rtngs_splitsGoodAndRjct() {
        stubRtngs();

        receivingService.receive(10L, request(rtngsLine(100L, 3L, 2L, "DAMG"))); // 3×24 양품, 2×24 불량

        verify(ibLine).receive(72L);
        verify(ibLine).reject(48L);
        verify(inv).increaseOnHand(72L);
        verify(rtngsInv).increaseOnHand(48L);
        verify(invHldService).holdOn(rtngsInv, 48L, "DAMG", null);
        verify(invHistRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("반품: 불량만 온 라인도 저장된다 (양품 0)")
    void rtngs_rjctOnlyLine() {
        stubRtngs();

        receivingService.receive(10L, request(rtngsLine(100L, null, 2L, "QLTY")));

        verify(ibLine, never()).receive(anyLong());
        verify(ibLine).reject(48L);
        verify(invHldService).holdOn(rtngsInv, 48L, "QLTY", null);
    }

    @Test
    @DisplayName("반품: 양품+불량 합계가 잔량(예정 − 양품누계 − 불량누계)을 넘으면 거부")
    void rtngs_rejectsOverRemaining() {
        stubRtngs();
        when(ibLine.getRcvdQty()).thenReturn(120L);
        when(ibLine.getRjctQty()).thenReturn(96L);   // 잔량 24 = 1개

        assertThrows(IllegalArgumentException.class,
                () -> receivingService.receive(10L, request(rtngsLine(100L, 1L, 1L, "DAMG"))));
        verify(ibLine, never()).receive(anyLong());
    }

    @Test
    @DisplayName("반품: 불량수량이 있으면 사유가 필수")
    void rtngs_requiresRjctRsn() {
        stubRtngs();

        assertThrows(IllegalArgumentException.class,
                () -> receivingService.receive(10L, request(rtngsLine(100L, 1L, 1L, null))));
    }

    @Test
    @DisplayName("정상 입고에 불량수량이 오면 거부 — 정상 검수는 불합격 수량을 두지 않는다")
    void normal_rejectsRjctQty() {
        assertThrows(IllegalArgumentException.class,
                () -> receivingService.receive(10L, request(rtngsLine(100L, 1L, 1L, "DAMG"))));
        verify(invHldService, never()).holdOn(any(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("반품: 보류는 모든 라인의 재고 처리가 끝난 뒤에 건다 (채번은 재고 락을 전부 잡은 뒤)")
    void rtngs_holdsAfterAllLines() {
        stubRtngs();
        IbLine second = mock(IbLine.class);
        when(second.getId()).thenReturn(101L);
        when(second.getIbOrder()).thenReturn(order);
        when(second.getProd()).thenReturn(prod);
        when(second.getExpctQty()).thenReturn(240L);
        when(second.getRcvdQty()).thenReturn(0L);
        when(second.getRjctQty()).thenReturn(0L);
        when(ibLineRepository.findById(101L)).thenReturn(Optional.of(second));

        receivingService.receive(10L, request(rtngsLine(100L, 0L, 1L, "DAMG"), rtngsLine(101L, 0L, 1L, "DAMG")));

        InOrder inOrder = inOrder(rtngsInv, invHldService);
        inOrder.verify(rtngsInv, times(2)).increaseOnHand(24L);
        inOrder.verify(invHldService, times(2)).holdOn(eq(rtngsInv), eq(24L), eq("DAMG"), any());
    }
```

- [ ] **Step 3: 실패 확인**

Run: `./mvnw test -Dtest=ReceivingServiceTest -q` → 컴파일 실패

- [ ] **Step 4: `ReceivingService` 구현**

필드 추가:
```java
    private final RtngsLocResolver rtngsLocResolver;
    private final InvHldService invHldService;
```
`receive()`의 라인 루프를:
```java
        // 불량분 보류는 라인 루프 뒤로 미룬다 — 보류 채번(HLD_NO)이 nbr_seq 락을 잡는데,
        // 라인 사이에서 잡으면 뒤 라인의 재고 락이 채번 락 뒤에 와 락 순서(채번은 재고 락을 전부 잡은 뒤)를 어긴다
        List<PendingHold> holds = new ArrayList<>();
        for (ReceiveRequest.Line line : req.getLines()) {
            receiveLine(order, staging, line).ifPresent(holds::add);
        }
        for (PendingHold hold : holds) {
            invHldService.holdOn(hold.inv(), hold.qty(), hold.rsnCd(), hold.rsnDscr());
        }
```
`private record PendingHold(Inv inv, long qty, String rsnCd, String rsnDscr) {}` 를 클래스 안에.

`receiveLine`을 통째로:
```java
    private Optional<PendingHold> receiveLine(IbOrder order, Loc staging, ReceiveRequest.Line line) {
        IbLine ibLine = findLine(order, line.getIbLineId());
        Prod prod = ibLine.getProd();
        long inspectUomQty = line.getInspectQty() != null ? line.getInspectQty() : 0;
        long rjctUomQty = line.getRjctQty() != null ? line.getRjctQty() : 0;
        if (inspectUomQty < 0 || rjctUomQty < 0) {
            throw new IllegalArgumentException("수량은 0 이상이어야 합니다: " + prod.getProdCd());
        }
        if (inspectUomQty + rjctUomQty < 1) {
            throw new IllegalArgumentException("검수수량 또는 불량수량이 1 이상이어야 합니다: " + prod.getProdCd());
        }
        if (rjctUomQty > 0 && !order.isRtngs()) {
            throw new IllegalArgumentException("정상 입고에는 불량수량을 입력할 수 없습니다: " + prod.getProdCd());
        }
        if (rjctUomQty > 0 && (line.getRjctRsnCd() == null || line.getRjctRsnCd().isBlank())) {
            throw new IllegalArgumentException("불량사유를 선택해야 합니다: " + prod.getProdCd());
        }

        // 검수는 입고단위(정상 발주단위 · 반품 출고단위) 개수로 세고, 저장은 재고 저장 단위인 낱개(EA)로 환산한다.
        String uomCd = order.rcvUomCd(prod);
        long inspect = prod.toEaQty(inspectUomQty, uomCd);
        long rjct = prod.toEaQty(rjctUomQty, uomCd);

        // 과입고 차단: 양품·불량 누계를 뺀 잔량 이내 (프론트도 같은 검증을 하지만 서버가 최종 방어선)
        long remaining = ibLine.getExpctQty() - ibLine.getRcvdQty() - ibLine.getRjctQty();
        if (inspect + rjct > remaining) {
            throw new IllegalArgumentException("검수수량이 예정 잔량을 초과합니다: " + prod.getProdCd()
                    + " (잔량 " + remaining + ", 검수 환산 " + (inspect + rjct) + ")");
        }

        LocalDate receiptDt = line.getReceiptDt() != null ? line.getReceiptDt() : LocalDate.now();
        Lot lot = lotIssuer.findOrCreate(prod, receiptDt, validateMfgDt(prod, line.getMfgDt(), receiptDt));
        InvDocRef ref = InvDocRef.ofIbLine(RefDocTyp.INBOUND, order.getIbNo(), ibLine.getId());

        if (inspect > 0) {
            ibLine.receive(inspect);
            invStore.increase(prod, staging, lot, inspect, TxTyp.RECEIVE, ref);
        }
        if (rjct == 0) {
            return Optional.empty();
        }
        // 불량은 스테이징을 거치지 않는다 — 보류된 재고는 적치지시를 걸 수 없어 거기 갇힌다. 반품존에 바로 받는다
        ibLine.reject(rjct);
        Inv rtngsInv = invStore.increase(prod, rtngsLocResolver.resolve(prod), lot, rjct, TxTyp.RECEIVE, ref);
        return Optional.of(new PendingHold(rtngsInv, rjct, line.getRjctRsnCd(), line.getRjctRsnDscr()));
    }
```
import 추가: `java.util.ArrayList`, `java.util.Optional`.

`cancelReceipt` — `ibLine.cancelReceive(qty);` 앞의 가용 검사 메시지와 취소 분기를:
```java
        boolean rjct = RtngsLocResolver.inRtngsZon(receipt.getLoc());
        if (inv.avalQty() < qty) {
            throw new IllegalStateException(rjct
                    ? "보류를 해제한 뒤 취소할 수 있습니다 (가용 " + inv.avalQty() + "): " + prod.getProdCd()
                    : "이미 적치됐거나 적치지시가 예약한 수량이 있어 검수를 취소할 수 없습니다 (가용 "
                            + inv.avalQty() + "): " + prod.getProdCd());
        }

        if (rjct) {
            ibLine.cancelReject(qty);
        } else {
            ibLine.cancelReceive(qty);
        }
```
(`invStore.lock(...)`의 `orElseThrow` 메시지 "스테이징 재고를 찾을 수 없습니다"는 "검수 재고를 찾을 수 없습니다"로.)

`receipts(Long ibOrderId)` · `receipts(Long, Long)`의 매핑을:
```java
        return receiveRows.stream()
                .map(r -> ReceiptResponse.from(r, cancelledIds.contains(r.getId()),
                        order.rcvUomCd(r.getProd()), RtngsLocResolver.inRtngsZon(r.getLoc())))
                .toList();
```
(라인 단위 메서드에서는 `IbOrder order = ibLine.getIbOrder();`를 먼저 잡는다.)

- [ ] **Step 5: `ReceiptResponse`**

```java
    /** 판정 — GOOD 양품(스테이징) / RJCT 불량(반품존, 보류). 로케이션이 반품존인지로 파생 */
    private final String dcsn;
```
생성자 시그니처 `private ReceiptResponse(InvHist hist, boolean cancelled, String rcvUomCd, boolean rjct)`, 본문에서
`this.inbUomCd = rcvUomCd; this.inbEaQty = hist.getProd().eaQtyOf(rcvUomCd); this.dcsn = rjct ? "RJCT" : "GOOD";`
`inbUomCd` javadoc을 `/** 검수 입력 단위 — 정상 입고단위 · 반품 출고단위. 수량을 「n BOX (m)」로 보여주는 재료 */`로.
`public static ReceiptResponse from(InvHist hist, boolean cancelled, String rcvUomCd, boolean rjct)`.

- [ ] **Step 6: 통과 확인**

Run: `./mvnw test -Dtest=ReceivingServiceTest,InspectionRuleTest -q` → PASS (기존 케이스 포함)

---

### Task 7: 조회 — 상대처 · `rjct` 반영

**Files:**
- Modify: `src/main/java/com/project/wmsback/inbound/dto/IbOrderSearchCond.java` (`odrDvsn`)
- Modify: `src/main/java/com/project/wmsback/inbound/dto/IbOrderResponse.java` · `IbOrderInspResponse.java` · `IbOrderCfmResponse.java` (`storeNm`, `odrDvsn`; Cfm에 `totalRjctQty`)
- Modify: `src/main/java/com/project/wmsback/inbound/dto/IbLineResponse.java` (`rjctQty`, 단위를 문서 구분으로)
- Modify: `src/main/java/com/project/wmsback/inbound/dto/PutawayCandidateResponse.java` (`storeNm`)
- Modify: `src/main/java/com/project/wmsback/inbound/repository/IbOrderRepositoryImpl.java`
- Modify: `src/main/java/com/project/wmsback/inbound/repository/IbLineRepositoryImpl.java`

**Interfaces:**
- Produces: 응답에 `storeNm`(반품이면 점포명, 아니면 null) · `odrDvsn`; `IbOrderCfmResponse.totalRjctQty`; `IbLineResponse.rjctQty`, `inbUomCd`/`inbEaQty`는 문서 구분에 맞는 검수 단위
- 검색 `vndrNm`은 벤더명 또는 점포명 contains(화면 라벨 「상대처」), `odrDvsn`은 정확일치

- [ ] **Step 1: DTO**

`IbOrderSearchCond`: `private String odrDvsn;` 추가 (javadoc `/** 발주구분 정확일치 — 반품(RTNGS)만 보기 */`). `vndrNm` javadoc을 `/** 상대처명 (벤더명 또는 점포명 contains) */`.

`IbOrderResponse`: 필드 `private final String storeNm; private final String odrDvsn;` 생성자 `(Long ibOrderId, String ibNo, String prgr, String vndrNm, String storeNm, String odrDvsn, LocalDate expctDe, long totalExpctQty, LocalDateTime inspDt, LocalDateTime cfmDt)`.

`IbOrderInspResponse`: 같은 두 필드, 생성자 `(Long ibOrderId, String ibNo, IbStatus status, String vndrNm, String storeNm, String odrDvsn, LocalDate expctDe, int lineCount, int cmplLineCount, LocalDateTime inspDt)`.

`IbOrderCfmResponse`: 두 필드 + `private final long totalRjctQty;` 생성자 `(Long ibOrderId, String ibNo, String prgr, IbStatus status, String vndrNm, String storeNm, String odrDvsn, LocalDate expctDe, long totalExpctQty, long totalRcvdQty, long totalRjctQty, long totalPtawyQty, LocalDateTime cfmDt)`. `totalRcvdQty` javadoc: `/** 양품 검수 합계 — 미적치 계산용 */`, `totalRjctQty`: `/** 불량 합계 — 결품 = 예정 − 양품 − 불량 */`.

`IbLineResponse`: `private final Long rjctQty;` 추가, 생성자에서
```java
        this.rjctQty = line.getRjctQty();
        // 검수 입력 단위는 문서 구분이 정한다 — 정상 입고단위, 반품 출고단위 (IbOrder#rcvUomCd)
        this.inbUomCd = line.getIbOrder().rcvUomCd(line.getProd());
        this.inbEaQty = line.getProd().eaQtyOf(this.inbUomCd);
```
`inbUomCd` javadoc을 `/** 검수 입력 단위 — 정상 입고단위 · 반품 출고단위. 화면이 검수수량 입력 칸 옆에 라벨로 붙인다 */`.

`PutawayCandidateResponse`: `private final String storeNm;` — 생성자 파라미터 `vndrNm` 바로 뒤에 `String storeNm`.

- [ ] **Step 2: `IbOrderRepositoryImpl`**

import `import static com.project.mdm.store.entity.QStore.store;`. 세 쿼리 모두:
- `.innerJoin(ibOrder.vendor, vendor)` → `.leftJoin(ibOrder.vendor, vendor).leftJoin(ibOrder.store, store)`
- select에 `vendor.vndrNm` 뒤 `store.storeNm, ibOrder.odrDvsn`
- groupBy에 `store.storeNm, ibOrder.odrDvsn` 추가
- `searchForCfm` select에 `sumOrZero(ibLine.rcvdQty)` 뒤 `sumOrZero(ibLine.rjctQty)`

`progressCode()`의 SCHEDULED 판정:
```java
                .when(sumOrZero(ibLine.rcvdQty).add(sumOrZero(ibLine.rjctQty)).eq(0L)).then(IbPrgr.SCHEDULED.name())
```
`cmplLineCount()`: `.when(ibLine.rcvdQty.add(ibLine.rjctQty).goe(ibLine.expctQty))`.

`searchConds`에 `odrDvsnEq(cond.getOdrDvsn())` 추가:
```java
    /** 상대처명 — 벤더명 또는 점포명 */
    private BooleanExpression vndrNmContains(String vndrNm) {
        return StringUtils.hasText(vndrNm)
                ? vendor.vndrNm.containsIgnoreCase(vndrNm).or(store.storeNm.containsIgnoreCase(vndrNm))
                : null;
    }

    private BooleanExpression odrDvsnEq(String odrDvsn) {
        return StringUtils.hasText(odrDvsn) ? ibOrder.odrDvsn.eq(odrDvsn) : null;
    }
```

- [ ] **Step 3: `IbLineRepositoryImpl.findAllPendingPutawayBatches`**

import store Q. `.innerJoin(ibOrder.vendor, vendor)` → `.leftJoin(ibOrder.vendor, vendor).leftJoin(ibOrder.store, store)`; select `vendor.vndrNm` 뒤 `store.storeNm`; groupBy에 `store.storeNm`; `vndrNmContains`를 위와 같이 `or(store.storeNm…)`.

- [ ] **Step 4: 컴파일 · 오프라인 HQL 검증**

Run: `./mvnw compile -q` → 성공. 기존 메모 `offline-hql-translation-probe`대로 존재하지 않는 DB + dialect 고정으로 기동해 세 목록 쿼리(`/inbound/asns`, `/inspection`, `/confirmation`, `/inbound/putaway/lines`)의 HQL→SQL 번역이 통과하는지 확인 — 실패 시 groupBy 누락이 원인이다.

- [ ] **Step 5: 회귀 테스트**

Run: `./mvnw test -Dtest=IbOrderTest,ReceivingServiceTest,PutawayServiceTest,PutawayTaskServiceTest -q` → PASS

---

### Task 8: OMS 입고주문 — 점포 · 원 출고 · 반품사유 · 환산

**Files:**
- Modify: `src/main/java/com/project/omsback/inbound/dto/OmsIbOrderSaveRequest.java` · `OmsIbLineSaveRequest.java` · `OmsIbOrderResponse.java` · `OmsIbLineResponse.java`
- Modify: `src/main/java/com/project/omsback/inbound/service/OmsIbOrderService.java`
- Modify: `src/main/java/com/project/omsback/inbound/repository/OmsIbOrderRepositoryImpl.java`
- Test: `src/test/java/com/project/omsback/inbound/service/OmsIbOrderServiceTest.java` (신규)

**Interfaces:**
- Consumes: `OmsIbOrder.builder().store/refOutbNo`, `update(8-인자)`, `odrUomCd`, `isRtngs`; `OmsIbLine.builder().rsnCd/rsnDscr`; `IbOrder.builder().store`; `CodeDetailRepository.existsById(new CodeDetailId(grpCd, cd))`; `StoreRepository`
- Produces: 요청 `storeId` · `refOutbNo`, 라인 `rsnCd` · `rsnDscr`; 응답 `storeId` · `storeCd` · `storeNm` · `refOutbNo`; 라인 응답 `rsnCd` · `rsnDscr` · `outbUomCd` · `outbEaQty` · `odrUomCd` · `odrEaQty`(구분에 맞는 단위)

- [ ] **Step 1: DTO**

`OmsIbOrderSaveRequest`에:
```java
    /** 반품 점포. 발주구분이 RTNGS면 필수, 아니면 비운다 (벤더와 둘 중 하나) */
    private Long storeId;
    /** 원 출고번호 (선택). 반품만 */
    private String refOutbNo;
```
`OmsIbLineSaveRequest`에:
```java
    /** 반품사유 (RTNGS_RSN). 반품 라인만 필수 */
    private String rsnCd;
    /** 반품사유 상세. ETC일 때만 */
    private String rsnDscr;
```
`odrQty` javadoc을 `/** 발주 수량. 정상은 입고단위, 반품은 출고단위 — 확정 시 낱개(EA)로 환산된다 */`.

`OmsIbOrderResponse`: `vendorId/vndrCd/vndrNm`을 null-safe로, `storeId/storeCd/storeNm/refOutbNo` 추가:
```java
        this.vendorId = order.getVendor() != null ? order.getVendor().getId() : null;
        this.vndrCd = order.getVendor() != null ? order.getVendor().getVndrCd() : null;
        this.vndrNm = order.getVendor() != null ? order.getVendor().getVndrNm() : null;
        this.storeId = order.getStore() != null ? order.getStore().getId() : null;
        this.storeCd = order.getStore() != null ? order.getStore().getStoreCd() : null;
        this.storeNm = order.getStore() != null ? order.getStore().getStoreNm() : null;
        this.refOutbNo = order.getRefOutbNo();
```
`totalCnvrQty`: `l.getOdrQty() * l.getProd().eaQtyOf(order.odrUomCd(l.getProd()))`.

`OmsIbLineResponse`: 필드 추가 `rsnCd` · `rsnDscr` · `outbUomCd` · `outbEaQty` · `odrUomCd` · `odrEaQty`. 생성자:
```java
        this.inbUomCd = line.getProd().getInbUomCd();
        this.inbEaQty = line.getProd().eaQtyOf(this.inbUomCd);
        this.outbUomCd = line.getProd().getOutbUomCd();
        this.outbEaQty = line.getProd().eaQtyOf(this.outbUomCd);
        // 발주 수량의 단위는 주문 구분이 정한다 — 정상 입고단위, 반품 출고단위
        this.odrUomCd = line.getOmsIbOrder().odrUomCd(line.getProd());
        this.odrEaQty = line.getProd().eaQtyOf(this.odrUomCd);
        this.cnvrQty = line.getOdrQty() * this.odrEaQty;
        this.rsnCd = line.getRsnCd();
        this.rsnDscr = line.getRsnDscr();
```

- [ ] **Step 2: 실패 테스트 — `OmsIbOrderServiceTest`**

```java
package com.project.omsback.inbound.service;

import com.project.common.batch.BatchExecutor;
import com.project.mdm.code.repository.CodeDetailRepository;
import com.project.mdm.nbr.service.NbrService;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.mdm.store.entity.Store;
import com.project.mdm.store.repository.StoreRepository;
import com.project.mdm.vendor.entity.Vendor;
import com.project.mdm.vendor.repository.VendorRepository;
import com.project.omsback.inbound.dto.OmsIbLineSaveRequest;
import com.project.omsback.inbound.dto.OmsIbOrderSaveRequest;
import com.project.omsback.inbound.entity.OmsIbOrder;
import com.project.omsback.inbound.repository.OmsIbLineRepository;
import com.project.omsback.inbound.repository.OmsIbOrderRepository;
import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.repository.IbOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 반품입고 주문 — 상대가 점포이고, 라인엔 사유가 붙고, 확정 환산은 출고단위다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OmsIbOrderServiceTest {

    @Mock OmsIbOrderRepository omsIbOrderRepository;
    @Mock OmsIbLineRepository omsIbLineRepository;
    @Mock ProdRepository prodRepository;
    @Mock VendorRepository vendorRepository;
    @Mock StoreRepository storeRepository;
    @Mock CodeDetailRepository codeDetailRepository;
    @Mock IbOrderRepository ibOrderRepository;
    @Mock NbrService nbrService;
    @Mock BatchExecutor batchExecutor;

    private OmsIbOrderService service;
    private Prod prod;

    @BeforeEach
    void setUp() {
        service = new OmsIbOrderService(omsIbOrderRepository, omsIbLineRepository, prodRepository, vendorRepository,
                storeRepository, codeDetailRepository, ibOrderRepository, nbrService, batchExecutor);
        prod = mock(Prod.class);
        when(prod.getInbUomCd()).thenReturn("BOX");
        when(prod.getOutbUomCd()).thenReturn("EA");
        when(prod.toEaQty(anyLong(), eq("BOX"))).thenAnswer(a -> a.getArgument(0, Long.class) * 24);
        when(prod.toEaQty(anyLong(), eq("EA"))).thenAnswer(a -> a.getArgument(0, Long.class));
        when(prodRepository.findById(1L)).thenReturn(Optional.of(prod));
        when(storeRepository.findById(5L)).thenReturn(Optional.of(mock(Store.class)));
        when(vendorRepository.findById(3L)).thenReturn(Optional.of(mock(Vendor.class)));
        when(codeDetailRepository.existsById(any())).thenReturn(true);
        when(nbrService.issue(any(), any())).thenReturn("NO-1");
    }

    private OmsIbOrderSaveRequest rtngsReq(String rsnCd, String rsnDscr) {
        OmsIbOrderSaveRequest req = new OmsIbOrderSaveRequest();
        req.setOdrDvsn("RTNGS");
        req.setStoreId(5L);
        req.setRefOutbNo("OB-20260820-001");
        req.setExpctDe(LocalDate.of(2026, 8, 26));
        OmsIbLineSaveRequest line = new OmsIbLineSaveRequest();
        line.setProdId(1L);
        line.setOdrQty(10L);
        line.setRsnCd(rsnCd);
        line.setRsnDscr(rsnDscr);
        req.setLines(List.of(line));
        return req;
    }

    @Test
    @DisplayName("반품 등록 — 점포·원 출고번호·라인 사유가 저장된다")
    void createRtngs() {
        service.create(rtngsReq("DAMG", null));

        ArgumentCaptor<OmsIbOrder> captor = ArgumentCaptor.forClass(OmsIbOrder.class);
        verify(omsIbOrderRepository).save(captor.capture());
        OmsIbOrder saved = captor.getValue();
        assertTrue(saved.isRtngs());
        assertNotNull(saved.getStore());
        assertNull(saved.getVendor());
        assertEquals("OB-20260820-001", saved.getRefOutbNo());
        assertEquals("DAMG", saved.getLines().get(0).getRsnCd());
    }

    @Test
    @DisplayName("반품 라인은 사유 필수, ETC면 상세 필수")
    void rtngsLineRequiresRsn() {
        assertThrows(IllegalArgumentException.class, () -> service.create(rtngsReq(null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.create(rtngsReq("ETC", " ")));
    }

    @Test
    @DisplayName("반품인데 점포가 없으면 거부, 정상인데 벤더가 없으면 거부")
    void partnerRequired() {
        OmsIbOrderSaveRequest noStore = rtngsReq("DAMG", null);
        noStore.setStoreId(null);
        assertThrows(IllegalArgumentException.class, () -> service.create(noStore));

        OmsIbOrderSaveRequest normal = rtngsReq(null, null);
        normal.setOdrDvsn("NRML");
        normal.setStoreId(null);
        assertThrows(IllegalArgumentException.class, () -> service.create(normal));
    }

    @Test
    @DisplayName("정상 라인에 반품사유가 오면 거부")
    void normalLineRejectsRsn() {
        OmsIbOrderSaveRequest normal = rtngsReq("DAMG", null);
        normal.setOdrDvsn("NRML");
        normal.setStoreId(null);
        normal.setVendorId(3L);
        assertThrows(IllegalArgumentException.class, () -> service.create(normal));
    }

    @Test
    @DisplayName("반품 확정 — ASN의 상대는 점포, 구분 RTNGS, 예정수량은 출고단위 환산")
    void confirmRtngsBuildsStoreAsn() {
        OmsIbOrder order = OmsIbOrder.builder().omsIbNo("PO-1").store(mock(Store.class)).odrDvsn("RTNGS")
                .expctDe(LocalDate.of(2026, 8, 26)).build();
        order.addLines(List.of(com.project.omsback.inbound.entity.OmsIbLine.builder().prod(prod).odrQty(10L).rsnCd("DAMG").build()));
        when(omsIbOrderRepository.findById(7L)).thenReturn(Optional.of(order));

        service.confirm(7L);

        ArgumentCaptor<IbOrder> captor = ArgumentCaptor.forClass(IbOrder.class);
        verify(ibOrderRepository).save(captor.capture());
        IbOrder asn = captor.getValue();
        assertTrue(asn.isRtngs());
        assertNotNull(asn.getStore());
        assertEquals(10L, asn.getLines().get(0).getExpctQty());   // EA ×1 — 출고단위 환산
    }
}
```

- [ ] **Step 3: 실패 확인**

Run: `./mvnw test -Dtest=OmsIbOrderServiceTest -q` → 컴파일 실패

- [ ] **Step 4: `OmsIbOrderService` 구현**

필드 추가(생성자 순서는 테스트와 같게 — `@RequiredArgsConstructor`는 선언 순):
```java
    private final ProdRepository prodRepository;
    private final VendorRepository vendorRepository;
    private final StoreRepository storeRepository;
    /** 반품사유 존재 검증. RsnValidator는 wmsback.inventory라 omsback에서 쓰지 않는다 (의존 방향) */
    private final CodeDetailRepository codeDetailRepository;
```
import: `com.project.mdm.store.entity.Store`, `com.project.mdm.store.repository.StoreRepository`, `com.project.mdm.code.entity.CodeDetailId`, `com.project.mdm.code.repository.CodeDetailRepository`.

`create`:
```java
        validate(req);
        String odrDvsn = odrDvsnOf(req);
        boolean rtngs = OmsIbOrder.RTNGS.equals(odrDvsn);
        String omsIbNo = nbrService.issue("OMS_IB_NO", req.getExpctDe());

        OmsIbOrder order = OmsIbOrder.builder()
                .omsIbNo(omsIbNo)
                .vendor(rtngs ? null : findVendor(req.getVendorId()))
                .store(rtngs ? findStore(req.getStoreId()) : null)
                .refOutbNo(blankToNull(req.getRefOutbNo()))
                .expctDe(req.getExpctDe())
                .odrDvsn(odrDvsn)
                .picNm(req.getPicNm())
                .rmk(req.getRmk())
                .build();
        order.addLines(toLines(req, rtngs));
```
`update`:
```java
        validate(req);
        String odrDvsn = odrDvsnOf(req);
        boolean rtngs = OmsIbOrder.RTNGS.equals(odrDvsn);
        OmsIbOrder order = …;
        order.update(rtngs ? null : findVendor(req.getVendorId()),
                rtngs ? findStore(req.getStoreId()) : null,
                blankToNull(req.getRefOutbNo()),
                req.getExpctDe(), odrDvsn, req.getPicNm(), req.getRmk(), toLines(req, rtngs));
```
헬퍼:
```java
    private Store findStore(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 점포입니다: " + storeId));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private List<OmsIbLine> toLines(OmsIbOrderSaveRequest req, boolean rtngs) {
        List<OmsIbLine> lines = new ArrayList<>();
        for (OmsIbLineSaveRequest line : req.getLines()) {
            Prod prod = prodRepository.findById(line.getProdId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다: " + line.getProdId()));
            lines.add(OmsIbLine.builder()
                    .prod(prod)
                    .odrQty(line.getOdrQty())
                    .rsnCd(rtngs ? line.getRsnCd() : null)
                    .rsnDscr(rtngs ? rsnDscrOf(line) : null)
                    .build());
        }
        return lines;
    }

    /** ETC일 때만 상세를 남긴다 — HLD_RSN과 같은 규칙 */
    private static String rsnDscrOf(OmsIbLineSaveRequest line) {
        return "ETC".equals(line.getRsnCd()) ? line.getRsnDscr().trim() : null;
    }
```
`confirm`의 ASN 생성:
```java
        IbOrder asn = IbOrder.builder()
                .ibNo(ibNo)
                .omsIbOrderId(order.getId())
                .vendor(order.getVendor())
                .store(order.getStore())
                .expctDe(order.getExpctDe())
                .odrDvsn(order.getOdrDvsn())
                .build();
        for (OmsIbLine line : order.getLines()) {
            // 발주 수량은 정상이면 입고단위, 반품이면 출고단위. ASN부터 창고의 모든 수량은 낱개(EA)다.
            // 단위가 갈리는 경계가 여기라서 환산도 여기서 한 번만 한다.
            Prod prod = line.getProd();
            asn.addLine(IbLine.builder()
                    .prod(prod)
                    .expctQty(prod.toEaQty(line.getOdrQty(), order.odrUomCd(prod)))
                    .build());
        }
```
`validate`:
```java
    private void validate(OmsIbOrderSaveRequest req) {
        boolean rtngs = OmsIbOrder.RTNGS.equals(odrDvsnOf(req));
        if (rtngs && req.getStoreId() == null) {
            throw new IllegalArgumentException("반품입고는 점포가 필수입니다.");
        }
        if (!rtngs && req.getVendorId() == null) {
            throw new IllegalArgumentException("벤더는 필수입니다.");
        }
        if (req.getExpctDe() == null) {
            throw new IllegalArgumentException("입고 예정일은 필수입니다.");
        }
        if (req.getLines() == null || req.getLines().isEmpty()) {
            throw new IllegalArgumentException("주문 라인은 최소 1건 필요합니다.");
        }
        for (OmsIbLineSaveRequest line : req.getLines()) {
            if (line.getProdId() == null) {
                throw new IllegalArgumentException("라인의 상품은 필수입니다.");
            }
            if (line.getOdrQty() == null || line.getOdrQty() < 1) {
                throw new IllegalArgumentException("발주 수량은 1 이상이어야 합니다.");
            }
            validateRsn(line, rtngs);
        }
    }

    /** 반품 라인은 사유 필수(그룹에 존재해야 하고 ETC면 상세 필수), 정상 라인은 사유를 받지 않는다 */
    private void validateRsn(OmsIbLineSaveRequest line, boolean rtngs) {
        boolean has = line.getRsnCd() != null && !line.getRsnCd().isBlank();
        if (!rtngs) {
            if (has) {
                throw new IllegalArgumentException("정상 발주 라인에는 반품사유를 둘 수 없습니다.");
            }
            return;
        }
        if (!has) {
            throw new IllegalArgumentException("반품사유를 선택해야 합니다.");
        }
        if (!codeDetailRepository.existsById(new CodeDetailId("RTNGS_RSN", line.getRsnCd()))) {
            throw new IllegalArgumentException("존재하지 않는 반품사유 코드입니다: " + line.getRsnCd());
        }
        if ("ETC".equals(line.getRsnCd()) && (line.getRsnDscr() == null || line.getRsnDscr().isBlank())) {
            throw new IllegalArgumentException("반품사유가 기타일 때는 사유 내용을 입력해야 합니다.");
        }
    }
```

- [ ] **Step 5: `OmsIbOrderRepositoryImpl.search`**

import `import static com.project.mdm.store.entity.QStore.store;`. `.innerJoin(omsIbOrder.vendor, vendor).fetchJoin()` → `.leftJoin(omsIbOrder.vendor, vendor).fetchJoin().leftJoin(omsIbOrder.store, store).fetchJoin()`. `vndrNmContains`:
```java
    /** 상대처명 — 벤더명 또는 점포명 */
    private BooleanExpression vndrNmContains(String vndrNm) {
        return StringUtils.hasText(vndrNm)
                ? omsIbOrder.vendor.vndrNm.containsIgnoreCase(vndrNm).or(omsIbOrder.store.storeNm.containsIgnoreCase(vndrNm))
                : null;
    }
```

- [ ] **Step 6: 통과 확인**

Run: `./mvnw test -Dtest=OmsIbOrderServiceTest,OmsIbOrderTest -q` → PASS. `./mvnw compile -q`로 `OmsIbProdRefChecker` 등 다른 호출부가 깨지지 않았는지 확인.

---

### Task 9: 점포 삭제 가드

**Files:**
- Create: `src/main/java/com/project/wmsback/inbound/service/WmsIbStoreRefChecker.java`
- Create: `src/main/java/com/project/omsback/inbound/service/OmsIbStoreRefChecker.java`

**Interfaces:**
- Consumes: `StoreRefChecker.findReference(Long)`

- [ ] **Step 1: 두 구현체**

`WmsIbStoreRefChecker`(`WmsIbVendorRefChecker`와 같은 모양, `@Order(3)`):
```java
package com.project.wmsback.inbound.service;

import com.project.mdm.store.service.StoreRefChecker;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static com.project.wmsback.inbound.entity.QIbOrder.ibOrder;

/** 반품 입고예정(ASN)이 점포를 참조 중인지 점포 마스터에 알려주는 구현체 ({@link StoreRefChecker} 참고). */
@Component
@Order(3)
@RequiredArgsConstructor
public class WmsIbStoreRefChecker implements StoreRefChecker {

    private final JPAQueryFactory queryFactory;

    @Override
    public String findReference(Long storeId) {
        boolean exists = queryFactory.selectOne()
                .from(ibOrder)
                .where(ibOrder.store.id.eq(storeId))
                .fetchFirst() != null;
        return exists ? "반품 입고예정(ASN)" : null;
    }
}
```
`OmsIbStoreRefChecker`(`@Order(4)`, `omsIbOrder.store.id.eq(storeId)`, 반환 `"반품 입고주문(OMS)"`).

- [ ] **Step 2: 컴파일**

Run: `./mvnw compile -q` → 성공

---

### Task 10: 적치 전략 대상 재도입 · 할당 후보 반품존 제외

**Files:**
- Modify: `src/main/java/com/project/wmsback/strategy/core/service/StrategyOptionService.java:36-39`
- Modify: `src/main/java/com/project/wmsback/strategy/putaway/service/PtawyStgyService.java:36-37`
- Modify: `src/main/java/com/project/wmsback/strategy/allocation/repository/AlocQueryRepository.java:62-67`

- [ ] **Step 1: RTNGS 필터 제거**

`StrategyOptionService` `odrDvsns` 분기를:
```java
            // 적치 전략 적용대상 — 반품(RTNGS)도 양품은 적치하므로 포함
            case "odrDvsns" -> codeDetailRepository.findByGrpCdOrderBySrtSeq("ODR_DVSN").stream()
                    .map(c -> new OptionResponse(c.getCodeCd(), c.getCodeNm())).toList();
```
`PtawyStgyService`: `private static final Set<String> ODR_DVSNS = Set.of("NRML", "URGT", "RTNGS");` 주석 `/** 적용대상 선택지 — 옵션 소스(StrategyOptionService odrDvsns)와 같아야 한다 */`.

- [ ] **Step 2: 할당 후보에서 반품존 제외**

`AlocQueryRepository.candidatesByProd`의 where에 `aval.gt(0L)` 뒤:
```java
                        // 반품존 재고는 후보가 아니다 — 보류를 풀자마자 반품 불량이 FEFO 최우선으로 나가면 안 된다.
                        // 양품 재판정은 「보류 해제 → 재고 이동(보관존)」 두 단계다
                        zon.bizDvsn.ne(BizDvsn.RTNGS).or(zon.bizDvsn.isNull())
```
import `com.project.wmsback.warehouse.entity.BizDvsn`.

- [ ] **Step 3: 회귀**

Run: `./mvnw test -Dtest=PutawayRecommendServiceTest,AlocPlannerTest,OutbAllocServiceTest -q` → PASS

---

### Task 11: 프론트 — 입고주문 등록 (구분 → 점포 · 원 출고 · 사유 · 단위)

**Files:**
- Create: `C:\wms-front\src\components\common\OutbOrderPickerModal.jsx`
- Modify: `C:\wms-front\src\pages\oms\InboundOrder.jsx`
- Modify: `C:\wms-front\src\api\omsIbOrderApi.js` (주석만 — payload 필드 설명)

**Interfaces:**
- Consumes: `outbOrderApi.list({ storeId, status: 'SHIPPED' })` · `outbOrderApi.lines(id)`(응답 `odrQty`는 낱개 EA), `StorePickerModal`, `ProdPickerModal uomRole`, `useCodes('RTNGS_RSN')`, `eaQtyPerOutbUomOf`
- Produces: 저장 payload `{ vendorId|null, storeId|null, refOutbNo|null, odrDvsn, expctDe, picNm, rmk, lines: [{ prodId, odrQty, rsnCd|null, rsnDscr|null }] }`

- [ ] **Step 1: `OutbOrderPickerModal.jsx`**

```jsx
import { useEffect, useState } from 'react';
import { Search, Truck, X } from 'lucide-react';

import { outbOrderApi } from '@/api/outbOrderApi';
import { num } from '@/utils/format';

/**
 * 원 출고 선택 팝업 — 반품주문의 라인 미리채움 출처. 점포의 출고확정(SHIPPED) 문서만 보여준다.
 * @param storeId  점포 id (필수 — 없으면 목록이 비어 있다)
 * @param onSelect 선택 확정 콜백. 출고 헤더 객체 하나를 넘긴다 (라인은 호출자가 outbOrderApi.lines로 받는다)
 */
export default function OutbOrderPickerModal({ open, storeId, onClose, onSelect }) {
    const [orders, setOrders] = useState([]);
    const [keyword, setKeyword] = useState('');

    useEffect(() => {
        if (!open || !storeId) return;
        let ignore = false;
        outbOrderApi.list({ storeId, status: 'SHIPPED' }).then(data => { if (!ignore) setOrders(data); });
        return () => { ignore = true; };
    }, [open, storeId]);

    if (!open) return null;
    const kw = keyword.trim().toLowerCase();
    const filtered = kw ? orders.filter(o => o.outbNo.toLowerCase().includes(kw)) : orders;

    return (
        <div className="fixed inset-0 z-50 flex items-start justify-center pt-16 bg-black/20" onClick={onClose}>
            <div className="bg-white rounded-2xl shadow-xl w-[560px] max-h-[70vh] flex flex-col" onClick={(e) => e.stopPropagation()}>
                <div className="px-5 py-3 border-b border-slate-200 flex items-center gap-2">
                    <Truck size={16} className="text-indigo-600" />
                    <span className="font-bold text-slate-800">원 출고 선택</span>
                    <span className="text-xs text-slate-400">출고확정된 문서만 · 고르면 라인이 들어옵니다</span>
                    <button onClick={onClose} className="ml-auto text-slate-400 hover:text-slate-600"><X size={16} /></button>
                </div>
                <div className="px-5 py-2 border-b border-slate-100 flex items-center gap-2">
                    <Search size={13} className="text-slate-400" />
                    <input autoFocus value={keyword} onChange={(e) => setKeyword(e.target.value)}
                           placeholder="출고번호" className="flex-1 text-sm outline-none" />
                </div>
                <div className="flex-1 overflow-y-auto divide-y divide-slate-100">
                    {filtered.length === 0 && (
                        <div className="py-10 text-center text-sm text-slate-400">출고확정된 문서가 없습니다</div>
                    )}
                    {filtered.map(o => (
                        <button key={o.outbOrderId} onClick={() => { onSelect(o); onClose(); }}
                                className="w-full px-5 py-2.5 flex items-center gap-4 text-left hover:bg-indigo-50/60">
                            <span className="w-40 font-medium text-slate-700">{o.outbNo}</span>
                            <span className="w-24 text-sm text-slate-500">{o.expctDe}</span>
                            <span className="text-sm text-slate-500">라인 {num(o.lineCount)} · 수량 {num(o.totalOrderQty)} EA</span>
                        </button>
                    ))}
                </div>
            </div>
        </div>
    );
}
```

- [ ] **Step 2: `InboundOrder.jsx` — 상태·헬퍼**

import에 `StorePickerModal`, `OutbOrderPickerModal`, `outbOrderApi`, `eaQtyPerOutbUomOf` 추가. `EMPTY_FORM`에 `storeId: '', storeCd: '', storeNm: '', refOutbNo: ''`. 상태:
```jsx
    const rtngsRsnCodes = useCodes('RTNGS_RSN');
    const [storePickerOpen, setStorePickerOpen] = useState(false);
    const [outbPickerOpen, setOutbPickerOpen] = useState(false);

    // 반품이면 상대가 점포이고 수량 단위가 출고단위다 — 화면의 갈림은 이 값 하나에서 나온다
    const isRtngs = form.odrDvsn === 'RTNGS';
    const uomCdOf = (line) => (isRtngs ? line.outbUomCd : line.inbUomCd);
    const eaPerUom = (line) => (isRtngs ? eaQtyPerOutbUomOf(line) : eaQtyPerInbUomOf(line));
    const convertedQty = (line) => (Number(line.odrQty) || 0) * eaPerUom(line);
```
(기존 `convertedQty` 정의는 이걸로 대체.)

구분 변경 핸들러 — `<select>`의 onChange를:
```jsx
    // 구분을 바꾸면 상대처·원 출고·사유를 비운다 — 벤더 발주에 점포가 남거나 반품에 벤더가 남으면 서버가 거부한다
    const changeDvsn = (odrDvsn) => setForm(prev => ({
        ...prev, odrDvsn,
        vendorId: '', vndrCd: '', vndrNm: '',
        storeId: '', storeCd: '', storeNm: '', refOutbNo: '',
        lines: prev.lines.map(l => ({ ...l, rsnCd: '', rsnDscr: '' })),
    }));
```
점포·원출고:
```jsx
    const pickStore = (s) => setForm(prev => ({
        ...prev, storeId: s.storeId, storeCd: s.storeCd, storeNm: s.storeNm, refOutbNo: '',
    }));

    // 원 출고를 고르면 그 문서의 라인이 들어온다. 출고 라인 수량은 낱개(EA)라 출고단위로 되돌려 채운다
    const pickOutbOrder = async (o) => {
        const lines = await outbOrderApi.lines(o.outbOrderId);
        setForm(prev => ({
            ...prev,
            refOutbNo: o.outbNo,
            lines: lines.map(l => ({
                prodId: l.prodId, prodCd: l.prodCd, prodNm: l.prodNm, tmpZon: l.tmpZon,
                ...prodMetaOf(l.prodId),
                odrQty: Math.max(1, Math.floor(l.odrQty / (prodMetaOf(l.prodId).outbEaQty ?? 1))),
                rsnCd: '', rsnDscr: '',
            })),
        }));
    };
```
`prodMetaOf`는 상품 마스터(단위·유통기한·`outbUomCd`·`outbEaQty`)를 알아야 한다 — 화면 진입 시 `prodApi.list()`를 한 번 받아 `prodById` 맵으로 둔다:
```jsx
    const [prodById, setProdById] = useState({});
    useEffect(() => {
        prodApi.list().then(data => setProdById(Object.fromEntries(data.map(p => [p.prodId, p]))));
    }, []);
    const prodMetaOf = (prodId) => {
        const p = prodById[prodId] ?? {};
        return { shelfLifeDays: p.shelfLifeDays, inbUomCd: p.inbUomCd, inbEaQty: p.inbEaQty, outbUomCd: p.outbUomCd, outbEaQty: p.outbEaQty };
    };
```
import `prodApi` from `@/api/prodApi`.

수정 진입 `setForm`에 `storeId: order.storeId ?? '', storeCd: order.storeCd ?? '', storeNm: order.storeNm ?? '', refOutbNo: order.refOutbNo ?? ''`, 라인 매핑 `lines.map(l => ({ ...l, odrQty: l.odrQty, rsnCd: l.rsnCd ?? '', rsnDscr: l.rsnDscr ?? '' }))`.

라인 사유 setter:
```jsx
    const setLineField = (idx, patch) => setForm(prev => ({
        ...prev, lines: prev.lines.map((l, i) => (i === idx ? { ...l, ...patch } : l)),
    }));
```

- [ ] **Step 3: `handleSave` 검증·payload**

```jsx
        if (isRtngs ? !form.storeId : !form.vendorId) {
            toast.error(isRtngs ? '반품 점포는 필수입니다.' : '벤더는 필수입니다.'); return;
        }
        …
        for (const l of form.lines) {
            if (!(Number(l.odrQty) > 0)) { toast.error(`${l.prodNm} 의 수량을 입력하세요.`); return; }
            if (isRtngs && !l.rsnCd) { toast.error(`${l.prodNm} 의 반품사유를 선택하세요.`); return; }
            if (isRtngs && l.rsnCd === 'ETC' && !l.rsnDscr?.trim()) { toast.error(`${l.prodNm} 의 사유 내용을 입력하세요.`); return; }
        }
        const payload = {
            vendorId: isRtngs ? null : Number(form.vendorId),
            storeId: isRtngs ? Number(form.storeId) : null,
            refOutbNo: isRtngs ? (form.refOutbNo || null) : null,
            expctDe: form.expctDe,
            odrDvsn: form.odrDvsn,
            picNm: form.picNm?.trim() || null,
            rmk: form.rmk?.trim() || null,
            lines: form.lines.map(l => ({
                prodId: l.prodId, odrQty: Number(l.odrQty),
                rsnCd: isRtngs ? l.rsnCd : null,
                rsnDscr: isRtngs && l.rsnCd === 'ETC' ? l.rsnDscr.trim() : null,
            })),
        };
```

- [ ] **Step 4: JSX — 헤더**

발주구분 `FormField` hint를 `"반품입고를 고르면 상대가 점포로 바뀌고 수량은 출고단위가 됩니다"`, `onChange={(e) => changeDvsn(e.target.value)}`.

벤더 `FormField`를 조건 분기로:
```jsx
                    {isRtngs ? (
                        <FormField label="반품 점포" required hint={form.storeCd ? `점포 코드 ${form.storeCd}` : '물건을 돌려보내는 점포'}>
                            <button onClick={() => setStorePickerOpen(true)} disabled={readOnly}
                                    className={inputCls + ' flex items-center justify-between gap-2 text-left hover:border-indigo-300 disabled:bg-slate-50 disabled:cursor-not-allowed'}>
                                <span className={`truncate ${form.storeNm ? 'text-slate-700' : 'text-slate-400'}`}>{form.storeNm || '점포 선택'}</span>
                                <Search size={13} className="shrink-0 text-slate-400" />
                            </button>
                        </FormField>
                    ) : (
                        … 기존 벤더 FormField 그대로 …
                    )}
```
입고 예정일 뒤에 (반품일 때만):
```jsx
                    {isRtngs && (
                        <FormField label="원 출고" hint="선택 — 고르면 그 출고의 라인이 들어옵니다 (점포 먼저)">
                            <div className="flex gap-1">
                                <button onClick={() => setOutbPickerOpen(true)} disabled={readOnly || !form.storeId}
                                        className={inputCls + ' flex items-center justify-between gap-2 text-left hover:border-indigo-300 disabled:bg-slate-50 disabled:cursor-not-allowed'}>
                                    <span className={`truncate ${form.refOutbNo ? 'text-slate-700' : 'text-slate-400'}`}>{form.refOutbNo || '출고 선택'}</span>
                                    <Search size={13} className="shrink-0 text-slate-400" />
                                </button>
                                {form.refOutbNo && !readOnly && (
                                    <button onClick={() => setForm(prev => ({ ...prev, refOutbNo: '' }))} title="참조 지우기"
                                            className="px-2 text-slate-400 hover:text-slate-600"><X size={14} /></button>
                                )}
                            </div>
                        </FormField>
                    )}
```

- [ ] **Step 5: JSX — 라인**

섹션 제목 옆 문구 `' · 수량은 발주단위 기준입니다'` → `{isRtngs ? ' · 수량은 출고단위 기준입니다' : ' · 수량은 발주단위 기준입니다'}`. 컬럼 헤더 `발주 수량` → `{isRtngs ? '반품 수량' : '발주 수량'}`, 헤더에 반품이면 `<span className="w-36 shrink-0">반품사유</span>` 추가(유통기한 열 뒤). 행에서 단위 라벨 `{line.inbUomCd}` → `{uomCdOf(line)}`, `eaQtyPerInbUomOf(line)` 두 곳 → `eaPerUom(line)`. 사유 셀(유통기한 뒤, 반품일 때만):
```jsx
                                {isRtngs && (
                                    <span className="w-36 shrink-0 flex flex-col gap-0.5">
                                        <select value={line.rsnCd ?? ''} disabled={readOnly}
                                                onChange={(e) => setLineField(idx, { rsnCd: e.target.value, rsnDscr: '' })}
                                                className="px-2 py-1 bg-white border border-slate-200 rounded-md text-sm">
                                            <option value="">사유 선택</option>
                                            {rtngsRsnCodes.selectOptions.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                                        </select>
                                        {line.rsnCd === 'ETC' && (
                                            <input value={line.rsnDscr ?? ''} disabled={readOnly} maxLength={200} placeholder="사유 내용"
                                                   onChange={(e) => setLineField(idx, { rsnDscr: e.target.value })}
                                                   className="px-2 py-1 bg-white border border-slate-200 rounded-md text-xs" />
                                        )}
                                    </span>
                                )}
                            </span>
```
합계 행에도 같은 자리에 `{isRtngs && <span className="w-36 shrink-0" />}`.
`ProdPickerModal`에 `uomRole={isRtngs ? 'outb' : 'inb'}`; `addLines`가 담는 라인에 `rsnCd: '', rsnDscr: ''` 추가.

모달 두 개를 `VendorPickerModal` 옆에:
```jsx
            <StorePickerModal open={storePickerOpen} onClose={() => setStorePickerOpen(false)} onSelect={pickStore} />
            <OutbOrderPickerModal open={outbPickerOpen} storeId={form.storeId} onClose={() => setOutbPickerOpen(false)} onSelect={pickOutbOrder} />
```
타이틀 부제: `'벤더 발주 등록 — 확정하면 입고예정(ASN)이 자동 생성됩니다'` → `isRtngs ? '점포 반품 등록 — 확정하면 입고예정(ASN)이 자동 생성됩니다' : …`.

- [ ] **Step 6: `omsIbOrderApi.js` 주석**

`create` 주석의 payload를 `{ vendorId|null, storeId|null, refOutbNo|null, odrDvsn, expctDe, picNm, rmk, lines: [{ prodId, odrQty, rsnCd|null, rsnDscr|null }] } — 반품입고(RTNGS)는 storeId·라인 rsnCd 필수, 수량은 출고단위`로.

- [ ] **Step 7: 확인**

`C:\wms-front`에서 `npm run lint` 통과. 브라우저(사용자가 dev 서버 기동)에서: 구분 「반품입고」 선택 → 점포 선택 → 원 출고 선택 → 라인이 들어오고 단위가 출고단위, 사유 선택 후 등록 → 관리 화면에 상대처가 점포명으로 보임.

---

### Task 12: 프론트 — 목록의 「상대처」 열

**Files:**
- Modify: `C:\wms-front\src\pages\oms\InboundOrderList.jsx`
- Modify: `C:\wms-front\src\pages\inbound\AsnList.jsx`
- Modify: `C:\wms-front\src\pages\inbound\PutawayOrderRegister.jsx`

- [ ] **Step 1: `InboundOrderList.jsx`**

헤더 컬럼 `{ field: 'vndrNm', headerName: '벤더', … }` →
```jsx
    {
        headerName: '상대처', flex: 1, minWidth: 110,
        headerTooltip: '정상 발주는 벤더, 반품입고는 점포',
        valueGetter: (p) => p.data.vndrNm ?? p.data.storeNm,
    },
```
`odrDvsn` 렌더러: `RTNGS`는 `bg-rose-100 text-rose-700`으로 (긴급 amber와 구분):
```jsx
            const cls = p.value === 'RTNGS' ? 'bg-rose-100 text-rose-700' : 'bg-amber-100 text-amber-700';
            return p.value === 'NRML'
                ? <span className="text-[11px] text-slate-500">{nm}</span>
                : <span className={`text-[11px] px-2 py-0.5 rounded-full font-bold ${cls}`}>{nm}</span>;
```
라인 컬럼 `odrQty` 렌더러의 단위 `{p.data.inbUomCd}` → `{p.data.odrUomCd}`, `inbEaQty` 필드 → `odrEaQty`. 라인 컬럼 끝에:
```jsx
    {
        field: 'rsnCd', headerName: '반품사유', width: 120,
        cellRenderer: (p) => p.value ? (p.context.rtngsRsnNm(p.value) + (p.data.rsnDscr ? ` — ${p.data.rsnDscr}` : '')) : <span className="text-slate-300">-</span>,
    },
```
라인 그리드에 `context={{ rtngsRsnNm: (cd) => rtngsRsnCodes.nm(cd) }}`; `const rtngsRsnCodes = useCodes('RTNGS_RSN');`. 검색 `SearchItem label="벤더"` → `"상대처"`, placeholder `'전체'` 유지. 선택 라벨 `${selected.omsIbNo} · ${selected.vndrNm}` → `${selected.vndrNm ?? selected.storeNm}`.

- [ ] **Step 2: `AsnList.jsx`**

`vndrNm` 컬럼 → 위와 같은 `상대처` valueGetter. 진행단계 컬럼 앞에 구분 컬럼:
```jsx
    {
        field: 'odrDvsn', headerName: '구분', width: 90,
        cellStyle: { display: 'flex', alignItems: 'center', justifyContent: 'center' },
        cellRenderer: (p) => p.value === 'RTNGS'
            ? <span className="text-[11px] px-2 py-0.5 rounded-full font-bold bg-rose-100 text-rose-700">반품</span>
            : null,
    },
```
검색조건에 `odrDvsn: ''` 추가, `SearchBar`에 `<SearchSelect name="odrDvsn" label="구분" options={[{ value: '', label: '전체' }, { value: 'NRML', label: '정상' }, { value: 'URGT', label: '긴급' }, { value: 'RTNGS', label: '반품입고' }]} />`. 라인 컬럼 `rcvdQty` 헤더 `'검수수량'` → `'양품'`, 그 뒤 `{ field: 'rjctQty', headerName: '불량', width: 120, cellClass: 'ag-right-aligned-cell', valueFormatter: inbQtyFmt }`. 벤더 검색 라벨 → 상대처.

- [ ] **Step 3: `PutawayOrderRegister.jsx`**

`vndrNm` 컬럼(41행) → 상대처 valueGetter, 라벨 `${selectedOrder.vndrNm}` → `${selectedOrder.vndrNm ?? selectedOrder.storeNm}`, 22행 매핑에 `storeNm: b.storeNm` 추가.

- [ ] **Step 4: 확인**

`npm run lint`. 브라우저에서 반품 ASN이 세 목록에 점포명으로 보이고 구분 필터가 동작.

---

### Task 13: 프론트 — 입고검수 불량 입력 · 이력 판정

**Files:**
- Modify: `C:\wms-front\src\pages\inbound\Receiving.jsx`
- Modify: `C:\wms-front\src\api\ibOrderApi.js` (주석)

**Interfaces:**
- Consumes: `IbOrderInspResponse.odrDvsn/storeNm`, `IbLineResponse.rjctQty/inbUomCd(문서 단위)`, `ReceiptResponse.dcsn`, `useCodes('HLD_RSN')`, `SelectCellEditor`

- [ ] **Step 1: 헤더·상태**

`HEADER_COLUMN_DEFS`의 `vndrNm` → 상대처 valueGetter + 구분 컬럼(AsnList와 같은 렌더러). 컴포넌트 안:
```jsx
    const hldRsnCodes = useCodes('HLD_RSN');
    const isRtngs = inspTarget?.odrDvsn === 'RTNGS';
```
import `useCodes`, `SelectCellEditor`.

- [ ] **Step 2: 라인 그리드 컬럼**

`잔량` valueGetter → `inInbUom(p.data.expctQty - p.data.rcvdQty - (p.data.rjctQty ?? 0), p.data)`, 툴팁 `'예정 − 양품 − 불량 (입고단위)'`. `_inspectQty` 컬럼 `cellEditorParams: { min: 0, precision: 0 }`, 헤더 `isRtngs ? '양품수량' : '검수수량'`. 그 뒤 반품일 때만 세 컬럼(`...(isRtngs ? [ … ] : [])`로 배열에 끼운다):
```jsx
        {
            field: '_rjctQty', headerName: '불량수량', width: 95, editable: canReceive, singleClickEdit: true,
            cellDataType: 'number', cellEditor: 'agNumberCellEditor', cellEditorParams: { min: 0, precision: 0 },
            valueFormatter: (p) => num(p.value), cellClass: 'ag-right-aligned-cell bg-rose-50',
            headerTooltip: '반품존에 받아 즉시 보류되는 수량 (출고단위 개수). 양품+불량이 잔량 이내',
        },
        {
            field: '_rjctRsnCd', headerName: '불량사유', width: 110, editable: canReceive, singleClickEdit: true,
            cellEditor: SelectCellEditor,
            cellEditorParams: { values: hldRsnCodes.values, labelMap: hldRsnCodes.nmByCd, placeholder: '사유' },
            cellClass: 'bg-rose-50',
            valueFormatter: (p) => hldRsnCodes.nm(p.value),
            cellRenderer: (p) => p.value ? hldRsnCodes.nm(p.value) : <span className="text-slate-300">-</span>,
        },
        {
            field: '_rjctRsnDscr', headerName: '사유 내용', width: 140, editable: (p) => canReceive && p.data._rjctRsnCd === 'ETC',
            singleClickEdit: true, cellClass: 'bg-rose-50',
            headerTooltip: '불량사유가 기타일 때만',
        },
```
`loadDetail`의 rows 매핑에 `_rjctQty: null, _rjctRsnCd: '', _rjctRsnDscr: ''` 추가. `pendingLineRows` 필터 → `r.expctQty - r.rcvdQty - (r.rjctQty ?? 0) > 0`.

반품 문서는 제조일자 하한 힌트를 부르지 않는다 — `loadDetail`에서 `const mins = isRtngsOf(asn) ? new Map() : await loadMinMfgDts(rows);` (`const isRtngsOf = (a) => a?.odrDvsn === 'RTNGS';`), `onLineCellChanged`도 같은 가드. 「제조일자 하한」 컬럼 렌더러는 `_minMfgDt === undefined`면 `…`를 그리므로 반품에선 `null`로 채워 「제한 없음」이 보이게 rows 매핑에 `_minMfgDt: isRtngsOf(asn) ? null : undefined`.

- [ ] **Step 3: 저장 검증·payload**

`handleReceiveClick`의 대상 필터 → 양품 또는 불량이 입력된 행:
```jsx
        const has = (v) => String(v ?? '').trim() !== '';
        const targets = rows.filter(r => has(r._inspectQty) || has(r._rjctQty));
```
루프 검증을:
```jsx
            const inspect = Number(r._inspectQty || 0);
            const rjct = Number(r._rjctQty || 0);
            if (inspect < 0 || rjct < 0 || !Number.isInteger(inspect) || !Number.isInteger(rjct)) {
                toast.error(`수량은 ${r.inbUomCd} 단위 0 이상 정수여야 합니다: ${r.prodCd}`); return;
            }
            if (inspect + rjct < 1) { toast.error(`양품 또는 불량 수량을 입력하세요: ${r.prodCd}`); return; }
            if (rjct > 0 && !isRtngs) { toast.error(`정상 입고에는 불량수량을 입력할 수 없습니다: ${r.prodCd}`); return; }
            if (rjct > 0 && !r._rjctRsnCd) { toast.error(`불량사유를 선택하세요: ${r.prodCd}`); return; }
            if (rjct > 0 && r._rjctRsnCd === 'ETC' && !String(r._rjctRsnDscr || '').trim()) { toast.error(`불량사유 내용을 입력하세요: ${r.prodCd}`); return; }
            const remaining = r.expctQty - r.rcvdQty - (r.rjctQty ?? 0);
            if ((inspect + rjct) * eaQtyPerInbUomOf(r) > remaining) {
                toast.error(`양품+불량이 잔량(${fmtStoredQty(remaining, r)})을 초과합니다: ${r.prodCd}`); return;
            }
```
(이후 입고일자·제조일자·하한 검사는 그대로. 하한 검사는 `!isRtngs &&` 가드.)

`doReceive` payload 라인:
```jsx
                    inspectQty: Number(r._inspectQty || 0),
                    rjctQty: isRtngs ? Number(r._rjctQty || 0) : null,
                    rjctRsnCd: isRtngs && Number(r._rjctQty || 0) > 0 ? r._rjctRsnCd : null,
                    rjctRsnDscr: isRtngs && r._rjctRsnCd === 'ETC' ? String(r._rjctRsnDscr || '').trim() : null,
```
`receiveSummary` → `targets.reduce((s, r) => s + (Number(r._inspectQty || 0) + Number(r._rjctQty || 0)) * eaQtyPerInbUomOf(r), 0)`; 확인 모달 문구에 반품이면 `불량 N 낱개는 반품존에 받아 보류됩니다` 한 줄 추가:
```jsx
                    {isRtngs && (
                        <p className="text-xs text-rose-600">
                            불량 {num(receiveConfirm.reduce((s, r) => s + Number(r._rjctQty || 0) * eaQtyPerInbUomOf(r), 0))} 낱개는 반품존에 받아 즉시 보류됩니다.
                        </p>
                    )}
```

- [ ] **Step 4: 검수 이력 그리드 판정 열**

`receiptColumnDefs`의 `검수수량` 앞에:
```jsx
        {
            field: 'dcsn', headerName: '판정', width: 80,
            cellStyle: { display: 'flex', alignItems: 'center', justifyContent: 'center' },
            cellRenderer: (p) => p.value === 'RJCT'
                ? <span className="text-[11px] font-bold px-2 py-0.5 rounded-full bg-rose-100 text-rose-700">불량</span>
                : <span className="text-[11px] font-bold px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700">양품</span>,
        },
```
취소 모달 문구: `이미 적치된 수량이 있으면 취소할 수 없습니다.` → `{cancelReceiptTarget.dcsn === 'RJCT' ? '보류를 해제한 뒤에 취소할 수 있습니다.' : '이미 적치된 수량이 있으면 취소할 수 없습니다.'}`.

부제·라벨의 `${inspTarget.vndrNm}` → `${inspTarget.vndrNm ?? inspTarget.storeNm}`; 부제에 반품이면 `양품은 RCV-STAGE, 불량은 반품존(보류)` 안내.

- [ ] **Step 5: `ibOrderApi.js` `receive` 주석**

payload에 `rjctQty · rjctRsnCd(HLD_RSN) · rjctRsnDscr — 반품입고만. 불량은 반품존에 RECEIVE 후 즉시 보류` 추가.

- [ ] **Step 6: 확인**

`npm run lint`. 브라우저: 반품 ASN 선택 → 불량 열이 붉게 보이고 정상 ASN에선 없음 → 양품 3 · 불량 2 저장 → 이력 탭에 양품/불량 2건, 재고 보류 화면에 보류 1건, 현재고에 `RTN-*-01` 행.

---

### Task 14: 프론트 — 입고확정 불량 · 결품

**Files:**
- Modify: `C:\wms-front\src\pages\inbound\InboundConfirm.jsx`

- [ ] **Step 1: 헤더 컬럼**

`vndrNm` → 상대처 valueGetter(툴팁 포함). `결품(EA)` valueGetter → `p.data.totalExpctQty - p.data.totalRcvdQty - p.data.totalRjctQty`, 툴팁 `'예정 − 양품 − 불량 합계. 확정하는 순간 이 수량이 결품으로 못박힌다'`. `미적치(EA)` 앞에:
```jsx
    {
        field: 'totalRjctQty', headerName: '불량(EA)', width: 90,
        headerTooltip: '반품존에 받아 보류된 수량 — 적치 대상이 아니다',
        cellClass: (p) => p.value > 0 ? 'ag-right-aligned-cell text-rose-600' : 'ag-right-aligned-cell text-slate-400',
        valueFormatter: (p) => num(p.value),
    },
```
`shortageOf` → `a.totalExpctQty - a.totalRcvdQty - a.totalRjctQty`.

- [ ] **Step 2: 라인 컬럼**

`rcvdQty` 헤더 `'양품'`, 뒤에 `{ field: 'rjctQty', headerName: '불량', width: 120, cellClass: 'ag-right-aligned-cell', valueFormatter: inbQtyFmt }`. `결품` valueGetter → `p.data.expctQty - p.data.rcvdQty - p.data.rjctQty`. 모달 라벨 `{a.vndrNm}` → `{a.vndrNm ?? a.storeNm}`.

- [ ] **Step 3: 확인**

`npm run lint`. 반품 ASN(양품 3 적치 완료, 불량 2)이 「적치완료」로 뜨고 결품 = 예정 − 3 − 2, 확정이 통과.

---

### Task 15: 문서

**Files:**
- Modify: `docs/design.md` (「입고주문 (OMS)」의 발주구분 문단 · 「v1에서 제외하는 것」 표 · 「검수 취소」 뒤에 「반품입고」 절)
- Modify: `docs/screen-list.html` (입고주문 두 행 · 입고예정 · 입고검수 · 입고확정 설명)
- Modify: `CLAUDE.md` (`RCV-STAGE` 문단)

- [ ] **Step 1: `docs/design.md`**

104행 문단을:
```markdown
- **발주구분(`odr_dvsn`)은 정상·긴급 사이에선 표시·분류용이지만, 반품입고(`RTNGS`)는 흐름을 가른다** — 상대처(벤더 → 점포) · 수량 단위(입고단위 → 출고단위) · 검수 판정(양품/불량)이 이 값 하나에서 갈린다(아래 「반품입고」). 긴급이 적치·피킹 순서를 바꾸지는 않는다.
```
「v1에서 제외하는 것」 표의 첫 행을:
```markdown
| ~~입고유형(정상/반품)과 반품 시 비정상 재고 상태~~ | **v1 포함으로 승격됨 (2026-08-25)** — 아래 「반품입고」 참고 |
```
「검수 취소」 절 뒤(「출고 (Outbound)」 앞)에:
```markdown
### 반품입고 (2026-08-25)

점포가 돌려보내는 물건을 받는 흐름. 반품출고(센터 → 벤더)는 미룬다 — 식품 센터는 실물을 돌려보내기보다 폐기 + 정산으로 끝내는 경우가 많고, 출고 상대가 점포뿐이라 벤더로 나가는 출고를 담을 자리부터 없다. 불량 재고가 쌓이기 시작한 뒤 「그걸 어떻게 내보낼까」로 붙인다.

**새 문서가 아니라 입고주문의 구분이다.** `ODR_DVSN`에 `RTNGS 반품입고`가 처음부터 입고주문 컬럼값으로 있었고, 헤더-라인 CRUD · 확정 · 확정취소 · 일괄확정을 한 벌 더 만들 이유가 없다. 바뀌는 것은 「상대가 점포일 수 있다」(`oms_ib_order` · `ib_order`의 `vendor_id`/`store_id` 둘 중 하나)와 「라인에 반품사유가 붙는다」(`oms_ib_line.rsn_cd`) 둘이다. 구분과 상대의 짝은 엔티티가 지킨다 — DB CHECK는 「둘 중 하나」까지만 본다(FK가 없는 것과 같은 판단).

**수량 단위는 출고단위다.** 점포는 우리가 보낸 단위로 돌려보낸다. 확정 시 환산이 `inbUomCd` / `outbUomCd`로 갈리는 자리는 지금도 하나(`OmsIbOrderService.confirm`)라 그 자리만 바뀐다.

**검수가 양품/불량을 가른다 — 불량은 스테이징을 거치지 않는다.** 보류된 재고는 가용수량(보유 − 예약 − 보류)에서 빠져 적치지시를 걸 수 없다. 스테이징에 받고 보류하면 거기 갇힌다. 그래서 불량은 반품존(`biz_dvsn = RTNGS`, 온도대별 `STORAGE` 로케이션 — `RtngsLocResolver`가 해석) 에 바로 `RECEIVE`하고 같은 트랜잭션에서 보류한다. 보류 대상이 보관 로케이션만인 것이 반품존이 `STORAGE`인 이유다. 보류는 라인 루프가 끝난 뒤 건다 — 보류 채번이 `nbr_seq` 락을 잡으므로 「채번은 재고 락을 전부 잡은 뒤」(락 순서)를 지키려면 뒤로 미뤄야 한다.

**`rcvd_qty`는 양품만이다.** 불량은 `rjct_qty`로 따로 센다. 그래서 입고확정 조건 `ptawy == rcvd`, 적치 대상 산출, `IbLine#putaway`가 전부 그대로다. 바뀌는 것은 과입고 잔량(`expct − rcvd − rjct`), 결품 식, 진행 파생(`rcvd + rjct == 0`이 「예정」)뿐이다.

**검수 규칙은 반품에서 둘이 빠진다.** 역순 제한은 대상이 아니다 — 오래된 Lot이 FEFO 앞으로 가는 것이 반품에서는 맞다. 잔여수명 하한은 양품이 0인 라인에서 빠진다 — 불량으로 받는 물건에 하한을 걸 이유가 없다. 둘 다 규칙의 `skipReason`이라 실행 로그에 사유가 남는다.

**반품존은 할당 후보가 아니다.** 보류를 풀자마자 반품 불량이 FEFO 최우선으로 나가면 안 된다. 양품 재판정은 「보류 해제 → 재고 이동(보관존)」 두 단계다.

**원 출고 참조는 느슨하고 선택이다.** `oms_ib_order.ref_outb_no`는 라인 미리채움의 출처일 뿐, 출고수량 초과 검사는 하지 않는다 — 부분 반품 누계 추적은 다음 단계다.
```

- [ ] **Step 2: `docs/screen-list.html`**

입고주문 행: `벤더 발주 <strong>등록·수정</strong> 폼.` 뒤에 `발주구분을 <strong>반품입고</strong>로 고르면 상대가 점포로 바뀌고 원 출고(선택)에서 라인을 미리 채우며, 라인에 반품사유가 붙고 수량은 출고단위가 된다(2026-08-25).` 추가. 입고주문 관리 행: `목록 검색(주문번호/벤더/상태/기간)` → `(주문번호/상대처/상태/기간)`. 입고예정 행: `목록 검색(입고번호/벤더/상태/기간)` → `(입고번호/상대처/진행단계/구분/기간)`. 입고검수 행(파일에서 찾아): 주요 기능에 `반품입고는 라인별 <strong>양품/불량</strong>을 나눠 받는다 — 양품은 RCV-STAGE, 불량은 반품존에 받아 즉시 보류(HLD_RSN 사유). 검수 이력에 판정 열.` 추가. 입고확정 행: `결품 = 예정 − 양품 − 불량` 명시.

- [ ] **Step 3: `CLAUDE.md`**

「재고 모델」의 `RCV-STAGE` 문단 끝에:
```markdown
반품 검수의 불량 도착지(반품존)는 상수가 아니라 `RtngsLocResolver`가 「상품 온도대와 같은 `BIZ_DVSN.RTNGS` 존의 STORAGE 로케이션」으로 해석한다 — 반품존 판정(`inRtngsZon`)도 거기 한 곳이다.
```
「재고 모델」 첫 불릿 뒤에 한 줄: `- \`ib_line.rcvd_qty\`는 **양품만**이고 불량은 \`rjct_qty\`다 — 입고확정 조건(\`ptawy == rcvd\`)과 적치 대상 산출이 불량을 모르는 이유. 결품은 \`expct − rcvd − rjct\`.`

- [ ] **Step 4: 전체 테스트**

Run: `./mvnw test -q` → 전체 PASS.

---

## 자체 점검 (계획 작성 후)

- 스펙 §2 상대처 → Task 1·2·7·8 / 반품사유 → 1·2·8·11 / `rjct_qty` → 1·2·6·7·13·14 / 반품존 → 1·3 / §3 검수 → 5·6·13 / §4 주변 영향 → 8(confirm)·9·10 / §5 API → 7·8 / §6 화면 → 11~14 / §7 테스트 → 각 태스크 / §8 문서 → 15.
- 시그니처 일치: `InspectionContext` 6-인자(Task 5 ↔ 테스트), `ReceivingService` 10-인자 생성자(Task 6 테스트 ↔ 구현), `OmsIbOrderService` 9-인자(Task 8), `IbOrderCfmResponse` 13-인자(Task 7 ↔ `searchForCfm` select 순서), `ReceiptResponse.from` 4-인자(Task 6).
- Task 5 Step 3의 `line.getRjctQty()`는 Task 6 Step 1 뒤에 컴파일된다 — 5와 6은 이어서 진행한다.
