-- =====================================================================
-- wav_stgy.cond_grp 주석 갱신 — 조건 필드에 STORE_GRP · STORE_TYP 를 더한다.
--
-- 배경: migration-wav-stgy-comment.sql 이 이 주석을 「OUTB_TYP · VHCL_FLTNO 둘」로
--   맞춰 놓았는데, 그 뒤 점포 마스터에 그룹·유형이 신설되면서(migration-add-store-grp-typ.sql)
--   WaveOrderField enum 에 STORE_GRP(납품처그룹) · STORE_TYP(납품처유형)이 더해졌다.
--   주석은 따라가지 않아 지금은 넷 중 둘만 적혀 있다.
--
--   앞선 증분이 고친 드리프트가 방향만 바뀌어 재발한 셈이다 — 그때는 「없는 필드를
--   안내」였고 지금은 「있는 필드를 은폐」다. 결과는 같다: DB만 보고 판단하는 사람이
--   조건그룹에 무엇을 넣을 수 있는지 틀리게 안다.
--
--   migration-wav-stgy-comment.sql 은 「그때 무엇을 적용했나」의 기록이라 고쳐 쓰지 않는다.
--   그 파일이 자기 자신에게 적용한 규칙을 그대로 따라 이 증분을 새로 쓴다.
--
-- 왜 주석 하나에 증분을 쓰나: 조건 필드는 코드 enum 이 소유하고 CHECK 로 표현되지 않아,
--   이 주석이 「이 JSONB 에 무엇을 넣을 수 있나」의 유일한 DB 쪽 안내이기 때문이다.
--
-- 무엇이 바뀌나: 주석 하나뿐이다. 컬럼·제약·데이터는 건드리지 않는다.
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — COMMENT 는 덮어쓰기이고, 테이블이 없으면 건너뛴다
-- =====================================================================

DO $mig$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name = 'wav_stgy') THEN

        COMMENT ON COLUMN wav_stgy.cond_grp IS '조건그룹 [[{fld,op,vals},…],…]. 그룹끼리 OR, 그룹 안 AND. 필드: WaveOrderField enum — OUTB_TYP 출고유형 · VHCL_FLTNO 차량편수 · STORE_GRP 납품처그룹 · STORE_TYP 납품처유형 (넷 다 값 목록의 주인은 공통코드다 — 뒤 둘은 store.store_grp·store_typ에서 읽는다)';

        RAISE NOTICE 'wav_stgy.cond_grp 주석 갱신 — 조건 필드 4종(OUTB_TYP·VHCL_FLTNO·STORE_GRP·STORE_TYP)으로 정정';
    ELSE
        RAISE NOTICE 'wav_stgy 없음 — 건너뜀 (신규 DB는 schema.sql 이 이미 최신 주석으로 만든다)';
    END IF;
END $mig$;
