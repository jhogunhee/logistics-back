-- =====================================================================
-- 단위 관련 주석 동기화 — 「저장 단위는 낱개(EA)」 번복 반영.
--
-- 배경: 라이브 DB의 단위 관련 주석이 EA 통일 이전 규약으로 남아 있다.
--   migration-uom.sql · migration-oms-outb.sql 이 그 문구로 적용했고, 그 뒤
--   「창고의 모든 수량 컬럼은 낱개(EA)」로 뒤집혔다(docs/design.md 「계량단위와 환산」의
--   번복 이력 참조). docs/schema.sql 은 이미 고쳤으므로 라이브 DB만 어긋난 상태다.
--
--   특히 두 문장이 사실과 반대다 —
--     - oms_outb_line       : 「확정 시 outb_line 으로 1:1 복사된다 (환산 없음)」
--     - oms_outb_line.odr_qty: 「입고주문과 달리 환산하지 않는다」
--   지금은 확정 시 prod_uom.ea_qty(출고단위)를 곱해 EA 로 환산한다
--   (OmsOutbOrderService.confirm → Prod.toEaQty). 이 주석을 읽으면 outb_line.odr_qty 가
--   출고단위라고 오해하게 되고, 그러면 「출고단위와 EA 를 둘 다 저장해야 하나」라는
--   있지도 않은 문제가 생긴다.
--
--   적용이 끝난 증분(migration-uom.sql · migration-oms-outb.sql)은 「그때 무엇을
--   적용했나」의 기록이라 고쳐 쓰지 않는다. 대신 「현재 라이브 상태 → schema.sql 상태」로
--   이 증분을 새로 쓴다 (migration-outb-wave-comment.sql 과 같은 형태).
--
-- 무엇이 바뀌나: 주석 아홉 개뿐이다. 컬럼·제약·데이터·인덱스는 건드리지 않는다.
--   대상은 단위가 걸린 자리 전부다 — 어느 증분까지 적용된 DB든 같은 값으로 수렴시킨다.
--     prod.inb_uom_cd · prod.outb_uom_cd
--     prod_uom(테이블) · prod_uom.ea_qty
--     oms_ib_line.odr_qty   (입고단위 — 환산 전)
--     oms_outb_line(테이블) · oms_outb_line.odr_qty  (출고단위 — 환산 전)
--     ib_line.expct_qty     (EA — 환산 후)
--     outb_line.odr_qty     (EA — 환산 후. 원래 '주문 수량' 뿐이라 단위가 없었다)
--
--   단위 규약 요약(이 증분이 심는 내용) —
--     입력 단위를 유지하는 컬럼은 주문 원장 둘뿐이다: oms_ib_line.odr_qty(입고단위) ·
--     oms_outb_line.odr_qty(출고단위). 그 밖 창고의 모든 수량 컬럼은 낱개(EA)이고,
--     환산은 OMS→WMS 경계 세 곳에서 곱셈으로만 일어난다.
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — COMMENT 는 덮어쓰기이고, 테이블이 없으면 건너뛴다
-- =====================================================================
DO $mig$
DECLARE
    v_done int := 0;
BEGIN
    -- ---------------------------------------------------------------
    -- 상품 마스터 — 어느 단위가 입고/출고단위인가
    -- ---------------------------------------------------------------
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name = 'prod') THEN

        COMMENT ON COLUMN prod.inb_uom_cd  IS '입고단위 (prod_uom에 있는 uom_cd여야 한다, FK 없음). 벤더에게 발주하고 납품받는 단위 (예: BOX). 이 단위를 쓰는 곳은 oms_ib_line.odr_qty 하나뿐이다. CHECK를 걸지 않는 이유는 값 목록의 주인이 공통코드 UOM 그룹이라서, CHECK가 있으면 단위를 추가할 때마다 DDL을 고쳐야 하기 때문 (zon.tmp_zon 등과 같은 형태)';
        COMMENT ON COLUMN prod.outb_uom_cd IS '출고단위 (prod_uom에 있는 uom_cd여야 한다, FK 없음). 출고주문서에 사람이 쓰는 단위 (oms_outb_line.odr_qty가 이 기준). 재고 저장 단위가 아니다 — 창고의 모든 수량 컬럼은 낱개(EA)다';

        v_done := v_done + 2;
    ELSE
        RAISE NOTICE 'prod 없음 — 건너뜀';
    END IF;

    -- ---------------------------------------------------------------
    -- 상품 포장 — 환산 계수가 사는 곳
    --   옛 문구는 「환산은 OmsIbOrderService.convert 한 곳에서만」이었다. 검수 입력과
    --   출고주문 확정이 환산 지점으로 추가되면서 셋이 됐고, 도착 단위도 출고단위가 아니라 EA다.
    -- ---------------------------------------------------------------
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name = 'prod_uom') THEN

        COMMENT ON TABLE  prod_uom        IS '상품 포장. (상품, 단위) 한 조합이 한 행 — 낱개수량과 중량을 포장마다 갖는다. 어느 단위든 낱개(EA)를 매개로 환산되는 근거가 여기다';
        COMMENT ON COLUMN prod_uom.ea_qty IS '이 단위 1개가 낱개 몇 개인가 (예: BOX 1개 = 24). 낱개 그 자체면 1. 환산(Prod.toEaQty = qty × ea_qty)은 OMS→WMS 경계 세 곳에서만 일어난다 — 발주→ASN(입고단위), 검수 입력(입고단위), 출고주문 확정(출고단위). 창고의 모든 수량 컬럼은 낱개(EA)다';

        v_done := v_done + 2;
    ELSE
        RAISE NOTICE 'prod_uom 없음 — 건너뜀';
    END IF;

    -- ---------------------------------------------------------------
    -- 주문 원장(OMS) — 입력 단위를 유지하는 유일한 두 컬럼
    -- ---------------------------------------------------------------
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name = 'oms_ib_line') THEN

        COMMENT ON COLUMN oms_ib_line.odr_qty IS '발주 수량. <<입고단위(prod.inb_uom_cd) 기준>> — 주문 원장은 사람이 쓰는 단위를 유지한다 (출고 쪽은 oms_outb_line.odr_qty가 출고단위). ASN 생성(주문확정) 시 prod_uom.ea_qty(입고단위)를 곱해 낱개(EA)로 환산된다';

        v_done := v_done + 1;
    ELSE
        RAISE NOTICE 'oms_ib_line 없음 — 건너뜀';
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name = 'oms_outb_line') THEN

        COMMENT ON TABLE  oms_outb_line         IS '출고주문 라인. 확정 시 outb_line으로 복사되며, 그때 수량만 prod_uom.ea_qty(출고단위)를 곱해 낱개(EA)로 환산된다';
        COMMENT ON COLUMN oms_outb_line.odr_qty IS '주문 수량. <<출고단위(prod.outb_uom_cd) 기준>> — 주문 원장은 사람이 쓰는 단위를 유지한다 (입고 쪽은 oms_ib_line.odr_qty가 입고단위). 확정 시 prod_uom.ea_qty(출고단위)를 곱해 낱개(EA)로 환산돼 outb_line.odr_qty가 된다';

        v_done := v_done + 2;
    ELSE
        RAISE NOTICE 'oms_outb_line 없음 — 건너뜀';
    END IF;

    -- ---------------------------------------------------------------
    -- 창고 작업문서(WMS) — 환산이 끝난 EA. 두 컬럼이 서로 대응이다
    -- ---------------------------------------------------------------
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name = 'ib_line') THEN

        COMMENT ON COLUMN ib_line.expct_qty IS '입고 예정 수량. 낱개(EA) 기준 — oms_ib_line.odr_qty(입고단위)에 prod_uom.ea_qty(입고단위)를 곱한 값이다. 창고의 모든 수량 컬럼이 같은 단위(EA)다';

        v_done := v_done + 1;
    ELSE
        RAISE NOTICE 'ib_line 없음 — 건너뜀';
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name = 'outb_line') THEN

        COMMENT ON COLUMN outb_line.odr_qty IS '주문 수량. 낱개(EA) 기준 — oms_outb_line.odr_qty(출고단위)에 prod_uom.ea_qty(출고단위)를 곱한 값이다. 창고의 모든 수량 컬럼이 같은 단위(EA)다 (입고 쪽 대응은 ib_line.expct_qty)';

        v_done := v_done + 1;
    ELSE
        RAISE NOTICE 'outb_line 없음 — 건너뜀';
    END IF;

    RAISE NOTICE '단위 주석 동기화 완료 — % 건 (schema.sql 기준으로 수렴)', v_done;
END $mig$;
