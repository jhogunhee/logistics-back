-- =====================================================================
-- 증분 마이그레이션: loc.ptawy_prty (적치 우선순위) 신설
--   대상: migration-add-stgy-inspection-putaway.sql 까지 적용된 라이브 DB → docs/schema.sql 상태
--   근거: 적치 전략의 후보 정렬 기준 "적치순서(PTAWY_PRTY)" — 레거시 PTAWY_SRT_WAY의 대응.
--         피킹 동선(pikng_prty)과 적치 동선이 다른 창고를 위해 분리한다.
--
--   ※ 이 증분을 적용하기 전에 백엔드를 재시작하면 안 된다 — Loc 엔티티가 이 컬럼을
--     매핑하므로 컬럼 없는 DB에서는 로케이션 조회가 실패한다 (ddl-auto=none).
--
--   재실행 안전: IF NOT EXISTS. BEGIN/COMMIT 없이 DO 블록 하나 (DBeaver 25P02 방지).
-- =====================================================================

DO $mig$
BEGIN

    ALTER TABLE loc ADD COLUMN IF NOT EXISTS ptawy_prty INTEGER DEFAULT 0 NOT NULL;

    COMMENT ON COLUMN loc.ptawy_prty IS '적치 우선순위. 적치 전략의 후보 정렬 기준(PTAWY_PRTY) — 낮을수록 먼저 배정. 피킹 동선(pikng_prty)과 적치 동선이 다른 창고를 위해 분리한다 (레거시 PTAWY_SRT_WAY의 대응)';

    -- 전략 정렬 기준에 PTAWY_PRTY가 추가됨에 따라 코멘트 동기화 (값 목록의 주인은 코드 — 문서용)
    COMMENT ON COLUMN ptawy_stgy.loc_srt IS '후보 정렬 [{"field":PIKNG_PRTY|PTAWY_PRTY|LOC_CD,"dir":ASC|DESC}]. 빈 배열 = 기본(피킹순위 ASC → 로케이션코드 ASC)';

END
$mig$;
