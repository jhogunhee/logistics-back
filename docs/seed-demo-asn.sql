-- =====================================================================
-- 입고예정(ASN) 화면 확인용 데모 데이터
--
--   목적 — 입고예정 목록의 수량 표시와 라인 진행상태를 실제 화면에서 보기 위한 데이터다.
--   라인 수량을 「입고단위 (낱개)」로 보여주므로(utils/format.js fmtInbQty) 경우의 수마다
--   어떻게 찍히는지, 진행상태 뱃지 네 가지가 다 나오는지 눈으로 확인한다.
--
--   ▣ 상품과 벤더는 새로 만들지 않는다.
--     이미 있는 마스터에서 골라 쓴다. 데모용 상품을 따로 만들면 상품 마스터에 실물이 없는
--     행이 쌓이고, 어차피 기존 상품과 같은 것(삼다수·서울우유 …)을 이름만 바꿔 다시 넣게 된다.
--
--   ▣ 수량은 고정값이 아니라 상품의 실제 입고단위에서 계산한다.
--     상품마다 입고단위가 다르고(PLT/BOX/TRAY) 낱개수량도 다르다. 고정 숫자를 쓰면 어떤
--     상품에서는 입고단위 배수가 아니게 되는데, 그러면 화면으로는 만들 수 없는 데이터가 된다 —
--     검수 입력이 입고단위 정수만 받고(Receiving.jsx) 과입고는 막히기 때문이다.
--     그래서 「몇 단위」로 정하고 낱개수량을 곱한다. 검수는 늘 예정 이하다.
--
--   ▣ 재고 원장까지 같이 만든다.
--     검수 이력(inv_hist RECEIVE)이 있어야 목록의 「검수일시」가 뜬다. 이력만 넣으면
--     「inv_hist 합계 = inv 스냅샷」 불변식이 깨지므로 적치 MOVE 2행과 현재고까지 짝을 맞춘다.
--
--   ▣ 번호는 DEMO 표식을 단다.
--     입고번호를 IB-20260813-001 형태로 넣으면 나중에 채번이 같은 번호를 발급해 uq_ib_no에
--     걸린다. IB-DEMO-0001 / PO-DEMO-0001 / LOT-DEMO-* 처럼 채번이 만들지 않는 형태를 쓴다.
--     Lot 번호는 「상품+입고일자의 기존 Lot 건수」로 채번하므로, 데모 Lot이 남아 있는 동안
--     그 상품·그 날짜의 다음 실제 Lot 번호가 한 칸 건너뛴다(중복은 안 난다).
--
--   ▣ 몇 번을 돌려도 안전하다. 맨 앞에서 이전 데모 데이터를 지우고 다시 만든다.
--     지우기만 하려면 파일 맨 아래 「데모 데이터 삭제」 블록을 쓴다. 상품·벤더는 손대지 않고
--     이 스크립트가 만든 입고예정·Lot·재고만 걷힌다.
--
--   ▣ 전체가 DO 블록 하나다 (BEGIN;/COMMIT; 을 쓰지 않는다) — CLAUDE.md 규칙.
--
--   전제: 상품(+포장) · 벤더 · 로케이션이 이미 있어야 한다 (docs/seed-dev.sql).
--
--   실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--     - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--
--   ※ 이 파일의 첫 버전(DEMO-01~06 상품과 VD-DEMO 벤더를 새로 만들던 것)을 이미 돌린
--     DB라면, 아래 「예전 버전 잔재 정리」를 <<먼저>> 한 번 실행할 것. 그 상품들이 남아 있으면
--     상품 고르기(tmp_zon, prod_cd 순)에서 DEMO-* 가 PROD-* 보다 앞서 뽑혀 버린다.
--     첫 버전을 안 돌렸으면 아무것도 안 지우므로 그냥 돌려도 된다.
-- =====================================================================

-- ── 예전 버전 잔재 정리 (한 번만) ────────────────────────────────────
DO $demo_asn_legacy$
DECLARE
    n int;
BEGIN
    SELECT count(*) INTO n FROM prod WHERE prod_cd LIKE 'DEMO-%';
    IF n = 0 THEN
        RAISE NOTICE '예전 버전 잔재 없음 — 건너뜀';
        RETURN;
    END IF;

    -- 데모 상품에 딸린 것부터 걷는다 (재고 → Lot → 문서 → 포장 → 상품)
    DELETE FROM inv_hist WHERE prod_id IN (SELECT prod_id FROM prod WHERE prod_cd LIKE 'DEMO-%');
    DELETE FROM inv      WHERE prod_id IN (SELECT prod_id FROM prod WHERE prod_cd LIKE 'DEMO-%');
    DELETE FROM lot      WHERE prod_id IN (SELECT prod_id FROM prod WHERE prod_cd LIKE 'DEMO-%');
    DELETE FROM ib_line  WHERE prod_id IN (SELECT prod_id FROM prod WHERE prod_cd LIKE 'DEMO-%');
    DELETE FROM oms_ib_line WHERE prod_id IN (SELECT prod_id FROM prod WHERE prod_cd LIKE 'DEMO-%');
    DELETE FROM prod_uom WHERE prod_id IN (SELECT prod_id FROM prod WHERE prod_cd LIKE 'DEMO-%');
    DELETE FROM prod     WHERE prod_cd LIKE 'DEMO-%';

    -- 라인이 사라진 빈 문서와 데모 벤더
    DELETE FROM ib_order WHERE ib_no LIKE 'IB-DEMO-%';
    DELETE FROM oms_ib_order WHERE oms_ib_no LIKE 'PO-DEMO-%';
    DELETE FROM vendor WHERE vndr_cd = 'VD-DEMO';

    RAISE NOTICE '예전 버전 잔재 정리 완료 — 데모 상품 % 건과 딸린 데이터를 지웠다', n;
END
$demo_asn_legacy$;

DO $demo_asn$
DECLARE
    v_vendor bigint;
    v_stage  bigint;   -- RCV-STAGE
    v_oms    bigint;
    v_asn    bigint;
    v_d1     date := CURRENT_DATE - 1;   -- 검수일
    n        int;
    n_lines  int;
    r        record;
BEGIN
    -- 0. 이전 데모 데이터 제거 ------------------------------------------
    -- 상품·벤더는 기존 마스터라 건드리지 않는다. 이 스크립트가 만든 것만 Lot 번호와
    -- 문서 번호로 찾아 지운다.
    DELETE FROM inv_hist WHERE lot_id IN (SELECT lot_id FROM lot WHERE lot_no LIKE 'LOT-DEMO-%');
    DELETE FROM inv      WHERE lot_id IN (SELECT lot_id FROM lot WHERE lot_no LIKE 'LOT-DEMO-%');
    DELETE FROM lot      WHERE lot_no LIKE 'LOT-DEMO-%';
    DELETE FROM ib_line  WHERE ib_order_id IN (SELECT ib_order_id FROM ib_order WHERE ib_no LIKE 'IB-DEMO-%');
    DELETE FROM ib_order WHERE ib_no LIKE 'IB-DEMO-%';
    DELETE FROM oms_ib_line  WHERE oms_ib_order_id IN (SELECT oms_ib_order_id FROM oms_ib_order WHERE oms_ib_no LIKE 'PO-DEMO-%');
    DELETE FROM oms_ib_order WHERE oms_ib_no LIKE 'PO-DEMO-%';

    -- 1. 기존 마스터에서 재료 고르기 --------------------------------------
    SELECT loc_id   INTO v_stage  FROM loc WHERE loc_cd = 'RCV-STAGE';
    SELECT vendor_id INTO v_vendor FROM vendor ORDER BY vndr_cd LIMIT 1;
    IF v_stage IS NULL OR v_vendor IS NULL THEN
        RAISE EXCEPTION '로케이션(RCV-STAGE) 또는 벤더가 없다 — docs/seed-dev.sql 을 먼저 적용할 것';
    END IF;

    -- 상품 5개. 입고단위 포장이 등록된 것만 고른다 — ea_qty가 있어야 수량을 계산할 수 있다.
    -- 온도대가 섞이게 tmp_zon 순으로 돌아가며 뽑는다 (냉장/냉동 뱃지도 화면에서 보이게).
    -- 라인별 진행 정도도 여기서 한 번에 계산한다. 검수·적치 모두 「단위 수」로 정하고
    -- 마지막에 낱개수량을 곱하므로 늘 입고단위 배수다. 진행상태 뱃지 네 가지가 한 화면에
    -- 다 나오게 흩어 놓는다.
    DROP TABLE IF EXISTS demo_pick;
    CREATE TEMP TABLE demo_pick ON COMMIT DROP AS
    SELECT u.*,
           CASE u.idx
               WHEN 0 THEN u.expct_units                          -- 전량 검수, 적치 일부  → 입고확정
               WHEN 1 THEN GREATEST(1, u.expct_units * 6 / 10)    -- 부분 검수(2회 나눠 옴) → 검수중
               WHEN 2 THEN GREATEST(1, u.expct_units * 3 / 10)    -- 부분 검수, 적치 전     → 검수중
               WHEN 3 THEN u.expct_units                          -- 전량 검수·전량 적치    → 적치완료
               ELSE 0 END AS rcvd_units,                          -- 미착                   → 입고예정
           CASE u.idx
               WHEN 0 THEN GREATEST(1, u.expct_units * 7 / 10)
               WHEN 1 THEN GREATEST(1, u.expct_units * 6 / 10)
               WHEN 2 THEN 0
               WHEN 3 THEN u.expct_units
               ELSE 0 END AS ptawy_units,
           NULL::bigint AS to_loc   -- 적치 로케이션은 적치수량이 정해진 뒤에 고른다
      FROM (
        SELECT t.*,
               -- 예정수량은 「단위 수」로 정한다. 낱개수량이 큰 포장(PLT)일수록 단위 수를 줄여
               -- 로케이션 용량(시드 기준 1000)을 넘지 않게 한다
               GREATEST(2, LEAST(50, 600 / t.ea_qty)) AS expct_units
          FROM (
            SELECT p.prod_id, p.prod_cd, p.prod_nm, p.tmp_zon, p.inb_uom_cd,
                   u.ea_qty, p.shelf_life_days,
                   row_number() OVER (ORDER BY p.tmp_zon, p.prod_cd) - 1 AS idx
              FROM prod p
              JOIN prod_uom u ON u.prod_id = p.prod_id AND u.uom_cd = p.inb_uom_cd
             ORDER BY p.tmp_zon, p.prod_cd
             LIMIT 5
          ) t
      ) u;

    SELECT count(*) INTO n_lines FROM demo_pick;
    IF n_lines < 5 THEN
        RAISE EXCEPTION '입고단위 포장이 등록된 상품이 5개 미만이다 (%건) — prod_uom을 먼저 채울 것', n_lines;
    END IF;

    -- 적치할 보관 로케이션 — 상품 온도대와 같고 적치량을 담을 수 있는 곳 중 우선순위 1등
    UPDATE demo_pick d SET to_loc = (
        SELECT l.loc_id FROM loc l
         WHERE l.loc_typ = 'STORAGE' AND l.tmp_zon = d.tmp_zon
           AND (l.max_qty IS NULL OR l.max_qty >= d.ptawy_units * d.ea_qty)
         ORDER BY l.ptawy_prty, l.loc_cd LIMIT 1
    ) WHERE d.ptawy_units > 0;

    SELECT count(*) INTO n FROM demo_pick WHERE ptawy_units > 0 AND to_loc IS NULL;
    IF n > 0 THEN
        RAISE EXCEPTION '적치할 보관 로케이션을 못 찾은 상품이 % 건 있다 (온도대 또는 용량)', n;
    END IF;

    -- 2. 입고주문(발주) — ASN 은 주문 없이 존재할 수 없다 (ib_order.oms_ib_order_id NOT NULL)
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_de, odr_dvsn, pic_nm, rmk, cfm_dt)
    VALUES ('PO-DEMO-0001', 'CONFIRMED', v_vendor, CURRENT_DATE, 'NRML', '김담당', '화면 확인용 데모', v_d1 + time '08:30')
    RETURNING oms_ib_order_id INTO v_oms;

    -- 발주 수량은 입고단위 기준이다 — 확정 시 낱개로 환산되어 ASN 라인이 된다
    INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, odr_qty)
    SELECT v_oms, prod_id, expct_units FROM demo_pick;

    -- 3. 입고예정(ASN) ----------------------------------------------------
    -- 라인마다 진행 정도가 달라 헤더는 아직 「입고중」이다 (첫 검수에 들어가고,
    -- 「입고확정」은 전 라인이 전량 검수돼야 한다).
    INSERT INTO ib_order (ib_no, oms_ib_order_id, status, vendor_id, expct_de, odr_dvsn, cfm_dt)
    VALUES ('IB-DEMO-0001', v_oms, 'RECEIVING', v_vendor, CURRENT_DATE, 'NRML', NULL)
    RETURNING ib_order_id INTO v_asn;

    -- 라인 수량은 전부 낱개(EA)다. 화면이 ea_qty로 나눠 입고단위로 보여준다.
    INSERT INTO ib_line (ib_order_id, prod_id, expct_qty, rcvd_qty, ptawy_qty)
    SELECT v_asn, prod_id, expct_units * ea_qty, rcvd_units * ea_qty, ptawy_units * ea_qty
      FROM demo_pick;

    -- 4. Lot — 검수가 있었던 상품만 --------------------------------------
    INSERT INTO lot (prod_id, lot_no, receipt_dt, mfg_dt, expiry_dt)
    SELECT prod_id, 'LOT-DEMO-A', v_d1, v_d1, v_d1 + shelf_life_days
      FROM demo_pick WHERE rcvd_units > 0;

    -- 5. 검수 이력 (RECEIVE) ----------------------------------------------
    -- 목록의 「검수일시」는 컬럼이 아니라 이 행들의 created_at 최댓값이다.
    -- idx 1번 라인만 두 번에 나눠 넣는다 — 헤더 검수일시가 라인들의 최댓값이 되는지 보려는 것이다.
    INSERT INTO inv_hist (tx_typ, prod_id, loc_id, lot_id, qty, rfn_doc_typ, rfn_doc_no, ib_line_id, created_at)
    SELECT 'RECEIVE', d.prod_id, v_stage, lo.lot_id,
           CASE WHEN d.idx = 1 THEN (d.rcvd_units / 2) * d.ea_qty ELSE d.rcvd_units * d.ea_qty END,
           'INBOUND', 'IB-DEMO-0001', il.ib_line_id,
           v_d1 + time '09:00' + (d.idx * interval '37 minutes')
      FROM demo_pick d
      JOIN lot lo     ON lo.prod_id = d.prod_id AND lo.lot_no = 'LOT-DEMO-A'
      JOIN ib_line il ON il.ib_order_id = v_asn AND il.prod_id = d.prod_id
     WHERE d.rcvd_units > 0;

    -- 분할검수 2회차 (오늘 아침 — 이 건의 최종 검수일시가 된다)
    INSERT INTO inv_hist (tx_typ, prod_id, loc_id, lot_id, qty, rfn_doc_typ, rfn_doc_no, ib_line_id, created_at)
    SELECT 'RECEIVE', d.prod_id, v_stage, lo.lot_id,
           (d.rcvd_units - d.rcvd_units / 2) * d.ea_qty,
           'INBOUND', 'IB-DEMO-0001', il.ib_line_id, CURRENT_DATE + time '08:40'
      FROM demo_pick d
      JOIN lot lo     ON lo.prod_id = d.prod_id AND lo.lot_no = 'LOT-DEMO-A'
      JOIN ib_line il ON il.ib_order_id = v_asn AND il.prod_id = d.prod_id
     WHERE d.idx = 1 AND d.rcvd_units - d.rcvd_units / 2 > 0;

    -- 6. 적치 이력 (MOVE) --------------------------------------------------
    -- MOVE는 출발지(-)와 도착지(+) 두 행이다. 두 행 모두 같은 from/to를 갖게 해서
    -- 한 행만 봐도 "스테이징 → 보관"을 알 수 있다.
    WITH mv AS (
        SELECT d.prod_id, lo.lot_id, d.to_loc, d.ptawy_units * d.ea_qty AS qty,
               v_d1 + time '16:00' + (d.idx * interval '15 minutes') AS at
          FROM demo_pick d
          JOIN lot lo ON lo.prod_id = d.prod_id AND lo.lot_no = 'LOT-DEMO-A'
         WHERE d.ptawy_units > 0
    )
    INSERT INTO inv_hist (tx_typ, prod_id, loc_id, lot_id, qty, rfn_doc_typ, rfn_doc_no, from_loc_id, to_loc_id, created_at)
    -- 출발지 행 (스테이징에서 빠진다)
    SELECT 'MOVE', prod_id, v_stage, lot_id, -qty, 'INBOUND', 'IB-DEMO-0001', v_stage, to_loc, at FROM mv
    UNION ALL
    -- 도착지 행 (보관 로케이션에 들어온다)
    SELECT 'MOVE', prod_id, to_loc,  lot_id,  qty, 'INBOUND', 'IB-DEMO-0001', v_stage, to_loc, at FROM mv;

    -- 7. 현재고 스냅샷 ------------------------------------------------------
    -- 이력 합계와 같아야 한다. 스테이징 잔류분(검수 − 적치)과 보관 로케이션 적치분으로 갈린다.
    -- 수량이 0이 되는 조합은 행을 만들지 않는다 (이력 SUM=0 ↔ 행 없음).
    INSERT INTO inv (prod_id, loc_id, lot_id, on_hand_qty, aloc_qty, hld_qty)
    SELECT h.prod_id, h.loc_id, h.lot_id, SUM(h.qty), 0, 0
      FROM inv_hist h
     WHERE h.lot_id IN (SELECT lot_id FROM lot WHERE lot_no LIKE 'LOT-DEMO-%')
     GROUP BY h.prod_id, h.loc_id, h.lot_id
    HAVING SUM(h.qty) <> 0;

    -- 8. 검증 --------------------------------------------------------------
    SELECT count(*) INTO n
      FROM (SELECT h.prod_id, h.loc_id, h.lot_id, SUM(h.qty) AS s
              FROM inv_hist h
             WHERE h.lot_id IN (SELECT lot_id FROM lot WHERE lot_no LIKE 'LOT-DEMO-%')
             GROUP BY h.prod_id, h.loc_id, h.lot_id) x
      LEFT JOIN inv i ON i.prod_id = x.prod_id AND i.loc_id = x.loc_id AND i.lot_id = x.lot_id
     WHERE COALESCE(i.on_hand_qty, 0) <> x.s;
    IF n > 0 THEN
        RAISE EXCEPTION '이력 합계와 재고 스냅샷이 % 건 어긋난다', n;
    END IF;

    -- 검수는 예정 이하, 적치는 검수 이하 (ck_ib_line_qty가 뒤엣것만 막는다)
    SELECT count(*) INTO n FROM ib_line il JOIN ib_order o ON o.ib_order_id = il.ib_order_id
     WHERE o.ib_no = 'IB-DEMO-0001' AND (il.rcvd_qty > il.expct_qty OR il.ptawy_qty > il.rcvd_qty);
    IF n > 0 THEN
        RAISE EXCEPTION '화면으로 만들 수 없는 수량 조합이 % 건 있다 (과입고 또는 과적치)', n;
    END IF;

    RAISE NOTICE '데모 데이터 생성 완료 — IB-DEMO-0001 (입고중) / 라인 % 건', n_lines;
    FOR r IN SELECT * FROM demo_pick ORDER BY idx LOOP
        RAISE NOTICE '  % % · 예정 % % / 검수 % / 적치 %',
              r.prod_cd, r.prod_nm, r.expct_units, r.inb_uom_cd, r.rcvd_units, r.ptawy_units;
    END LOOP;

    DROP TABLE demo_pick;
END
$demo_asn$;

-- =====================================================================
-- 적용 후 확인
--   1) 화면에서 볼 것 — 입고예정 목록에서 IB-DEMO-0001 을 펼친다
--        · 진행상태 뱃지 네 가지가 다 나오는지 (입고예정 / 검수중 / 입고확정 / 적치완료)
--        · 수량이 전부 "n BOX (m)" 또는 "n EA" 형태인지 — 낱개로 물러난 표시가 없어야 한다
--        · 위쪽 목록 예정수량(EA)이 아래 라인 괄호 안 숫자의 합과 맞는지
--        · 헤더 검수일시가 오늘 08:40 인지 (분할검수 2회차 = 라인들 중 가장 늦은 시각)
--        · 미착 라인은 검수·적치가 빈 칸인지
--
--   2) 라인 수량과 표시 재료를 한 번에
--      SELECT p.prod_cd, p.prod_nm, p.inb_uom_cd, u.ea_qty,
--             il.expct_qty, il.rcvd_qty, il.ptawy_qty,
--             il.expct_qty / u.ea_qty AS 예정단위, il.rcvd_qty / u.ea_qty AS 검수단위
--        FROM ib_line il
--        JOIN ib_order o ON o.ib_order_id = il.ib_order_id
--        JOIN prod p     ON p.prod_id = il.prod_id
--        LEFT JOIN prod_uom u ON u.prod_id = p.prod_id AND u.uom_cd = p.inb_uom_cd
--       WHERE o.ib_no = 'IB-DEMO-0001' ORDER BY p.prod_cd;
--
--   3) 라인별 최종 검수일시 (화면의 검수일시와 같아야 한다)
--      SELECT h.ib_line_id, max(h.created_at) AS insp_dt
--        FROM inv_hist h WHERE h.tx_typ = 'RECEIVE' AND h.ib_line_id IS NOT NULL
--       GROUP BY h.ib_line_id ORDER BY h.ib_line_id;
-- =====================================================================

-- =====================================================================
-- 데모 데이터 삭제 — 상품·벤더는 그대로 두고 이 스크립트가 만든 것만 걷는다
-- =====================================================================
-- DO $demo_asn_drop$
-- BEGIN
--     DELETE FROM inv_hist WHERE lot_id IN (SELECT lot_id FROM lot WHERE lot_no LIKE 'LOT-DEMO-%');
--     DELETE FROM inv      WHERE lot_id IN (SELECT lot_id FROM lot WHERE lot_no LIKE 'LOT-DEMO-%');
--     DELETE FROM lot      WHERE lot_no LIKE 'LOT-DEMO-%';
--     DELETE FROM ib_line  WHERE ib_order_id IN (SELECT ib_order_id FROM ib_order WHERE ib_no LIKE 'IB-DEMO-%');
--     DELETE FROM ib_order WHERE ib_no LIKE 'IB-DEMO-%';
--     DELETE FROM oms_ib_line  WHERE oms_ib_order_id IN (SELECT oms_ib_order_id FROM oms_ib_order WHERE oms_ib_no LIKE 'PO-DEMO-%');
--     DELETE FROM oms_ib_order WHERE oms_ib_no LIKE 'PO-DEMO-%';
--     RAISE NOTICE '데모 데이터 삭제 완료';
-- END
-- $demo_asn_drop$;
