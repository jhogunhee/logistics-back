-- =====================================================================
-- 이동지시(inv_mov_task)에 이동구분(mov_dvsn) 추가.
--
-- 현재 라이브 상태(migration-inv-mov-task.sql 적용) → schema.sql 상태.
-- 근거: 재고이동 화면의 확정·취소는 그 화면에서 등록한 지시(재고이동 유형)만 대상이어야
--   한다 — 적치·피킹 지시가 이 테이블로 들어와도 유형이 구분되어 각자의 경로에서만
--   처리된다 (레거시의 「재고업무 유형만 확정 가능, 입고적치 차단」 대응).
--   값: INV_MOV 재고이동 / PTAWY 적치 / PIKNG 피킹. 기존 행은 전부 재고이동 화면에서
--   등록된 것이므로 INV_MOV로 백필한다.
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — 존재 확인을 걸어 이미 적용된 항목은 건너뛴다
-- =====================================================================
DO $mig$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'inv_mov_task' AND column_name = 'mov_dvsn'
    ) THEN
        ALTER TABLE inv_mov_task ADD COLUMN mov_dvsn VARCHAR(10);
        UPDATE inv_mov_task SET mov_dvsn = 'INV_MOV' WHERE mov_dvsn IS NULL;
        ALTER TABLE inv_mov_task ALTER COLUMN mov_dvsn SET NOT NULL;
        RAISE NOTICE 'inv_mov_task.mov_dvsn 컬럼 추가 (기존 행 INV_MOV 백필)';
    ELSE
        RAISE NOTICE 'inv_mov_task.mov_dvsn 컬럼 이미 존재 — 건너뜀';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_inv_mov_dvsn') THEN
        ALTER TABLE inv_mov_task ADD CONSTRAINT ck_inv_mov_dvsn
            CHECK (mov_dvsn IN ('INV_MOV', 'PTAWY', 'PIKNG'));
        RAISE NOTICE 'ck_inv_mov_dvsn CHECK 추가';
    ELSE
        RAISE NOTICE 'ck_inv_mov_dvsn CHECK 이미 존재 — 건너뜀';
    END IF;

    COMMENT ON COLUMN inv_mov_task.mov_dvsn IS '이동구분 — INV_MOV 재고이동 / PTAWY 적치 / PIKNG 피킹. 재고이동 화면의 등록은 INV_MOV 고정이고, 그 화면의 확정·취소도 INV_MOV만 허용한다(적치·피킹 유형은 각자의 경로 전용 — 레거시의 「재고업무 유형만 확정 가능, 입고적치 차단」 대응). 적치·피킹 지시를 이 테이블로 통합할지(별도 putaway_task 유지 여부)는 각 지시 구현 시 결정';
    RAISE NOTICE 'inv_mov_task.mov_dvsn 코멘트 갱신';
END
$mig$;

-- 확인:
--   SELECT mov_dvsn, count(*) FROM inv_mov_task GROUP BY mov_dvsn;
