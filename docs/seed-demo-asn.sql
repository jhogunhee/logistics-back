-- =====================================================================
-- 입고예정(ASN) 화면 확인용 데모 데이터
--
--   목적 — 입고예정 목록의 수량 표시를 실제 화면에서 보기 위한 데이터다.
--   라인 수량을 「입고단위 (낱개)」로 보여주기 시작하면서(utils/format.js fmtInbQty)
--   경우의 수마다 어떻게 찍히는지 눈으로 확인할 것이 생겼다:
--
--     · 박스로 딱 떨어짐        → "48 BOX (288)"
--     · 입고단위가 원래 낱개    → "150 EA"      (괄호 생략)
--     · 수량 0                  → 빈 칸
--
--   박스로 안 떨어지는 값("372 EA"처럼 낱개로 물러나는 표시)은 데모에 두지 않는다.
--   수량을 전부 입고단위 배수로 맞췄기 때문이다 — 그 표시는 규칙을 어긴 데이터를 만났을 때만
--   나와야 하고, 데모가 미리 만들어 두면 정상인 모습으로 읽힌다.
--     · 헤더 합계는 낱개(EA)     → 라인 괄호 안 숫자의 합과 맞는다
--
--   ▣ 재고 원장까지 같이 만든다.
--     검수 이력(inv_hist RECEIVE)이 있어야 목록의 「검수일시」가 뜬다. 그런데 이력만
--     넣으면 「inv_hist 합계 = inv 스냅샷」 불변식이 깨지므로, 적치 MOVE 2행과
--     현재고 스냅샷까지 짝을 맞춰 넣는다. 재고 화면도 같이 정상으로 보인다.
--
--   ▣ 번호는 전부 DEMO 표식을 단다.
--     입고번호를 IB-20260813-001 형태로 넣으면 나중에 채번이 같은 번호를 발급해
--     uq_ib_no에 걸린다. IB-DEMO-0001 / PO-DEMO-0001 / DEMO-01 처럼 채번이 절대
--     만들지 않는 형태를 써서 실제 데이터와 섞이지 않게 한다.
--
--   ▣ 몇 번을 돌려도 안전하다. 맨 앞에서 이전 데모 데이터를 지우고 다시 만든다.
--     지우기만 하려면 파일 맨 아래 「데모 데이터 삭제」 블록을 쓴다.
--
--   ▣ 전체가 DO 블록 하나다 (BEGIN;/COMMIT; 을 쓰지 않는다) — CLAUDE.md 규칙.
--
--   전제: docs/seed-dev.sql 의 로케이션(RCV-STAGE, DRY-*, CHL-*)이 있어야 한다.
--
--   실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--     - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
-- =====================================================================

DO $demo_asn$
DECLARE
    v_vendor  bigint;
    v_stage   bigint;   -- RCV-STAGE
    v_oms1    bigint;
    v_oms2    bigint;
    v_asn1    bigint;
    v_asn2    bigint;
    v_d1      date := CURRENT_DATE - 1;   -- ASN1 검수 시작일
    -- ASN2는 오늘 들어와 오늘 끝난 건으로 잡는다. 목록의 기본 검색이 「오늘 ~ 7일 뒤」라
    -- 예정일을 과거로 두면 화면에 뜨지 않는다.
    v_d2      date := CURRENT_DATE;
    n         int;
BEGIN
    -- 0. 이전 데모 데이터 제거 ------------------------------------------
    -- 데모 상품(DEMO-%)은 이 스크립트만 만들므로 상품 기준으로 지우면 재고까지 정확히 걷힌다.
    DELETE FROM inv_hist WHERE prod_id IN (SELECT prod_id FROM prod WHERE prod_cd LIKE 'DEMO-%');
    DELETE FROM inv      WHERE prod_id IN (SELECT prod_id FROM prod WHERE prod_cd LIKE 'DEMO-%');
    DELETE FROM lot      WHERE prod_id IN (SELECT prod_id FROM prod WHERE prod_cd LIKE 'DEMO-%');
    DELETE FROM ib_line  WHERE ib_order_id IN (SELECT ib_order_id FROM ib_order WHERE ib_no LIKE 'IB-DEMO-%');
    DELETE FROM ib_order WHERE ib_no LIKE 'IB-DEMO-%';
    DELETE FROM oms_ib_line  WHERE oms_ib_order_id IN (SELECT oms_ib_order_id FROM oms_ib_order WHERE oms_ib_no LIKE 'PO-DEMO-%');
    DELETE FROM oms_ib_order WHERE oms_ib_no LIKE 'PO-DEMO-%';
    DELETE FROM prod_uom WHERE prod_id IN (SELECT prod_id FROM prod WHERE prod_cd LIKE 'DEMO-%');
    DELETE FROM prod     WHERE prod_cd LIKE 'DEMO-%';
    DELETE FROM vendor   WHERE vndr_cd = 'VD-DEMO';

    -- 로케이션은 시드 데이터라 여기서 만들지 않는다. 없으면 적치 이력을 만들 수 없다.
    SELECT loc_id INTO v_stage FROM loc WHERE loc_cd = 'RCV-STAGE';
    IF v_stage IS NULL THEN
        RAISE EXCEPTION 'RCV-STAGE 로케이션이 없다 — docs/seed-dev.sql 을 먼저 적용할 것';
    END IF;

    -- 1. 벤더 -----------------------------------------------------------
    INSERT INTO vendor (vndr_cd, vndr_nm, pic_nm, tel_no)
    VALUES ('VD-DEMO', '(데모) 한빛유통', '김담당', '02-1234-5678')
    RETURNING vendor_id INTO v_vendor;

    -- 2. 상품과 포장 ------------------------------------------------------
    -- 입고단위(inb_uom_cd)와 그 낱개수량(prod_uom.ea_qty)이 화면 표시의 재료다.
    -- 박스당 낱개수를 6·12·8·24·1로 흩어 놔야 표시 경우의 수가 다 나온다.
    INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('DEMO-01', '(데모) 제주 삼다수 2L',       'DRY', 'BOX', 'EA', 180),
           ('DEMO-02', '(데모) 서울우유 1L',          'CHL', 'BOX', 'EA',  14),
           ('DEMO-03', '(데모) 농심 신라면 멀티팩',   'DRY', 'BOX', 'EA', 180),
           ('DEMO-04', '(데모) 해태 홈런볼',          'DRY', 'BOX', 'EA', 270),
           ('DEMO-05', '(데모) 오뚜기 진라면 (낱개)', 'DRY', 'EA',  'EA', 180),
           ('DEMO-06', '(데모) 델몬트 오렌지주스 1.5L','DRY', 'BOX', 'EA',  90);

    -- 낱개(EA)는 어느 상품에나 있어야 환산의 바닥이 된다
    INSERT INTO prod_uom (prod_id, uom_cd, ea_qty, wgt)
    SELECT prod_id, 'EA', 1, NULL FROM prod WHERE prod_cd LIKE 'DEMO-%';

    INSERT INTO prod_uom (prod_id, uom_cd, ea_qty, wgt)
    SELECT p.prod_id, 'BOX', u.ea_qty, u.wgt
      FROM (VALUES ('DEMO-01',  6::bigint, 12.5::numeric),
                   ('DEMO-02', 12,         13.0),
                   ('DEMO-03',  8,          4.2),
                   ('DEMO-04', 24,          6.8),
                   ('DEMO-06',  6,         10.4)) AS u(prod_cd, ea_qty, wgt)
      JOIN prod p ON p.prod_cd = u.prod_cd;

    -- 3. 입고주문(발주) — ASN 은 주문 없이 존재할 수 없다 (ib_order.oms_ib_order_id NOT NULL)
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_de, odr_dvsn, pic_nm, rmk, cfm_dt)
    VALUES ('PO-DEMO-0001', 'CONFIRMED', v_vendor, CURRENT_DATE, 'NRML', '김담당', '표시 확인용 데모', v_d1 + time '08:30')
    RETURNING oms_ib_order_id INTO v_oms1;

    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_de, odr_dvsn, pic_nm, rmk, cfm_dt)
    VALUES ('PO-DEMO-0002', 'CONFIRMED', v_vendor, v_d2, 'NRML', '김담당', '오늘 들어와 끝난 건', v_d2 + time '07:00')
    RETURNING oms_ib_order_id INTO v_oms2;

    -- 발주 수량은 입고단위(BOX) 기준이다 — 확정 시 낱개로 환산되어 ASN 라인이 된다
    INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, odr_qty)
    SELECT v_oms1, p.prod_id, o.odr_qty
      FROM (VALUES ('DEMO-01', 48::bigint), ('DEMO-02', 50), ('DEMO-03', 40),
                   ('DEMO-04', 20), ('DEMO-05', 150), ('DEMO-06', 30)) AS o(prod_cd, odr_qty)
      JOIN prod p ON p.prod_cd = o.prod_cd;

    INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, odr_qty)
    SELECT v_oms2, prod_id, 20 FROM prod WHERE prod_cd = 'DEMO-01';

    -- 4. 입고예정(ASN) ----------------------------------------------------
    -- ASN1: 진행 중. 라인마다 상태가 달라 표시 경우의 수가 한 화면에 다 뜬다.
    INSERT INTO ib_order (ib_no, oms_ib_order_id, status, vendor_id, expct_de, odr_dvsn, cfm_dt)
    VALUES ('IB-DEMO-0001', v_oms1, 'RECEIVING', v_vendor, CURRENT_DATE, 'NRML', NULL)
    RETURNING ib_order_id INTO v_asn1;

    -- ASN2: 검수·적치가 끝난 건. 「확정일시」 컬럼이 채워진 모습을 보려고 둔다.
    INSERT INTO ib_order (ib_no, oms_ib_order_id, status, vendor_id, expct_de, odr_dvsn, cfm_dt)
    -- 확정일시는 전량 검수로 RECEIVED에 들어간 시각이라 검수 시각(07:30)과 같다.
    -- 그 뒤 적치(08:50)가 끝나면서 COMPLETED로 넘어갔다.
    VALUES ('IB-DEMO-0002', v_oms2, 'COMPLETED', v_vendor, v_d2, 'NRML', v_d2 + time '07:30')
    RETURNING ib_order_id INTO v_asn2;

    -- 라인 수량은 전부 낱개(EA)다. 괄호 안 숫자가 이 값이고, 화면이 ea_qty로 나눠 BOX를 만든다.
    --
    -- ▣ 화면으로 만들 수 없는 상태는 데모로도 만들지 않는다. 검수수량이 지켜야 할 것이 둘이다:
    --   1) 입고단위의 배수 — 검수 입력이 입고단위 정수만 받는다 (Receiving.jsx).
    --      배수가 아니면 입고검수의 잔량이 "-2.5 BOX" 같은 업무상 없는 숫자로 뜬다.
    --   2) 예정수량 이하 — 과입고는 화면과 서버가 함께 막는다 (Receiving.jsx의 잔량 초과 검증).
    --      검수가 예정을 넘으면 잔량이 음수가 되는데, 그 상태 자체가 만들어질 수 없다.
    --   예정수량은 발주(oms_ib_line.odr_qty × ea_qty)에서 오므로 늘 배수다.
    --
    --   DEMO-02: 두 번에 나눠 검수한 부분입고 (40박스 중 20+20). 잔량 10박스
    --   DEMO-03: 33박스만 오고 그중 30박스를 적치. 잔량 7박스 · 스테이징 잔류 3박스
    --   DEMO-04: 전량 검수(20박스) 후 15박스만 적치 — 스테이징에 5박스가 남아 있다
    --   DEMO-06: 아직 아무것도 안 옴 — 검수·적치가 빈 칸
    --
    --   적치수량도 입고단위 배수로 맞춘다. 적치 자체는 낱개 단위라 배수가 아닐 수도 있지만,
    --   데모 데이터가 굳이 그 경우를 만들 이유가 없다 — 화면에서 그 줄만 단위가 달라 보인다.
    INSERT INTO ib_line (ib_order_id, prod_id, expct_qty, rcvd_qty, ptawy_qty)
    SELECT v_asn1, p.prod_id, l.expct_qty, l.rcvd_qty, l.ptawy_qty
      FROM (VALUES ('DEMO-01', 288::bigint, 288::bigint, 216::bigint),
                   ('DEMO-02', 600, 480, 480),
                   ('DEMO-03', 320, 264, 240),
                   ('DEMO-04', 480, 480, 360),
                   ('DEMO-05', 150, 150, 150),
                   ('DEMO-06', 180,   0,   0)) AS l(prod_cd, expct_qty, rcvd_qty, ptawy_qty)
      JOIN prod p ON p.prod_cd = l.prod_cd;

    INSERT INTO ib_line (ib_order_id, prod_id, expct_qty, rcvd_qty, ptawy_qty)
    SELECT v_asn2, prod_id, 120, 120, 120 FROM prod WHERE prod_cd = 'DEMO-01';

    -- 5. Lot -------------------------------------------------------------
    -- 상품+입고일자로 구분된다. ASN1과 ASN2는 입고일이 달라 Lot이 따로 생긴다.
    INSERT INTO lot (prod_id, lot_no, receipt_dt, mfg_dt, expiry_dt)
    SELECT p.prod_id, 'LOT-DEMO-A', v_d1, v_d1, v_d1 + p.shelf_life_days
      FROM prod p WHERE p.prod_cd LIKE 'DEMO-%' AND p.prod_cd <> 'DEMO-06';  -- 안 온 상품은 Lot도 없다

    INSERT INTO lot (prod_id, lot_no, receipt_dt, mfg_dt, expiry_dt)
    SELECT p.prod_id, 'LOT-DEMO-B', v_d2, v_d2, v_d2 + p.shelf_life_days
      FROM prod p WHERE p.prod_cd = 'DEMO-01';

    -- 6. 검수 이력 (RECEIVE) ----------------------------------------------
    -- 목록의 「검수일시」는 컬럼이 아니라 이 행들의 created_at 최댓값이다.
    -- DEMO-02를 일부러 두 번에 나눠 넣는다 — 헤더 검수일시가 라인들의 최댓값(= 두 번째 시각)이
    -- 되는지 화면에서 확인하려는 것이다.
    INSERT INTO inv_hist (tx_typ, prod_id, loc_id, lot_id, qty, rfn_doc_typ, rfn_doc_no, ib_line_id, created_at)
    SELECT 'RECEIVE', p.prod_id, v_stage, lo.lot_id, e.qty, 'INBOUND', 'IB-DEMO-0001', il.ib_line_id, e.at
      FROM (VALUES ('DEMO-01', 288::bigint, (v_d1 + time '09:12')::timestamp),
                   ('DEMO-02', 240, v_d1 + time '10:05'),
                   ('DEMO-02', 240, CURRENT_DATE + time '08:40'),   -- 분할검수 2회차 = 이 건의 최종 검수일시
                   ('DEMO-03', 264, v_d1 + time '11:20'),
                   ('DEMO-04', 480, v_d1 + time '13:45'),
                   ('DEMO-05', 150, v_d1 + time '14:02')) AS e(prod_cd, qty, at)
      JOIN prod p    ON p.prod_cd = e.prod_cd
      JOIN lot  lo   ON lo.prod_id = p.prod_id AND lo.lot_no = 'LOT-DEMO-A'
      JOIN ib_line il ON il.ib_order_id = v_asn1 AND il.prod_id = p.prod_id;

    INSERT INTO inv_hist (tx_typ, prod_id, loc_id, lot_id, qty, rfn_doc_typ, rfn_doc_no, ib_line_id, created_at)
    SELECT 'RECEIVE', p.prod_id, v_stage, lo.lot_id, 120, 'INBOUND', 'IB-DEMO-0002', il.ib_line_id, v_d2 + time '07:30'
      FROM prod p
      JOIN lot  lo   ON lo.prod_id = p.prod_id AND lo.lot_no = 'LOT-DEMO-B'
      JOIN ib_line il ON il.ib_order_id = v_asn2 AND il.prod_id = p.prod_id
     WHERE p.prod_cd = 'DEMO-01';

    -- 7. 적치 이력 (MOVE) --------------------------------------------------
    -- MOVE는 출발지(-)와 도착지(+) 두 행이다. 두 행 모두 같은 from/to를 갖게 해서
    -- 한 행만 봐도 "스테이징 → 보관"을 알 수 있다.
    WITH mv AS (
    SELECT p.prod_id, lo.lot_id, dst.loc_id AS to_loc, m.qty, m.ib_no, m.at
      FROM (VALUES ('DEMO-01', 216::bigint, 'DRY-A-01-02', 'IB-DEMO-0001', (v_d1 + time '16:20')::timestamp),
                   ('DEMO-02', 480, 'CHL-A-01-02', 'IB-DEMO-0001', CURRENT_DATE + time '09:30'),
                   ('DEMO-03', 240, 'DRY-A-02-01', 'IB-DEMO-0001', v_d1 + time '16:35'),
                   ('DEMO-04', 360, 'DRY-B-01-01', 'IB-DEMO-0001', v_d1 + time '16:50'),
                   ('DEMO-05', 150, 'DRY-B-01-02', 'IB-DEMO-0001', v_d1 + time '17:05'),
                   ('DEMO-01', 120, 'DRY-C-01-01', 'IB-DEMO-0002', v_d2 + time '08:50')) AS m(prod_cd, qty, loc_cd, ib_no, at)
      JOIN prod p   ON p.prod_cd = m.prod_cd
      JOIN lot  lo  ON lo.prod_id = p.prod_id
                   AND lo.lot_no = CASE WHEN m.ib_no = 'IB-DEMO-0002' THEN 'LOT-DEMO-B' ELSE 'LOT-DEMO-A' END
      JOIN loc  dst ON dst.loc_cd = m.loc_cd
    )
    INSERT INTO inv_hist (tx_typ, prod_id, loc_id, lot_id, qty, rfn_doc_typ, rfn_doc_no, from_loc_id, to_loc_id, created_at)
    -- 출발지 행 (스테이징에서 빠진다)
    SELECT 'MOVE', prod_id, v_stage, lot_id, -qty, 'INBOUND', ib_no, v_stage, to_loc, at FROM mv
    UNION ALL
    -- 도착지 행 (보관 로케이션에 들어온다)
    SELECT 'MOVE', prod_id, to_loc,  lot_id,  qty, 'INBOUND', ib_no, v_stage, to_loc, at FROM mv;

    -- 8. 현재고 스냅샷 ------------------------------------------------------
    -- 이력 합계와 같아야 한다. 스테이징 잔류분(검수 − 적치)과 보관 로케이션 적치분 둘로 갈린다.
    -- 수량이 0이 되는 조합은 행을 만들지 않는다 (이력 SUM=0 ↔ 행 없음).
    INSERT INTO inv (prod_id, loc_id, lot_id, on_hand_qty, aloc_qty, hld_qty)
    SELECT h.prod_id, h.loc_id, h.lot_id, SUM(h.qty), 0, 0
      FROM inv_hist h
      JOIN prod p ON p.prod_id = h.prod_id AND p.prod_cd LIKE 'DEMO-%'
     GROUP BY h.prod_id, h.loc_id, h.lot_id
    HAVING SUM(h.qty) <> 0;

    -- 9. 검증 --------------------------------------------------------------
    SELECT count(*) INTO n
      FROM (SELECT h.prod_id, h.loc_id, h.lot_id, SUM(h.qty) AS s
              FROM inv_hist h JOIN prod p ON p.prod_id = h.prod_id AND p.prod_cd LIKE 'DEMO-%'
             GROUP BY h.prod_id, h.loc_id, h.lot_id) x
      LEFT JOIN inv i ON i.prod_id = x.prod_id AND i.loc_id = x.loc_id AND i.lot_id = x.lot_id
     WHERE COALESCE(i.on_hand_qty, 0) <> x.s;
    IF n > 0 THEN
        RAISE EXCEPTION '이력 합계와 재고 스냅샷이 % 건 어긋난다', n;
    END IF;

    SELECT count(*) INTO n FROM ib_line il JOIN ib_order o ON o.ib_order_id = il.ib_order_id
     WHERE o.ib_no LIKE 'IB-DEMO-%';
    RAISE NOTICE '데모 데이터 생성 완료 — 입고예정 2건 / 라인 % 건 (상품 6, 벤더 1)', n;
    RAISE NOTICE '  IB-DEMO-0001 입고중  — 표시 경우의 수 전부';
    RAISE NOTICE '  IB-DEMO-0002 적치완료 — 확정일시가 찍힌 모습';
END
$demo_asn$;

-- =====================================================================
-- 적용 후 확인
--   1) 화면에서 볼 것 — 입고예정 목록에서 벤더 "(데모) 한빛유통" 검색
--        · 위쪽 목록 예정수량(EA) 2,018 = 아래 라인 괄호 안 숫자의 합
--        · DEMO-02 검수일시가 두 번째 검수 시각(오늘 08:40)으로 뜨는지
--        · DEMO-03 검수수량 "33 BOX (264)" — 예정 40박스 중 일부만 온 부분입고
--        · 모든 수량이 "n BOX (m)" 또는 "n EA" 형태인지 — 낱개로 물러난 표시가 하나도 없어야 한다
--        · 입고검수 화면의 잔량이 전부 0 이상의 정수 박스인지
--          (소수나 음수가 보이면 데이터가 화면으로 만들 수 없는 상태라는 뜻이다)
--        · DEMO-05 "150 EA" (괄호 없음) · DEMO-06 검수·적치 빈 칸
--        · IB-DEMO-0002 에 확정일시가 찍혀 있는지
--
--   2) 라인 수량과 표시 재료를 한 번에
--      SELECT o.ib_no, p.prod_cd, p.prod_nm, p.inb_uom_cd, u.ea_qty,
--             il.expct_qty, il.rcvd_qty, il.ptawy_qty
--        FROM ib_line il
--        JOIN ib_order o ON o.ib_order_id = il.ib_order_id
--        JOIN prod p     ON p.prod_id = il.prod_id
--        LEFT JOIN prod_uom u ON u.prod_id = p.prod_id AND u.uom_cd = p.inb_uom_cd
--       WHERE o.ib_no LIKE 'IB-DEMO-%' ORDER BY o.ib_no, p.prod_cd;
--
--   3) 라인별 최종 검수일시 (화면의 검수일시와 같아야 한다)
--      SELECT h.ib_line_id, max(h.created_at) AS insp_dt
--        FROM inv_hist h WHERE h.tx_typ = 'RECEIVE' AND h.ib_line_id IS NOT NULL
--       GROUP BY h.ib_line_id ORDER BY h.ib_line_id;
-- =====================================================================

-- =====================================================================
-- 데모 데이터 삭제 — 아래 블록만 실행하면 흔적 없이 걷힌다
-- =====================================================================
-- DO $demo_asn_drop$
-- BEGIN
--     DELETE FROM inv_hist WHERE prod_id IN (SELECT prod_id FROM prod WHERE prod_cd LIKE 'DEMO-%');
--     DELETE FROM inv      WHERE prod_id IN (SELECT prod_id FROM prod WHERE prod_cd LIKE 'DEMO-%');
--     DELETE FROM lot      WHERE prod_id IN (SELECT prod_id FROM prod WHERE prod_cd LIKE 'DEMO-%');
--     DELETE FROM ib_line  WHERE ib_order_id IN (SELECT ib_order_id FROM ib_order WHERE ib_no LIKE 'IB-DEMO-%');
--     DELETE FROM ib_order WHERE ib_no LIKE 'IB-DEMO-%';
--     DELETE FROM oms_ib_line  WHERE oms_ib_order_id IN (SELECT oms_ib_order_id FROM oms_ib_order WHERE oms_ib_no LIKE 'PO-DEMO-%');
--     DELETE FROM oms_ib_order WHERE oms_ib_no LIKE 'PO-DEMO-%';
--     DELETE FROM prod_uom WHERE prod_id IN (SELECT prod_id FROM prod WHERE prod_cd LIKE 'DEMO-%');
--     DELETE FROM prod     WHERE prod_cd LIKE 'DEMO-%';
--     DELETE FROM vendor   WHERE vndr_cd = 'VD-DEMO';
--     RAISE NOTICE '데모 데이터 삭제 완료';
-- END
-- $demo_asn_drop$;
