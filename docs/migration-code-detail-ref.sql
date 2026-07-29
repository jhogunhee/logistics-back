-- =====================================================================
-- 공통코드 상세에 참조값 3칸 추가 (code_detail.ref1 ~ ref3)
--   전제: docs/migration-nbr.sql 까지 적용된 DB.
--
--   배경 —
--   코드마다 딸린 자잘한 속성(임계값 · 부가 코드 등)을 담을 자리가 없었다. 속성이 하나 생길
--   때마다 컬럼을 늘리거나 별도 테이블을 만드는 대신, 공통코드의 관행대로 범용 참조값 칸을 둔다.
--
--   ▣ 뜻은 그룹마다 다르다.
--     컬럼 이름으로는 무엇이 들어 있는지 알 수 없다 — 그게 이 방식의 대가다.
--     무엇을 담았는지는 (1) 그 값을 읽는 코드와 (2) code_group.description 에 남긴다.
--
--   ▣ 표시 스타일(뱃지 색 등)은 여기 담지 않는다.
--     그건 프론트 상수가 갖기로 이미 정한 사항이다 (code_group 테이블 주석 참고).
--     여기 담으면 화면 스타일이 DB 배포에 묶인다.
--
--   ▣ 이름은 ref 다 (rfn 아님).
--     표준 단어 사전은 원래 「참조 = RFN」이었고 과거에 ref_doc_no → rfn_doc_no 개명까지
--     했지만, ref 가 통용되는 표기라 사전을 REF 로 되돌렸다(docs/naming-dictionary.md).
--     기존 inv_hist.rfn_doc_typ · rfn_doc_no 는 아직 옛 표기로 남아 있어 당분간 둘이 공존한다.
--
--   ▣ 전체가 DO 블록 하나다 (BEGIN;/COMMIT; 을 쓰지 않는다) — CLAUDE.md 규칙.
--     존재 확인이 걸려 있어 몇 번을 돌려도 안전하다.
--
--   실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--     - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
-- =====================================================================

DO $code_ref$
DECLARE
    c text;
BEGIN
    FOREACH c IN ARRAY ARRAY['ref1', 'ref2', 'ref3']
    LOOP
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_name = 'code_detail' AND column_name = c) THEN
            EXECUTE format('ALTER TABLE code_detail ADD COLUMN %I VARCHAR(100)', c);
            RAISE NOTICE 'code_detail.% 추가', c;
        ELSE
            RAISE NOTICE 'code_detail.% 이미 존재 — 건너뜀', c;
        END IF;
    END LOOP;

    COMMENT ON COLUMN code_detail.ref1 IS '참조값1. 뜻은 그룹마다 다르다 (해당 그룹의 description 참고)';
    COMMENT ON COLUMN code_detail.ref2 IS '참조값2';
    COMMENT ON COLUMN code_detail.ref3 IS '참조값3';

    RAISE NOTICE '공통코드 참조값 3칸 반영 완료';
END
$code_ref$;

-- =====================================================================
-- 적용 후 확인
--   SELECT grp_cd, code_cd, code_nm, srt_seq, ref1, ref2, ref3
--     FROM code_detail ORDER BY grp_cd, srt_seq;
-- =====================================================================
