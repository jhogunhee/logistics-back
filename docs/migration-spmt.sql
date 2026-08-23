-- =====================================================================
-- 정기 보충(SPMT) 도입 — 이동구분 SPMT 추가 + 보충지시 번호 채번 규칙.
--
-- 현재 라이브 상태(migration-fxng-loc.sql 적용) → schema.sql 상태.
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
    IF EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'ck_inv_mov_dvsn'
           AND pg_get_constraintdef(oid) NOT LIKE '%SPMT%'
    ) THEN
        ALTER TABLE inv_mov_task DROP CONSTRAINT ck_inv_mov_dvsn;
        ALTER TABLE inv_mov_task ADD CONSTRAINT ck_inv_mov_dvsn
            CHECK (mov_dvsn IN ('INV_MOV', 'PTAWY', 'SPMT'));
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
    COMMENT ON COLUMN inv_mov_task.mov_dvsn IS '이동구분 — INV_MOV 재고이동 / PTAWY 적치 / SPMT 보충(피킹존 고정로케이션을 min 미달 시 max까지 채우는 보관→피킹 이동, 2026-08-21). 등록은 재고이동 화면이 INV_MOV, 보충 화면이 SPMT 고정. 확정·취소는 두 유형 모두 이동지시 관리 화면이 처리한다(실물을 옮기는 동일 작업). 적치는 별도 putaway_task 유지로 확정(2026-08-04 — FROM이 항상 스테이징이라 컬럼이 남고, ib_line_id 같은 입고 전용 컬럼이 이 테이블로 새어 나온다), 피킹도 별도 pikng_task로 확정(2026-08-20 — 같은 논리 + 예약 의미 충돌)되어 PIKNG 값은 제거했다';
    RAISE NOTICE 'inv_mov_task.mov_dvsn 코멘트 갱신';
END
$mig$;

-- 확인:
--   SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_inv_mov_dvsn';
--   SELECT * FROM nbr_rule WHERE rule_cd = 'SPMT_NO';
