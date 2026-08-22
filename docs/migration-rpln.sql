-- =====================================================================
-- 수시보충 — 이동지시에 RPLN 구분 · 짝 피킹지시 · 이력 유형.
--
-- 현재 라이브 상태 → schema.sql 상태. 바뀌는 것 셋:
--   ① inv_mov_task.mov_dvsn 에 RPLN 추가 + pikng_task_id 컬럼(짝 피킹지시, FK 없음)
--      피킹지시 발행이 보관존 할당분에 짝으로 내는 보충지시다. 예약은 잡지 않는다(할당이 든다).
--      살아 있는 보충은 피킹지시당 하나 — 취소된 것은 다시 낼 수 있어 부분 유니크.
--   ② inv_hist.tx_typ 에 RPLN 추가 — 보관존→피킹존 이동을 일반 MOVE 와 구분해 남긴다(예약이 함께 옮겨 간다).
--   ③ 주석 정정 — fxng_loc 「보충 프로세스 미구현」 → 수시보충 도착지 1순위.
--   ④ mov_dvsn 에서 PTAWY 제거 — 적치는 putaway_task 가 따로라 쓰는 코드가 0건, 라이브 행도 0건(2026-08-22 확인).
--      피킹(PIKNG)을 뺐을 때와 같은 이유 — 어느 코드도 쓰지 않는 값을 선택지로 남기지 않는다.
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - 재실행 안전 — 존재 확인을 걸어 이미 적용된 항목은 건너뛴다
-- =====================================================================
DO $mig$
BEGIN
    -- 1) 짝 피킹지시 컬럼
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'inv_mov_task' AND column_name = 'pikng_task_id'
    ) THEN
        ALTER TABLE inv_mov_task ADD COLUMN pikng_task_id BIGINT;
        RAISE NOTICE 'inv_mov_task.pikng_task_id 추가';
    END IF;

    -- 2) 이동구분 CHECK — RPLN 추가 · PTAWY 제거. PTAWY 행이 남아 있으면 제약이 거부하므로 먼저 센다
    IF EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'ck_inv_mov_dvsn'
           AND (pg_get_constraintdef(oid) NOT LIKE '%RPLN%' OR pg_get_constraintdef(oid) LIKE '%PTAWY%')
    ) THEN
        IF EXISTS (SELECT 1 FROM inv_mov_task WHERE mov_dvsn = 'PTAWY') THEN
            RAISE EXCEPTION 'inv_mov_task 에 mov_dvsn = PTAWY 행이 있어 제약을 바꿀 수 없습니다 — 먼저 정리하세요';
        END IF;
        ALTER TABLE inv_mov_task DROP CONSTRAINT ck_inv_mov_dvsn;
        ALTER TABLE inv_mov_task ADD CONSTRAINT ck_inv_mov_dvsn CHECK (mov_dvsn IN ('INV_MOV', 'RPLN'));
        RAISE NOTICE 'ck_inv_mov_dvsn = (INV_MOV, RPLN)';
    END IF;

    -- 3) 살아 있는 보충은 피킹지시당 하나
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'uq_inv_mov_rpln_task') THEN
        CREATE UNIQUE INDEX uq_inv_mov_rpln_task ON inv_mov_task (pikng_task_id) WHERE status <> 'CANCELLED';
        RAISE NOTICE 'uq_inv_mov_rpln_task 생성';
    END IF;

    -- 4) 이력 유형 CHECK 에 RPLN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'ck_invh_tx_typ' AND pg_get_constraintdef(oid) NOT LIKE '%RPLN%'
    ) THEN
        ALTER TABLE inv_hist DROP CONSTRAINT ck_invh_tx_typ;
        ALTER TABLE inv_hist ADD CONSTRAINT ck_invh_tx_typ CHECK (tx_typ IN ('RECEIVE', 'MOVE', 'ADJUST', 'PICK', 'SHIP', 'RPLN'));
        RAISE NOTICE 'ck_invh_tx_typ 에 RPLN 추가';
    END IF;

    -- 5) 주석
    COMMENT ON COLUMN inv_mov_task.mov_dvsn      IS '이동구분 — INV_MOV 재고이동 / RPLN 수시보충(피킹지시 발행이 보관존 할당분에 짝으로 냄, 예약 없음, /outbound/replenishment 에서 전량 확정). 적치·피킹은 각자 테이블(putaway_task · pikng_task)이라 여기 값이 없다. 재고이동 화면의 등록은 INV_MOV 고정이고, 그 화면의 확정·취소도 INV_MOV만 허용한다';
    COMMENT ON COLUMN inv_mov_task.pikng_task_id IS '짝 피킹지시 (RPLN만, FK 없음). 수시보충은 피킹지시 발행이 보관존 할당분에 짝으로 내는 지시라 주인이 피킹지시다. 예약은 잡지 않는다 — 할당이 이미 들고 있고, 확정이 그 예약을 도착지로 옮기며 outb_alloc.inv_id 도 도착지 행으로 바꾼다. 전량 확정만(할당 행 하나 = 재고 행 하나)';
    COMMENT ON COLUMN inv_hist.tx_typ IS 'RECEIVE 입고 / MOVE 이동(적치 포함) / ADJUST 조정 / PICK 피킹(보관→SHIP-STAGE, 2행) / SHIP 출고확정(SHIP-STAGE 반출, 1행 — 실물과 예약을 함께 소진, 도착지 없음) / RPLN 수시보충(보관존→피킹존, 2행 — 예약이 함께 옮겨 간다)';
    COMMENT ON TABLE fxng_loc IS '고정 로케이션 마스터. 상품×로케이션 지정 (FK 없음 — 존재·STORAGE·온도대 검증은 FxngLocService, 삭제 가드는 WmsProdRefChecker·LocRefQueryRepository). 적치 FXNG_LOC 방식의 후보 원천이고 수시보충 도착지의 1순위다. min/max는 정기보충 기준 — 정기보충은 미구현';
END
$mig$;
