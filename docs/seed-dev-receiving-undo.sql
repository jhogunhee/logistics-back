-- =====================================================================
-- docs/seed-dev-receiving.sql 되돌리기 — 그 시드가 만든 것만 걷어낸다.
--
--   시드가 남긴 표시 created_by = 'seed-rcv'를 따라간다. append-only 원장인 inv_hist가
--   표시의 주인이고, 재고 스냅샷·라인 누계·헤더 상태는 거기서 역산한다.
--
--   ▣ 적치지시가 이미 발행됐으면 거부한다. 예약(inv.aloc_qty)이 남은 채 재고를 줄이면
--     ck_inv_qty(예약 + 보류 ≤ 보유)에 걸린다 — 그때는 「적치지시」 화면에서 지시를 먼저 취소할 것.
--
--   ▣ 지우는 범위를 시드가 손댄 것으로 좁혀 뒀다. 빈 재고 행 삭제와 헤더 상태 되돌리기 둘 다
--     조건을 안 걸면 화면에서 만든 남의 데이터까지 쓸어간다(빈 inv 행 · 검수를 전부 취소해 둔 RECEIVING 건).
--     그래서 손댈 입고건 목록을 표시를 지우기 **전에** 붙잡아 둔다.
--
--   실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--     NOTICE는 결과 패널의 Server Output 탭에서 볼 것.
--     ※ 조각만 복사해 붙여넣지 말 것 — DO 블록은 첫 줄부터 끝까지가 한 문장이라,
--       여는 줄이 빠지면 DECLARE가 홀로 실행돼 「syntax error at or near "CONSTANT"」가 난다.
--
--   전체가 DO 블록 하나다 (BEGIN;/COMMIT;을 쓰지 않는다) — CLAUDE.md 규칙.
--   원자적이라 중간에 실패하면 아무것도 반영되지 않는다.
-- =====================================================================

DO $undo_seed_rcv$
DECLARE
    c_marker CONSTANT TEXT := 'seed-rcv';
    v_order_ids BIGINT[];
    v_blocked   INT;
    v_rows      INT;
BEGIN
    -- 손댈 입고건을 먼저 붙잡아 둔다 — 표시(inv_hist)를 지운 뒤엔 「어디까지가 시드였나」를 알 수 없다
    SELECT array_agg(DISTINCT l.ib_order_id) INTO v_order_ids
    FROM inv_hist h JOIN ib_line l ON l.ib_line_id = h.ib_line_id
    WHERE h.created_by = c_marker;

    IF v_order_ids IS NULL THEN
        RAISE NOTICE '되돌릴 것이 없다 — created_by = ''%'' 인 검수 이력이 없다.', c_marker;
        RETURN;
    END IF;

    SELECT count(*) INTO v_blocked
    FROM putaway_task t
    WHERE t.status = 'DIRECTED'
      AND t.ib_line_id IN (SELECT DISTINCT ib_line_id FROM inv_hist
                           WHERE created_by = c_marker AND ib_line_id IS NOT NULL);
    IF v_blocked > 0 THEN
        RAISE EXCEPTION '미완료 적치지시가 %건 있다 — 「적치지시」 화면에서 취소한 뒤 다시 실행할 것', v_blocked;
    END IF;

    -- 1. 재고 스냅샷에서 시드가 넣은 만큼 뺀다
    UPDATE inv i
    SET on_hand_qty = i.on_hand_qty - s.qty, updated_at = CURRENT_TIMESTAMP, updated_by = c_marker
    FROM (SELECT prod_id, loc_id, lot_id, SUM(qty) AS qty FROM inv_hist
          WHERE created_by = c_marker GROUP BY prod_id, loc_id, lot_id) s
    WHERE i.prod_id = s.prod_id AND i.loc_id = s.loc_id AND i.lot_id = s.lot_id;

    -- 수량이 모두 0이 된 행은 지운다 (Inv#isEmpty와 같은 판정 — 보유·예약·보류를 모두 본다).
    -- 시드가 건드린 키만 본다
    DELETE FROM inv i
    WHERE i.on_hand_qty = 0 AND i.aloc_qty = 0 AND i.hld_qty = 0
      AND EXISTS (SELECT 1 FROM inv_hist h
                  WHERE h.created_by = c_marker
                    AND h.prod_id = i.prod_id AND h.loc_id = i.loc_id AND h.lot_id = i.lot_id);

    -- 2. 라인 누계 되돌리기
    UPDATE ib_line l
    SET rcvd_qty = l.rcvd_qty - s.qty, updated_at = CURRENT_TIMESTAMP, updated_by = c_marker
    FROM (SELECT ib_line_id, SUM(qty) AS qty FROM inv_hist
          WHERE created_by = c_marker AND ib_line_id IS NOT NULL GROUP BY ib_line_id) s
    WHERE l.ib_line_id = s.ib_line_id;

    -- 3. 이력 삭제. 표시의 주인이라 위에서 다 쓴 뒤에 지운다
    DELETE FROM inv_hist WHERE created_by = c_marker;
    GET DIAGNOSTICS v_rows = ROW_COUNT;

    -- 4. 아무도 안 쓰는 Lot만 정리
    DELETE FROM lot
    WHERE created_by = c_marker
      AND NOT EXISTS (SELECT 1 FROM inv      WHERE inv.lot_id      = lot.lot_id)
      AND NOT EXISTS (SELECT 1 FROM inv_hist WHERE inv_hist.lot_id = lot.lot_id);

    -- 5. 헤더 되돌리기 — 시드가 손댄 입고건 중 검수 이력이 하나도 안 남은 것만
    UPDATE ib_order o
    SET status = 'SCHEDULED', updated_at = CURRENT_TIMESTAMP, updated_by = c_marker
    WHERE o.ib_order_id = ANY(v_order_ids)
      AND o.status = 'RECEIVING'
      AND NOT EXISTS (SELECT 1 FROM ib_line l
                      WHERE l.ib_order_id = o.ib_order_id AND (l.rcvd_qty > 0 OR l.rjct_qty > 0));

    RAISE NOTICE '되돌리기 완료 — 입고건 %건 / 검수 이력 %건을 걷어냈다.',
        array_length(v_order_ids, 1), v_rows;
END
$undo_seed_rcv$;
