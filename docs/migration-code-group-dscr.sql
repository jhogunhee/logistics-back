-- =====================================================================
-- code_group.description → dscr 수렴.
--
-- 배경: 이 컬럼의 이름을 두 문서가 다르게 말하고 있었다.
--   - docs/schema.sql          : description  (신규 DB는 이 이름으로 만들어진다)
--   - migration-catchup-to-schema.sql 의 개명 목록 : description → dscr
--     (기존 DB는 이 이름으로 바뀐다)
--   즉 「신규 DB는 schema.sql, 기존 DB는 catchup」이라는 두 구축 경로가 서로 다른
--   스키마를 만들고 있었고, 엔티티 CodeGroup 은 description 만 매핑하고 있었다.
--   ddl-auto=none 이라 Hibernate 가 이 불일치를 기동 시점에 잡아주지도 않는다.
--
-- 결정: 사전(설명 = DSCR)을 따라 dscr 로 통일한다. 같은 스키마의
--       inv_hld.rsn_dscr · inv_hld_rlz_acrst.rsn_dscr 와도 표기가 맞는다.
--       schema.sql · CodeGroup · CodeGroupResponse · CodeGroupSaveRequest ·
--       codeApi.js · CodeMaster.jsx 를 함께 고쳤다.
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — 어느 이름으로 되어 있든 dscr 로 수렴한다
-- =====================================================================
DO $mig$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'code_group' AND column_name = 'description') THEN

        IF EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'code_group' AND column_name = 'dscr') THEN
            -- 둘 다 있는 경우: catchup 이 dscr 을 만든 뒤 schema.sql 을 덧씌운 DB.
            -- description 쪽이 비어 있을 때만 안전하게 버린다.
            IF EXISTS (SELECT 1 FROM code_group WHERE description IS NOT NULL) THEN
                RAISE EXCEPTION 'code_group 에 description·dscr 이 모두 있고 description 에 값이 있다. 수동 병합이 필요하다';
            END IF;
            ALTER TABLE code_group DROP COLUMN description;
            RAISE NOTICE 'code_group.description 제거 (dscr 이 이미 있고 description 은 비어 있었다)';
        ELSE
            ALTER TABLE code_group RENAME COLUMN description TO dscr;
            RAISE NOTICE 'code_group.description → dscr 개명';
        END IF;
    ELSE
        RAISE NOTICE 'code_group.description 없음 — 이미 dscr 이다 (건너뜀)';
    END IF;

    COMMENT ON COLUMN code_group.dscr IS '그룹 설명. 이 그룹을 어느 컬럼이 참조하는지와, ref1~ref3에 무엇을 담았는지를 적어 둔다';
END $mig$;
