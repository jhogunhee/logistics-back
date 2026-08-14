-- =====================================================================
-- 점포그룹·점포유형 도입 — store 컬럼 2개 + 공통코드 2그룹 + 시드 점포 값 채움.
--
-- 현재 라이브 상태(migration-add-store-cd-nbr.sql 적용) → schema.sql 상태.
-- 근거: 웨이브 편성 조건 「납품처그룹·납품처유형」과 할당 분배 대상 선별이
--   「점포 마스터에 컬럼이 없어 보류」로 묶여 있었다(WaveOrderField javadoc,
--   할당 분석설계 §13). 컬럼 신설로 보류를 해제한다 — WaveOrderField·AlocLineField에
--   상수가 추가되어 화면·저장 검증·실행에 자동 반영된다(P1).
--   - NULL = 미지정. 조건 판정에서 미지정 점포는 부정 연산자(NE·NOT_IN)만 참
--     (ConditionOperator 규칙 — 속성 없음).
--   - CHECK 없음: 값 목록은 공통코드(STORE_GRP·STORE_TYP)가 소유한다. OUTB_TYP과
--     같은 방식 — 코드가 늘면 코드관리 화면에서 추가하면 되고 DDL 변경이 없다.
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — 존재 확인을 걸어 이미 적용된 항목은 건너뛴다
-- =====================================================================
DO $mig$
BEGIN
    -- 1. store 컬럼 ---------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'store' AND column_name = 'store_grp') THEN
        ALTER TABLE store ADD COLUMN store_grp VARCHAR(10);
        ALTER TABLE store ADD COLUMN store_typ VARCHAR(10);
        COMMENT ON COLUMN store.store_grp IS '점포그룹 (공통코드 STORE_GRP — 체인·계열 묶음). NULL = 미지정. 웨이브 편성·할당 분배 조건의 기준값. CHECK 없음 — 값 목록은 공통코드가 소유하고 존재 검증은 하지 않는다(화면 콤보로만 들어온다)';
        COMMENT ON COLUMN store.store_typ IS '점포유형 (공통코드 STORE_TYP — 편의점·마트·급식). NULL = 미지정. 웨이브 편성·할당 분배 조건의 기준값';
        RAISE NOTICE 'store.store_grp · store_typ 컬럼 추가';
    ELSE
        RAISE NOTICE 'store.store_grp 이미 존재 — 건너뜀';
    END IF;

    -- 2. 공통코드 그룹 + 코드값 ---------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM code_group WHERE grp_cd = 'STORE_GRP') THEN
        INSERT INTO code_group (grp_cd, grp_nm, dscr) VALUES
            ('STORE_GRP', '점포그룹', '점포의 체인·계열 묶음 (store.store_grp). 웨이브 편성 조건 「납품처그룹」과 할당 분배 대상 선별의 기준값');
        INSERT INTO code_detail (grp_cd, code_cd, code_nm, srt_seq) VALUES
            ('STORE_GRP', 'CNU', '씨앤유', 1),
            ('STORE_GRP', 'HANMAUM', '한마음', 2),
            ('STORE_GRP', 'HAENGBOK', '행복', 3);
        RAISE NOTICE '공통코드 STORE_GRP 그룹 + 코드 3건 시드';
    ELSE
        RAISE NOTICE '공통코드 STORE_GRP 이미 존재 — 건너뜀';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM code_group WHERE grp_cd = 'STORE_TYP') THEN
        INSERT INTO code_group (grp_cd, grp_nm, dscr) VALUES
            ('STORE_TYP', '점포유형', '점포의 업태 (store.store_typ — 편의점·마트·급식). 웨이브 편성 조건 「납품처유형」과 할당 분배 대상 선별의 기준값');
        INSERT INTO code_detail (grp_cd, code_cd, code_nm, srt_seq) VALUES
            ('STORE_TYP', 'CVS', '편의점', 1),
            ('STORE_TYP', 'MART', '마트', 2),
            ('STORE_TYP', 'FDSVC', '급식', 3);
        RAISE NOTICE '공통코드 STORE_TYP 그룹 + 코드 3건 시드';
    ELSE
        RAISE NOTICE '공통코드 STORE_TYP 이미 존재 — 건너뜀';
    END IF;

    -- 3. 시드 점포 값 채움 --------------------------------------------------
    -- 미지정(NULL)인 시드 점포에만 채운다 — 화면에서 이미 값을 넣었다면 덮지 않는다.
    UPDATE store SET store_grp = 'CNU',      store_typ = 'CVS'
     WHERE store_cd IN ('ST-0001', 'ST-0002') AND store_grp IS NULL;
    UPDATE store SET store_grp = 'HANMAUM',  store_typ = 'MART'
     WHERE store_cd IN ('ST-0003', 'ST-0004') AND store_grp IS NULL;
    UPDATE store SET store_grp = 'HAENGBOK', store_typ = 'FDSVC'
     WHERE store_cd = 'ST-0005' AND store_grp IS NULL;
    RAISE NOTICE '시드 점포 5건 그룹·유형 채움 (이미 값이 있으면 유지)';
END
$mig$;

-- 확인:
--   SELECT store_cd, store_nm, store_grp, store_typ FROM store ORDER BY store_cd;
--   SELECT * FROM code_detail WHERE grp_cd IN ('STORE_GRP', 'STORE_TYP') ORDER BY grp_cd, srt_seq;
