-- =====================================================================
-- putaway_task 의 인덱스·제약 이름 ptwy → ptawy.
--
-- 배경: 「적치」가 스키마 안에서 세 철자로 갈려 있었다.
--   - putaway_task            (테이블명 — 규칙 5로 사전 적용 예외라 그대로 둔다)
--   - ptawy_stgy · ptawy_qty  (사전값 PTAWY)
--   - ix_ptwy_task_* · ck_ptwy_task_*  (schema.sql 머리말의 옛 약어 ptwy)
--   테이블명은 예외로 남기더라도, 같은 개념에 세 철자를 쓰는 것까지 예외로 볼 수는 없다.
--   인덱스·제약은 이름이 곧 식별자일 뿐이라 개명 비용이 없어 사전 쪽으로 맞춘다.
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — 존재 확인을 걸어 이미 적용된 항목은 건너뛴다
-- =====================================================================
DO $mig$
DECLARE
    r        RECORD;
    n_idx    INT := 0;
    n_con    INT := 0;
BEGIN
    -- 인덱스 -------------------------------------------------------------
    FOR r IN
        SELECT old_nm, new_nm FROM (VALUES
            ('ix_ptwy_task_line',     'ix_ptawy_task_line'),
            ('ix_ptwy_task_open_loc', 'ix_ptawy_task_open_loc')
        ) AS v(old_nm, new_nm)
    LOOP
        IF EXISTS (SELECT 1 FROM pg_class c
                     JOIN pg_namespace s ON s.oid = c.relnamespace
                    WHERE c.relkind = 'i' AND s.nspname = current_schema()
                      AND c.relname = r.old_nm) THEN
            EXECUTE format('ALTER INDEX %I RENAME TO %I', r.old_nm, r.new_nm);
            n_idx := n_idx + 1;
        END IF;
    END LOOP;

    -- 제약 ---------------------------------------------------------------
    FOR r IN
        SELECT old_nm, new_nm FROM (VALUES
            ('ck_ptwy_task_status', 'ck_ptawy_task_status'),
            ('ck_ptwy_task_qty',    'ck_ptawy_task_qty')
        ) AS v(old_nm, new_nm)
    LOOP
        IF EXISTS (SELECT 1 FROM pg_constraint c
                     JOIN pg_class t ON t.oid = c.conrelid
                     JOIN pg_namespace s ON s.oid = t.relnamespace
                    WHERE s.nspname = current_schema()
                      AND t.relname = 'putaway_task' AND c.conname = r.old_nm) THEN
            EXECUTE format('ALTER TABLE putaway_task RENAME CONSTRAINT %I TO %I', r.old_nm, r.new_nm);
            n_con := n_con + 1;
        END IF;
    END LOOP;

    RAISE NOTICE '인덱스 % 개 · 제약 % 개 개명 (ptwy → ptawy)', n_idx, n_con;
END $mig$;
