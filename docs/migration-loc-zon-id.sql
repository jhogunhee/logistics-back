-- =====================================================================
-- 증분 마이그레이션: loc.zon_cd(문자열) → loc.zon_id(존 PK 참조)
--   대상: migration-zon.sql 까지 적용된 라이브 DB → docs/schema.sql 상태
--   근거: 로케이션의 존 참조를 Loc.zon @ManyToOne 연관으로 매핑한다.
--         FK 컬럼은 참조 테이블 PK명을 그대로 쓰는 규칙(zon_id)을 따르고,
--         PK가 아닌 컬럼(zon_cd)을 조인 키로 두면 Hibernate가 지연 프록시를 못 만든다.
--         FK 제약은 걸지 않는다 (FK 0건 정책 유지) — 존재 검증은 LocService, 삭제 가드는 ZonService.
--
--   ※ 이 증분을 적용하기 전에 백엔드를 재시작하면 안 된다 — Loc 엔티티가 zon_id를
--     매핑하므로 컬럼 없는 DB에서는 로케이션 조회가 실패한다 (ddl-auto=none).
--   ※ zon 마스터에 없는 존 코드를 가진 loc 행이 있으면 백필이 못 채워 NOT NULL 단계에서
--     멈춘다 — 그 행을 먼저 존 마스터에 등록하거나 로케이션을 정리한 뒤 다시 돌릴 것.
--
--   재실행 안전: 단계마다 컬럼 존재 확인. BEGIN/COMMIT 없이 DO 블록 하나 (DBeaver 25P02 방지).
-- =====================================================================

DO $mig$
DECLARE
    v_orphans BIGINT;
BEGIN

    -- 1. 컬럼 추가 (NULL 허용으로 먼저 만들고 백필 뒤 NOT NULL)
    ALTER TABLE loc ADD COLUMN IF NOT EXISTS zon_id BIGINT;

    -- 2. 백필 — 옛 zon_cd가 아직 남아 있을 때만
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'loc' AND column_name = 'zon_cd') THEN

        UPDATE loc l
           SET zon_id = z.zon_id
          FROM zon z
         WHERE z.zon_cd = l.zon_cd
           AND l.zon_id IS NULL;

        SELECT count(*) INTO v_orphans FROM loc WHERE zon_id IS NULL;
        IF v_orphans > 0 THEN
            RAISE EXCEPTION 'loc % 행의 zon_cd가 zon 마스터에 없다 — 존을 먼저 등록하고 다시 실행할 것', v_orphans;
        END IF;

        ALTER TABLE loc DROP COLUMN zon_cd;
        RAISE NOTICE 'loc.zon_cd → zon_id 백필 완료, zon_cd 삭제';
    ELSE
        RAISE NOTICE 'loc.zon_cd 이미 없음 — 백필 건너뜀';
    END IF;

    ALTER TABLE loc ALTER COLUMN zon_id SET NOT NULL;

    -- 3. 코멘트 동기화 (schema.sql과 같은 문구)
    COMMENT ON COLUMN loc.zon_id  IS '소속 존 (zon.zon_id). FK는 없다 — 존재 검증은 LocService, 존 삭제 가드는 ZonService가 한다';
    COMMENT ON TABLE  zon         IS '존 마스터. 로케이션의 상위 그룹 (loc.zon_id가 참조, FK 없음)';
    COMMENT ON COLUMN zon.zon_cd  IS '존 코드 (업무 식별자, 예: DRY, RCV-STAGE). 등록 후 변경 금지 — 재고조사 범위(inv_stktk.zon_cd) 등이 코드값을 보존한다';

END
$mig$;
