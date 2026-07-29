-- =====================================================================
-- nbr_rule 패턴 구조화 — 자유 텍스트 ptrn을 prfx/prfx_dlmt/de_dlmt/seq_dgt로 교체하고
-- dync_ky_typ을 NONE/DATE 2종에서 NONE/YEAR/MONTH/DAY 4종으로 확장한다.
-- us_yn(사용 여부)도 제거한다 — 앞으로는 비활성화가 아니라 물리 삭제만 허용.
--
--   전제: docs/migration-nbr.sql까지 적용된 DB.
--   전체가 DO 블록 하나다 (BEGIN;/COMMIT;을 쓰지 않는다) — CLAUDE.md 규칙.
--   전 구간에 존재 확인이 걸려 있어 몇 번을 돌려도 안전하다.
--
--   실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--     - NOTICE는 결과 패널의 Server Output 탭에서 볼 것
--     - 이 스크립트를 적용하는 배포에는 애플리케이션 코드 변경(NbrRule/NbrPattern/
--       NbrService/NbrRuleService/DTO 4종/DyncKyTyp, Task 1~3)도 같이 나가야 한다 —
--       옛 코드가 남은 채로 이 스크립트만 먼저 적용하면 ptrn/us_yn 컬럼을 찾는
--       옛 코드가 즉시 실패한다.
-- =====================================================================

DO $nbr_structured$
DECLARE
    orphan_cnt   INTEGER;
    disabled_cnt INTEGER;
BEGIN
    -- 1. 신규 컬럼 추가 (nullable) ------------------------------------
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'nbr_rule' AND column_name = 'prfx'
    ) THEN
        ALTER TABLE nbr_rule ADD COLUMN prfx VARCHAR(20);
        RAISE NOTICE 'nbr_rule.prfx 컬럼 추가';
    ELSE
        RAISE NOTICE 'nbr_rule.prfx 컬럼 이미 존재 — 건너뜀';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'nbr_rule' AND column_name = 'prfx_dlmt'
    ) THEN
        ALTER TABLE nbr_rule ADD COLUMN prfx_dlmt VARCHAR(1);
        RAISE NOTICE 'nbr_rule.prfx_dlmt 컬럼 추가';
    ELSE
        RAISE NOTICE 'nbr_rule.prfx_dlmt 컬럼 이미 존재 — 건너뜀';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'nbr_rule' AND column_name = 'de_dlmt'
    ) THEN
        ALTER TABLE nbr_rule ADD COLUMN de_dlmt VARCHAR(1);
        RAISE NOTICE 'nbr_rule.de_dlmt 컬럼 추가';
    ELSE
        RAISE NOTICE 'nbr_rule.de_dlmt 컬럼 이미 존재 — 건너뜀';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'nbr_rule' AND column_name = 'seq_dgt'
    ) THEN
        ALTER TABLE nbr_rule ADD COLUMN seq_dgt SMALLINT;
        RAISE NOTICE 'nbr_rule.seq_dgt 컬럼 추가';
    ELSE
        RAISE NOTICE 'nbr_rule.seq_dgt 컬럼 이미 존재 — 건너뜀';
    END IF;

    -- 2. 기존 ptrn을 파싱해 백필 ----------------------------------------
    --    두 가지 알려진 모양만 지원한다:
    --      PREFIX{prfx_dlmt}{SEQ:n}                             (예: PROD-{SEQ:4})
    --      PREFIX{prfx_dlmt}{yyyyMMdd}{de_dlmt}{SEQ:n}           (예: IB-{yyyyMMdd}-{SEQ:3})
    --    de_dlmt는 날짜 토큰이 있는(dync_ky_typ='DATE') 행만 '}...{SEQ' 사이 리터럴에서
    --    추출한다. NONE 행은 de_dlmt를 쓰지 않으므로 prfx_dlmt와 같은 값으로 채운다.
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'nbr_rule' AND column_name = 'ptrn'
    ) THEN
        UPDATE nbr_rule
           SET prfx      = COALESCE(prfx, regexp_replace(split_part(ptrn, '{', 1), '.$', '')),
               prfx_dlmt = COALESCE(prfx_dlmt, right(split_part(ptrn, '{', 1), 1)),
               seq_dgt   = COALESCE(seq_dgt, (regexp_match(ptrn, '\{SEQ:(\d)\}'))[1]::SMALLINT),
               de_dlmt   = COALESCE(
                               de_dlmt,
                               CASE
                                   WHEN dync_ky_typ = 'DATE'
                                       THEN (regexp_match(ptrn, '\}(.)\{SEQ'))[1]
                                   ELSE right(split_part(ptrn, '{', 1), 1)
                               END
                           )
         WHERE prfx IS NULL OR prfx_dlmt IS NULL OR de_dlmt IS NULL OR seq_dgt IS NULL;
        RAISE NOTICE 'ptrn → prfx/prfx_dlmt/de_dlmt/seq_dgt 백필 완료';
    ELSE
        RAISE NOTICE 'nbr_rule.ptrn 컬럼이 이미 없음 — 백필 건너뜀 (이미 이관된 DB)';
    END IF;

    -- 3. 백필 결과 검증 --------------------------------------------------
    SELECT count(*) INTO orphan_cnt
      FROM nbr_rule
     WHERE prfx IS NULL OR prfx_dlmt IS NULL OR de_dlmt IS NULL OR seq_dgt IS NULL;
    IF orphan_cnt > 0 THEN
        RAISE EXCEPTION '% 건의 nbr_rule 행이 알려진 ptrn 모양과 맞지 않아 백필에 실패했습니다. 수동으로 확인하세요.', orphan_cnt;
    END IF;

    -- 4. dync_ky_typ 확장: DATE → DAY, CHECK 제약 재생성 ------------------
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'nbr_rule' AND column_name = 'dync_ky_typ'
    ) THEN
        UPDATE nbr_rule SET dync_ky_typ = 'DAY' WHERE dync_ky_typ = 'DATE';
        RAISE NOTICE 'dync_ky_typ=DATE → DAY 변환 완료';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_nbr_rule_dync_ky_typ') THEN
        ALTER TABLE nbr_rule DROP CONSTRAINT ck_nbr_rule_dync_ky_typ;
    END IF;
    ALTER TABLE nbr_rule ADD CONSTRAINT ck_nbr_rule_dync_ky_typ
        CHECK (dync_ky_typ IN ('NONE', 'YEAR', 'MONTH', 'DAY'));

    -- 5. prfx/prfx_dlmt/de_dlmt/seq_dgt NOT NULL + CHECK 제약 ------------
    ALTER TABLE nbr_rule ALTER COLUMN prfx SET NOT NULL;
    ALTER TABLE nbr_rule ALTER COLUMN prfx_dlmt SET NOT NULL;
    ALTER TABLE nbr_rule ALTER COLUMN de_dlmt SET NOT NULL;
    ALTER TABLE nbr_rule ALTER COLUMN seq_dgt SET NOT NULL;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_nbr_rule_prfx_dlmt') THEN
        ALTER TABLE nbr_rule ADD CONSTRAINT ck_nbr_rule_prfx_dlmt CHECK (prfx_dlmt IN ('-', '_', ''));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_nbr_rule_de_dlmt') THEN
        ALTER TABLE nbr_rule ADD CONSTRAINT ck_nbr_rule_de_dlmt CHECK (de_dlmt IN ('-', '_', ''));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_nbr_rule_seq_dgt') THEN
        ALTER TABLE nbr_rule ADD CONSTRAINT ck_nbr_rule_seq_dgt CHECK (seq_dgt BETWEEN 1 AND 9);
    END IF;

    -- 6. us_yn='N' 행이 있으면 중단 (물리 삭제 정책 전환 안전장치) -----------
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'nbr_rule' AND column_name = 'us_yn'
    ) THEN
        SELECT count(*) INTO disabled_cnt FROM nbr_rule WHERE us_yn = 'N';
        IF disabled_cnt > 0 THEN
            RAISE EXCEPTION '% 건의 nbr_rule 행이 us_yn=N(비활성)입니다. 물리 삭제만 허용하는 정책으로 바뀌므로, 이 스크립트를 다시 돌리기 전에 해당 행을 삭제할지 직접 결정하세요.', disabled_cnt;
        END IF;
    END IF;

    -- 7. ptrn/us_yn 컬럼과 관련 제약 DROP ---------------------------------
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_nbr_rule_us_yn') THEN
        ALTER TABLE nbr_rule DROP CONSTRAINT ck_nbr_rule_us_yn;
    END IF;
    ALTER TABLE nbr_rule DROP COLUMN IF EXISTS us_yn;
    ALTER TABLE nbr_rule DROP COLUMN IF EXISTS ptrn;

    -- 8. 컬럼 코멘트 갱신 --------------------------------------------------
    COMMENT ON TABLE  nbr_rule IS '채번 규칙. 접두어+구분자+SEQ 자릿수+리셋단위로 형식을 정의 (예: prfx=PROD, prfx_dlmt=-, seq_dgt=4, dync_ky_typ=NONE → PROD-0001)';
    COMMENT ON COLUMN nbr_rule.prfx        IS '접두어 리터럴 (예: IB, PROD)';
    COMMENT ON COLUMN nbr_rule.prfx_dlmt   IS '접두어 뒤 구분자. NONE이면 접두어→SEQ 사이 유일한 경계로 쓰인다';
    COMMENT ON COLUMN nbr_rule.de_dlmt     IS '날짜 뒤 구분자. NONE에서는 쓰이지 않음(화면에서 비활성화 처리)';
    COMMENT ON COLUMN nbr_rule.seq_dgt     IS 'SEQ 자릿수 (zero-pad 폭), 1~9';
    COMMENT ON COLUMN nbr_rule.dync_ky_typ IS '리셋 단위. NONE=카운터 전역 공유(dync_ky 고정값 -) / YEAR·MONTH·DAY=호출자가 넘긴 날짜를 해당 단위로 잘라 카운터 분리, 화면 날짜 조각 포맷(yyyy/yyyyMM/yyyyMMdd)도 여기서 파생';
    COMMENT ON COLUMN nbr_seq.dync_ky IS '동적키 값. dync_ky_typ=NONE이면 고정값 "-", YEAR/MONTH/DAY면 각각 yyyy/yyyyMM/yyyyMMdd';

    RAISE NOTICE 'nbr_rule 구조화 마이그레이션 완료';
END
$nbr_structured$;

-- =====================================================================
-- 적용 후 확인
--   1) 6건이 정확히 파싱됐는지
--      SELECT rule_cd, prfx, prfx_dlmt, de_dlmt, seq_dgt, dync_ky_typ FROM nbr_rule ORDER BY rule_cd;
--      -- PROD_CD/PROD/-/-/4/NONE, VNDR_CD/VD/-/-/4/NONE,
--      -- OMS_IB_NO/PO/-/-/3/DAY, IB_NO/IB/-/-/3/DAY, OUTB_NO/OB/-/-/3/DAY, OUTB_WAV_NO/WV/-/-/3/DAY
--   2) ptrn/us_yn 컬럼이 없어졌는지
--      SELECT column_name FROM information_schema.columns
--       WHERE table_name = 'nbr_rule' AND column_name IN ('ptrn', 'us_yn');
--      -- 0건이어야 한다
--   3) 발급이 기존과 동일한 번호를 내는지 (예: IB_NO)
--      -- 애플리케이션에서 NbrService.issue("IB_NO", LocalDate.now())를 호출해 확인
-- =====================================================================
