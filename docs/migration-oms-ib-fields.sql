-- =====================================================================
-- 입고주문 헤더에 발주구분 · 담당자 · 비고 추가
--   전제: docs/migration-nbr.sql 까지 적용된 DB.
--
--   배경 —
--   입고주문 헤더가 벤더 · 예정일뿐이라 실제 발주서로 쓰기엔 칸이 모자랐다.
--   벤더에게 전달할 메모를 적을 곳이 없고, 누가 발주했는지도 남지 않는다
--   (감사 컬럼 created_by 는 인증이 없어 'admin' 고정이다).
--
--   ▣ 세 컬럼 모두 이름은 표준 단어 사전을 따른다 —
--     발주(ODR) + 구분(DVSN) → odr_dvsn, 담당자(PIC) → pic_nm, 비고(RMK) → rmk
--
--   ▣ odr_dvsn 은 공통코드 ODR_DVSN 을 문자열로 참조한다 (FK 없음, CHECK 없음).
--     값 목록의 주인이 사용자 편집 가능한 공통코드라 CHECK 를 걸면 코드를 늘릴 때마다
--     DDL 을 고쳐야 한다 — prod.inb_uom_cd · loc.zon_cd 와 같은 형태다.
--     기본값 NRML 은 컬럼 DEFAULT 가 가리키는 값이라 폐기하면 안 된다.
--
--   ▣ 지금은 표시·분류용이다. 긴급(URGT)이 적치·피킹 우선순위를 바꾸지는 않는다 —
--     그러려면 변환 시 ASN 까지 값을 넘겨야 하고, 그건 별개의 결정이다.
--
--   ▣ 전체가 DO 블록 하나다 (BEGIN;/COMMIT; 을 쓰지 않는다) — CLAUDE.md 규칙.
--     전 구간에 존재 확인이 걸려 있어 몇 번을 돌려도 안전하다.
--
--   실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--     - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
-- =====================================================================

DO $oms_fields$
DECLARE
    n int;
BEGIN
    -- 1. 공통코드 ODR_DVSN ----------------------------------------------
    INSERT INTO code_group (grp_cd, grp_nm, description)
    VALUES ('ODR_DVSN', '발주구분', '입고주문의 성격 (oms_ib_order.odr_dvsn)')
    ON CONFLICT (grp_cd) DO NOTHING;

    INSERT INTO code_detail (grp_cd, code_cd, code_nm, srt_seq) VALUES
        ('ODR_DVSN', 'NRML',  '정상',     1),
        ('ODR_DVSN', 'URGT',  '긴급',     2),
        ('ODR_DVSN', 'RTNGS', '반품입고', 3)
    ON CONFLICT (grp_cd, code_cd) DO NOTHING;

    RAISE NOTICE '공통코드 ODR_DVSN 그룹 + 3건 반영';

    -- 2. 컬럼 3종 -------------------------------------------------------
    -- odr_dvsn 은 NOT NULL 이라 백필을 먼저 한 뒤 제약을 건다.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'oms_ib_order' AND column_name = 'odr_dvsn') THEN
        ALTER TABLE oms_ib_order ADD COLUMN odr_dvsn VARCHAR(10);
        UPDATE oms_ib_order SET odr_dvsn = 'NRML' WHERE odr_dvsn IS NULL;
        ALTER TABLE oms_ib_order ALTER COLUMN odr_dvsn SET DEFAULT 'NRML';
        ALTER TABLE oms_ib_order ALTER COLUMN odr_dvsn SET NOT NULL;
        RAISE NOTICE 'oms_ib_order.odr_dvsn 추가 (기존 주문 전부 NRML 백필)';
    ELSE
        RAISE NOTICE 'oms_ib_order.odr_dvsn 이미 존재 — 건너뜀';
    END IF;

    -- 담당자·비고는 NULL 허용이다. 기존 주문에는 채울 값이 없고, 앞으로도 필수가 아니다.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'oms_ib_order' AND column_name = 'pic_nm') THEN
        ALTER TABLE oms_ib_order ADD COLUMN pic_nm VARCHAR(30);
        RAISE NOTICE 'oms_ib_order.pic_nm 추가';
    ELSE
        RAISE NOTICE 'oms_ib_order.pic_nm 이미 존재 — 건너뜀';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'oms_ib_order' AND column_name = 'rmk') THEN
        ALTER TABLE oms_ib_order ADD COLUMN rmk VARCHAR(200);
        RAISE NOTICE 'oms_ib_order.rmk 추가';
    ELSE
        RAISE NOTICE 'oms_ib_order.rmk 이미 존재 — 건너뜀';
    END IF;

    COMMENT ON COLUMN oms_ib_order.odr_dvsn IS '발주구분. 공통코드 ODR_DVSN (NRML 정상 / URGT 긴급 / RTNGS 반품입고). 지금은 표시·분류용이며 창고 작업 흐름을 바꾸지 않는다 — 긴급을 적치·피킹 우선순위에 반영하려면 그때 ASN까지 전달해야 한다';
    COMMENT ON COLUMN oms_ib_order.pic_nm   IS '발주 담당자명. 감사 컬럼 created_by(로그인 계정)와는 별개다 — 인증이 없어 created_by가 admin 고정이기도 하고, 대신 발주한 경우 실제 담당자가 다를 수 있다';
    COMMENT ON COLUMN oms_ib_order.rmk      IS '비고. 벤더 전달사항 등 자유 입력. 변환 시 ASN으로 넘기지 않는다 — 발주 시점의 메모라 창고 작업 지시와 성격이 다르다';

    -- 3. 검증 -----------------------------------------------------------
    SELECT count(*) INTO n
      FROM oms_ib_order o
     WHERE NOT EXISTS (SELECT 1 FROM code_detail c
                        WHERE c.grp_cd = 'ODR_DVSN' AND c.code_cd = o.odr_dvsn);
    IF n > 0 THEN
        RAISE EXCEPTION '공통코드 ODR_DVSN 에 없는 발주구분을 쓰는 주문이 % 건 있다', n;
    END IF;

    RAISE NOTICE '입고주문 헤더 항목 추가 완료';
END
$oms_fields$;

-- =====================================================================
-- 적용 후 확인
--   SELECT code_cd, code_nm, srt_seq FROM code_detail WHERE grp_cd = 'ODR_DVSN' ORDER BY srt_seq;
--   SELECT oms_ib_no, odr_dvsn, pic_nm, rmk FROM oms_ib_order ORDER BY oms_ib_no;
-- =====================================================================
