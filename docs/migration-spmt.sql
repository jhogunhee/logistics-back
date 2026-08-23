-- =====================================================================
-- 정기 보충(SPMT) 도입 — 이동구분 SPMT 추가 + 보충지시 번호 채번 규칙.
--
-- 현재 라이브 상태(migration-fxng-loc.sql · migration-rpln.sql 적용) → schema.sql 상태.
-- migration-rpln.sql 뒤에 돌릴 것 — 그 파일이 제약을 (INV_MOV, RPLN)으로 재정의하므로 먼저 돌리면 SPMT가 빠진다.
-- 근거: docs/design.md 「고정 로케이션 마스터 (피킹존)」 — min 미달 시 max까지 채우는
--   지시를 inv_mov_task에 mov_dvsn=SPMT로 싣는다 (예약·MOVE 이력·용량 합산 재사용).
--   확정·취소는 이동지시 관리 화면이 INV_MOV와 함께 처리한다.
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — 존재 확인을 걸어 이미 적용된 항목은 건너뛴다
-- =====================================================================
DO $mig$
BEGIN
    -- 1. ck_inv_mov_dvsn 재정의 — SPMT 허용 ---------------------------------
    --    migration-rpln.sql 이 (INV_MOV, RPLN)으로 좁혀 둔 상태에 SPMT를 더한다.
    --    PTAWY는 적치가 putaway_task로 확정되며 제거됐으므로 되살리지 않는다.
    IF EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'ck_inv_mov_dvsn'
           AND pg_get_constraintdef(oid) NOT LIKE '%SPMT%'
    ) THEN
        ALTER TABLE inv_mov_task DROP CONSTRAINT ck_inv_mov_dvsn;
        ALTER TABLE inv_mov_task ADD CONSTRAINT ck_inv_mov_dvsn
            CHECK (mov_dvsn IN ('INV_MOV', 'RPLN', 'SPMT'));
        RAISE NOTICE 'ck_inv_mov_dvsn 재정의 (SPMT 추가)';
    ELSE
        RAISE NOTICE 'ck_inv_mov_dvsn 이미 SPMT 포함 — 건너뜀';
    END IF;

    -- 2. 보충지시 번호 채번 규칙 (SP-YYYYMMDD-NNN) ---------------------------
    IF NOT EXISTS (SELECT 1 FROM nbr_rule WHERE rule_cd = 'SPMT_NO') THEN
        INSERT INTO nbr_rule (rule_cd, rule_nm, prfx, prfx_dlmt, de_dlmt, seq_dgt, dync_ky_typ)
        VALUES ('SPMT_NO', '보충지시 번호', 'SP', '-', '-', 3, 'DAY');
        RAISE NOTICE 'nbr_rule SPMT_NO 추가';
    ELSE
        RAISE NOTICE 'nbr_rule SPMT_NO 이미 존재 — 건너뜀';
    END IF;

    -- 3. 코멘트 갱신 ---------------------------------------------------------
    COMMENT ON COLUMN inv_mov_task.mov_dvsn IS '이동구분 — INV_MOV 재고이동 / RPLN 수시보충(피킹지시 발행이 보관존 할당분에 짝으로 냄, 예약 없음, /outbound/replenishment 에서 전량 확정) / SPMT 정기보충(고정로케이션이 min 미달일 때 max까지 채움, 예약 있음, 2026-08-21). 적치·피킹은 각자 테이블(putaway_task · pikng_task)이라 여기 값이 없다. 등록은 재고이동 화면이 INV_MOV, 정기보충 화면이 SPMT 고정. 확정·취소는 INV_MOV·SPMT만 이동지시 관리 화면이 처리하고(둘 다 예약을 들어 실물을 옮기는 동일 작업) RPLN은 예약을 들지 않아 RplnService 전용 경로다. 적치는 별도 putaway_task 유지로 확정(2026-08-04 — FROM이 항상 스테이징이라 컬럼이 남고, ib_line_id 같은 입고 전용 컬럼이 이 테이블로 새어 나온다), 피킹도 별도 pikng_task로 확정(2026-08-20 — 같은 논리 + 예약 의미 충돌)되어 PIKNG 값은 제거했다';
    RAISE NOTICE 'inv_mov_task.mov_dvsn 코멘트 갱신';
END
$mig$;

-- 확인:
--   SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_inv_mov_dvsn';
--   SELECT * FROM nbr_rule WHERE rule_cd = 'SPMT_NO';
