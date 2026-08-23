-- =====================================================================
-- 출고확정 — 웨이브 종료 상태 · 스테이징 예약 백필 · 주석 정정.
--
-- 현재 라이브 상태 → schema.sql 상태. 바뀌는 것 셋:
--   ① outb_wave.status 에 CLOSED 추가 (ck_outb_wave_status 교체) + clos_dt 컬럼 신설
--      웨이브에 종료 상태가 없어 ISSUED 가 「나갔다 · 작업중 · 끝났다」 셋을 겸했다. 출고확정이
--      소속 주문을 전부 닫으면 웨이브도 CLOSED 로 닫는다. 되돌리는 전이는 없다(출고확정 취소 없음).
--   ② SHIP-STAGE inv 행의 aloc_qty 백필
--      피킹이 이제 예약을 출발지에서 소진하고 도착지(SHIP-STAGE)에 다시 잡는다(from 예약 → to 예약).
--      그전에 피킹된 스테이징 재고는 aloc_qty = 0 이라, 출고확정이 거기서 예약을 풀면 ck_inv_qty 에
--      걸린다. 라이브에 출고확정이 없었으므로 스테이징 실물은 전부 미출고 피킹분이다 —
--      주문이 SHIPPED 가 아닌 outb_alloc.pikng_qty 의 (prod, lot)별 합으로 채운다(on_hand 상한).
--   ③ 주석 — inv.aloc_qty(예약 원천에 스테이징 추가) · outb_alloc.pikng_qty · outb_order.shmt_dt ·
--      outb_wave.status · inv_hist.tx_typ
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — 존재 확인을 걸어 이미 적용된 항목은 건너뛰고, 백필은 aloc_qty = 0 인 행만 건드린다
-- =====================================================================
DO $mig$
DECLARE
    v_cnt BIGINT;
BEGIN
    -- 1) 종료 시각 컬럼
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'outb_wave' AND column_name = 'clos_dt'
    ) THEN
        ALTER TABLE outb_wave ADD COLUMN clos_dt TIMESTAMP;
        RAISE NOTICE 'outb_wave.clos_dt 추가';
    ELSE
        RAISE NOTICE 'outb_wave.clos_dt 있음 — 건너뜀';
    END IF;

    -- 2) CHECK 교체 (PLANNED, ISSUED → PLANNED, ISSUED, CLOSED)
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_outb_wave_status') THEN
        ALTER TABLE outb_wave DROP CONSTRAINT ck_outb_wave_status;
    END IF;
    ALTER TABLE outb_wave ADD CONSTRAINT ck_outb_wave_status
        CHECK (status IN ('PLANNED', 'ISSUED', 'CLOSED'));
    RAISE NOTICE 'ck_outb_wave_status CHECK 재생성 (PLANNED, ISSUED, CLOSED)';

    -- 3) SHIP-STAGE 예약 백필 — 미출고 피킹분만큼. 이미 예약이 잡힌 행(aloc_qty > 0)은 건드리지 않는다.
    --    재료는 할당이 아니라 지시다 — 할당의 보관 inv 행은 전량 집품으로 지워졌을 수 있지만 지시는
    --    재고 키(prod·lot)를 발행 시점 스냅샷으로 들고 있고, 모든 피킹은 지시를 지난다(cmpl_qty = pikng_qty)
    UPDATE inv i
       SET aloc_qty = LEAST(i.on_hand_qty - i.hld_qty, s.cmpl_qty)
      FROM (SELECT t.prod_id, t.lot_id, SUM(t.cmpl_qty) AS cmpl_qty
              FROM pikng_task t
              JOIN outb_alloc a ON a.outb_alloc_id = t.outb_alloc_id
              JOIN outb_line l  ON l.outb_line_id = a.outb_line_id
              JOIN outb_order o ON o.outb_order_id = l.outb_order_id
             WHERE o.status <> 'SHIPPED' AND t.status <> 'CANCELLED' AND t.cmpl_qty > 0
             GROUP BY t.prod_id, t.lot_id) s
     WHERE i.prod_id = s.prod_id AND i.lot_id = s.lot_id
       AND i.loc_id = (SELECT loc_id FROM loc WHERE loc_cd = 'SHIP-STAGE')
       AND i.aloc_qty = 0;
    GET DIAGNOSTICS v_cnt = ROW_COUNT;
    RAISE NOTICE 'SHIP-STAGE 예약 백필 %건', v_cnt;

    -- 4) 주석
    COMMENT ON COLUMN outb_wave.status  IS 'PLANNED 편성중(주문 담기 가능) / ISSUED 지시가 나가 있다(작업중 또는 확정 대기) / CLOSED 종료(소속 주문이 전부 출고확정). 종료로 가는 길은 출고확정 하나이고 되돌아오는 길은 없다. RELEASED를 쓰지 않는 것은 inv_hld의 RELEASED(보류 해제)와 한 토큰이 두 뜻이 되기 때문';
    COMMENT ON COLUMN outb_wave.clos_dt IS '종료 시각 — 소속 주문이 전부 출고확정된 시점. 사전 「마감 CLOS」 + 일시 DT. ib_order가 clos_dt → cfm_dt로 개명한 것은 그 사건이 확정이어서이고, 웨이브의 사건은 확정이 아니라 종료(확정의 결과)라 clos_dt가 맞다';
    COMMENT ON COLUMN outb_order.shmt_dt IS '출고 확정 시각 — 재고가 창고를 떠난 것으로 확정된 시점. SHIPPED와 짝. 출고실적은 별도 테이블이 아니라 이 값 + inv_hist의 SHIP 행(rfn_doc_no = 출고번호)이다';
    COMMENT ON COLUMN inv.aloc_qty IS '예약 수량 — 출고 할당 전용이 아니라 예약수량 일반. 원천 셋: 출고 할당(outb_alloc, 보관) · 이동지시(inv_mov_task, 보관) · 피킹된 물량(SHIP-STAGE). 피킹은 예약을 출발지에서 소진하고 도착지에 다시 잡으며(from 예약 → to 예약), 이동확정·출고확정이 on_hand와 함께 소진. 물리 이동이 아니므로 이력에 기록하지 않음. 항등식: aloc_qty = 원천별 미소진 잔량 합 — 스테이징에서는 주문이 SHIPPED가 아닌 outb_alloc.pikng_qty의 합 (대사 대상)';
    COMMENT ON COLUMN outb_alloc.pikng_qty IS '피킹 완료 수량. 피킹 시 보관→SHIP-STAGE MOVE(tx PICK)와 함께 누적되고, 이때 보관 inv.aloc_qty가 소진되며 SHIP-STAGE inv.aloc_qty가 같은 만큼 잡힌다(예약 이전). 출고확정이 그 예약과 실물을 함께 소진. 항등식: pikng_qty = pikng_task.cmpl_qty = SUM(pikng_acrst.pikng_qty)';
    COMMENT ON COLUMN inv_hist.tx_typ IS 'RECEIVE 입고 / MOVE 이동(적치 포함) / ADJUST 조정 / PICK 피킹(보관→SHIP-STAGE, 2행) / SHIP 출고확정(SHIP-STAGE 반출, 1행 — 실물과 예약을 함께 소진, 도착지 없음)';
    RAISE NOTICE '주석 정정 완료';
END $mig$;
