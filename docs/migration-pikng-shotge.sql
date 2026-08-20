-- =====================================================================
-- 피킹 결품 종결 — pikng_task 에 결품사유 2컬럼 + 공통코드 SHOTGE_RSN 신설.
--
-- 배경: 지시 30 · 집품 25 · 나머지 5는 끝내 안 나오는 상황에서 문서를 닫을 방법이 없었다.
--   지시는 cmpl != drct 라 DONE 이 못 되고, 실적이 있어 취소도 안 되고, 할당해제는
--   pikng_qty = 0 만 열리고, 재고조사는 예약을 먼저 풀라 하고, 주문은 전 할당이 소진되지
--   않아 PICKED 가 못 된다 — 실물 없는 예약이 영구히 남아 다른 주문도 그 재고를 못 썼다.
--
-- 해법: 「지시수량을 실적수량까지 낮춰 닫고, 그 잔량만큼 예약을 푼다」 =
--   inv_mov_task.cancelRemainder() 의 부분확정 분기와 같은 조작을 피킹에도 둔다.
--   낮춘 잔량이 곧 결품이고, 근거를 남길 자리가 아래 두 컬럼이다.
--   장부에만 남은 수량(on_hand)은 여기서 건드리지 않는다 — 장부를 줄이는 경로는 재고조사 하나.
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — 존재 확인을 걸어 이미 적용된 항목은 건너뛴다
-- =====================================================================
DO $mig$
BEGIN
    -- 1. 결품사유 컬럼 ------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'pikng_task' AND column_name = 'shotge_qty') THEN
        ALTER TABLE pikng_task ADD COLUMN shotge_qty      BIGINT;
        ALTER TABLE pikng_task ADD COLUMN shotge_rsn_cd   VARCHAR(10);
        ALTER TABLE pikng_task ADD COLUMN shotge_rsn_dscr VARCHAR(200);
        ALTER TABLE pikng_task ADD CONSTRAINT ck_pikng_task_shotge
            CHECK ((shotge_qty IS NULL) = (shotge_rsn_cd IS NULL) AND (shotge_qty IS NULL OR shotge_qty > 0));

        COMMENT ON COLUMN pikng_task.shotge_qty      IS '결품 수량 — 결품 종결이 포기한 잔량. 종결이 drct_qty를 cmpl_qty까지 낮추므로 종결 후에는 원래 지시수량이 남지 않아 파생시킬 수 없다. DONE 이후 값이 바뀌지 않는 사실 컬럼이다';
        COMMENT ON COLUMN pikng_task.shotge_rsn_cd   IS '결품 사유 코드 (공통코드 SHOTGE_RSN). 결품 종결로 닫힌 지시에만 채워진다 — 전량 집품으로 DONE이 된 지시는 NULL이라 이 컬럼의 유무가 곧 결품 여부다';
        COMMENT ON COLUMN pikng_task.shotge_rsn_dscr IS '기타 결품사유 텍스트. shotge_rsn_cd = ETC일 때만 사용 (inv_hld.rsn_dscr과 같은 규칙)';
        RAISE NOTICE 'pikng_task.shotge_qty · shotge_rsn_cd · shotge_rsn_dscr 추가';
    ELSE
        RAISE NOTICE 'pikng_task 결품 컬럼 이미 존재 — 건너뜀';
    END IF;

    -- 2. 결품사유 공통코드 --------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM code_group WHERE grp_cd = 'SHOTGE_RSN') THEN
        INSERT INTO code_group (grp_cd, grp_nm, dscr)
        VALUES ('SHOTGE_RSN', '결품사유', '피킹 결품 종결 사유 (pikng_task.shotge_rsn_cd). ETC(기타)일 때만 자유 텍스트 shotge_rsn_dscr를 받는다');
        INSERT INTO code_detail (grp_cd, code_cd, code_nm, srt_seq) VALUES ('SHOTGE_RSN', 'NOSTOCK', '재고없음', 1);
        INSERT INTO code_detail (grp_cd, code_cd, code_nm, srt_seq) VALUES ('SHOTGE_RSN', 'DAMG', '파손', 2);
        INSERT INTO code_detail (grp_cd, code_cd, code_nm, srt_seq) VALUES ('SHOTGE_RSN', 'MISLOC', '위치오류', 3);
        INSERT INTO code_detail (grp_cd, code_cd, code_nm, srt_seq) VALUES ('SHOTGE_RSN', 'ETC', '기타', 4);
        RAISE NOTICE '공통코드 SHOTGE_RSN 시드';
    ELSE
        RAISE NOTICE '공통코드 SHOTGE_RSN 이미 존재 — 건너뜀';
    END IF;
END
$mig$;

-- 확인:
--   SELECT column_name, data_type FROM information_schema.columns
--    WHERE table_name = 'pikng_task' AND column_name LIKE 'shotge%';
--   SELECT * FROM code_detail WHERE grp_cd = 'SHOTGE_RSN' ORDER BY srt_seq;
--
--   -- 결품으로 닫힌 지시 (종결이 drct_qty와 aloc_qty를 같이 낮추므로 대조로는 나오지 않는다)
--   SELECT t.pikng_task_id, t.drct_qty, t.cmpl_qty, t.shotge_qty, t.shotge_rsn_cd, t.shotge_rsn_dscr
--     FROM pikng_task t WHERE t.shotge_rsn_cd IS NOT NULL ORDER BY t.cmpl_dt DESC;
