-- =====================================================================
-- wav_stgy.cond_grp 주석 갱신 — 조건 필드 목록을 실제 구현에 맞춘다.
--
-- 배경: 라이브 DB의 wav_stgy.cond_grp 주석이 「필드: STORE 점포 · ODR_DE 주문일」로
--   남아 있다. migration-add-stgy-wave.sql 이 그 문구로 적용했는데, 실제 구현된
--   WaveOrderField enum 은 OUTB_TYP(출고유형) · VHCL_FLTNO(차량편수) 둘이다.
--   같은 증분이 그 두 컬럼(outb_order.outb_typ · vhcl_fltno)을 함께 만들었으니
--   주석만 옛 설계안의 필드명을 그대로 옮겨 적은 것이다.
--   docs/schema.sql 은 이미 고쳤으므로 라이브 DB만 어긋난 상태다.
--
--   적용이 끝난 증분(migration-add-stgy-wave.sql)은 「그때 무엇을 적용했나」의 기록이라
--   고쳐 쓰지 않는다. 대신 「현재 라이브 상태 → schema.sql 상태」로 이 증분을 새로 쓴다.
--   (migration-outb-wave-comment.sql 과 같은 형태다.)
--
-- 왜 주석 하나에 증분을 쓰나: 이 주석은 「이 JSONB 에 무엇을 넣을 수 있나」의 유일한
--   DB 쪽 안내다. 조건 필드는 코드 enum 이 소유하고 CHECK 로 표현되지 않으므로,
--   주석이 틀리면 DB만 보고 판단하는 사람에게 없는 필드를 알려주는 셈이 된다.
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

        COMMENT ON COLUMN wav_stgy.cond_grp IS '조건그룹 [[{fld,op,vals},…],…]. 그룹끼리 OR, 그룹 안 AND. 필드: WaveOrderField enum — OUTB_TYP 출고유형 · VHCL_FLTNO 차량편수 (둘 다 값 목록의 주인은 공통코드다)';

        RAISE NOTICE 'wav_stgy.cond_grp 주석 갱신 — 조건 필드를 OUTB_TYP·VHCL_FLTNO 로 정정';
    ELSE
        RAISE NOTICE 'wav_stgy 없음 — 건너뜀 (신규 DB는 schema.sql 이 이미 최신 주석으로 만든다)';
    END IF;
END $mig$;
