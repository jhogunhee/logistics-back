-- =====================================================================
-- 재고 보류 — 같은 재고 행에 같은 사유의 미해제 보류를 여러 건 허용.
-- 부분 UNIQUE 인덱스(uq_inv_hld_open_rsn)를 같은 컬럼의 일반 부분 인덱스로 교체한다.
--
-- 현재 라이브 상태(migration-inv-hld.sql 적용) → schema.sql 상태.
-- 근거: docs/design.md 「재고 보류」 (2026-08-09 확정 — 초기 「동일 사유 미해제 중복 차단」 번복).
--   - 그 규칙엔 기간 개념이 없어, 같은 Lot이 다른 날 같은 사유로 또 걸리는 정당한 업무를
--     해제 전까지 영구히 막았다. 우회하려면 일어나지도 않은 해제를 실적에 남겨야 했다.
--   - 기존 건에 합산하지 않는다 — 보류 건은 보류번호·사유 텍스트·등록 시각·부분해제 이력을
--     각자 갖는 독립 단위라, 합치면 그게 첫 건에 흡수된다(outb_alloc 같은 연결 행이 아니다).
--   - 차단이 막아주던 중복 제출 실수는 해제(사유: 오등록)로 되돌린다 — 등록 즉시 발효라
--     취소 구간이 없어서 만들어둔 경로가 이미 그것이다.
--   - 항등식(inv.hld_qty = SUM(HELD 건의 hld_qty - rlz_qty))은 행이 몇 개든 그대로 성립한다.
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — 존재 확인을 걸어 이미 적용된 항목은 건너뛴다
-- =====================================================================
DO $mig$
BEGIN
    -- 1. 동일 사유 중복 차단 인덱스 제거 -------------------------------------
    IF to_regclass('uq_inv_hld_open_rsn') IS NOT NULL THEN
        DROP INDEX uq_inv_hld_open_rsn;
        RAISE NOTICE 'uq_inv_hld_open_rsn 제거 — 동일 사유 미해제 중복 허용';
    ELSE
        RAISE NOTICE 'uq_inv_hld_open_rsn 이미 없음 — 건너뜀';
    END IF;

    -- 2. 항등식 대사 · 미해제 보류 조회용 일반 부분 인덱스 -------------------
    IF to_regclass('ix_inv_hld_open') IS NULL THEN
        CREATE INDEX ix_inv_hld_open ON inv_hld (prod_id, loc_id, lot_id) WHERE status = 'HELD';
        RAISE NOTICE 'ix_inv_hld_open 생성';
    ELSE
        RAISE NOTICE 'ix_inv_hld_open 이미 존재 — 건너뜀';
    END IF;
END
$mig$;

-- 확인:
--   SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'inv_hld' ORDER BY indexname;
--   -- 항등식 대사 (차이 행이 0건이어야 한다)
--   SELECT i.prod_id, i.loc_id, i.lot_id, i.hld_qty, COALESCE(SUM(h.hld_qty - h.rlz_qty), 0) AS hld_sum
--     FROM inv i
--     LEFT JOIN inv_hld h ON h.prod_id = i.prod_id AND h.loc_id = i.loc_id AND h.lot_id = i.lot_id
--                        AND h.status = 'HELD'
--    GROUP BY i.prod_id, i.loc_id, i.lot_id, i.hld_qty
--   HAVING i.hld_qty <> COALESCE(SUM(h.hld_qty - h.rlz_qty), 0);
