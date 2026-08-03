-- =====================================================================
-- 웨이브 상태값을 RELEASED → ISSUED로, released_at → issued_dt로 개명.
--
-- 현재 라이브 상태(migration-outb-wave.sql 적용) → schema.sql 상태.
-- 근거 둘:
--   ① 웨이브는 할당의 단위가 아니라 피킹지시의 발행 단위다(design.md 「웨이브」절).
--      할당은 주문 단위 화면이 트리거하므로 「릴리즈(=일괄 할당)」라는 이름이 가리키는
--      동작 자체가 이 설계에 없다. 상태명·시각 컬럼명이 둘 다 그 이름을 쓰고 있었다.
--   ② RELEASED가 한 코드베이스에서 두 뜻으로 쓰인다 — inv_hld의 RELEASED는 보류 「해제」다.
--      사전에도 해제는 RLZ로 따로 있어(inv_hld_rlz_acrst) 발행에 그 토큰을 쓸 이유가 없다.
--   덤으로 _at → _dt 이탈도 함께 해소된다. 사전은 일자 DE / 일시 DT이고 _at 접미는
--   감사 컬럼 4종(created_at·updated_at) 전용이다.
--
-- 웨이브에 상태 전이를 일으키는 코드는 아직 없다(피킹지시 미구현) — 라이브 데이터는 전부
-- PLANNED일 것으로 보지만, 그렇지 않은 경우를 대비해 값 백필을 먼저 돌린 뒤 CHECK를 바꾼다.
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — 존재 확인을 걸어 이미 적용된 항목은 건너뛴다
-- =====================================================================
DO $mig$
DECLARE
    v_cnt BIGINT;
BEGIN
    -- 1) 시각 컬럼 개명 (released_at → issued_dt)
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'outb_wave' AND column_name = 'released_at'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
             WHERE table_name = 'outb_wave' AND column_name = 'issued_dt'
        ) THEN
            -- 둘 다 있는 상태는 개명이 중간에 끊긴 흔적이다. 값이 남아 있으면 옮기고 옛 컬럼을 버린다
            UPDATE outb_wave SET issued_dt = released_at WHERE issued_dt IS NULL AND released_at IS NOT NULL;
            ALTER TABLE outb_wave DROP COLUMN released_at;
            RAISE NOTICE 'outb_wave: released_at 값을 issued_dt로 옮기고 옛 컬럼 제거';
        ELSE
            ALTER TABLE outb_wave RENAME COLUMN released_at TO issued_dt;
            RAISE NOTICE 'outb_wave.released_at → issued_dt 개명';
        END IF;
    ELSE
        RAISE NOTICE 'outb_wave.released_at 없음 — 이미 개명됨, 건너뜀';
    END IF;

    -- 2) 상태값 백필 (RELEASED → ISSUED). CHECK를 바꾸기 전에 먼저 돌려야 한다
    UPDATE outb_wave SET status = 'ISSUED' WHERE status = 'RELEASED';
    GET DIAGNOSTICS v_cnt = ROW_COUNT;
    RAISE NOTICE 'outb_wave.status RELEASED → ISSUED 백필 %건', v_cnt;

    -- 3) CHECK 교체
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_outb_wave_status') THEN
        ALTER TABLE outb_wave DROP CONSTRAINT ck_outb_wave_status;
    END IF;
    ALTER TABLE outb_wave ADD CONSTRAINT ck_outb_wave_status
        CHECK (status IN ('PLANNED', 'ISSUED'));
    RAISE NOTICE 'ck_outb_wave_status CHECK 재생성 (PLANNED, ISSUED)';

    -- 4) 코멘트 — 낡은 「할당의 단위 / 릴리즈」 서술까지 함께 정정한다
    COMMENT ON TABLE  outb_wave IS '출고 웨이브. 피킹지시의 발행 단위 — 여러 주문의 집품을 한 번에 지시하기 위한 그룹. 발행 이후 진행(피킹/확정)은 주문 단위라 웨이브는 여기서 역할이 끝난다. 할당은 웨이브가 아니라 주문 단위다';
    COMMENT ON COLUMN outb_wave.status    IS 'PLANNED 편성중(주문 담기 가능) / ISSUED 피킹지시 발행 완료. RELEASED를 쓰지 않는 것은 inv_hld의 RELEASED(보류 해제)와 한 토큰이 두 뜻이 되기 때문';
    COMMENT ON COLUMN outb_wave.issued_dt IS '피킹지시 발행 시각. 미발행이면 NULL';
    COMMENT ON COLUMN outb_order.wav_id   IS '편성된 출고 웨이브. NULL = 아직 미편성. 주문은 웨이브에 편성돼야 피킹지시를 받는다(주문 1건짜리 웨이브도 허용)';
    RAISE NOTICE 'outb_wave · outb_order.wav_id 코멘트 갱신';
END
$mig$;

-- 확인:
--   SELECT status, count(*) FROM outb_wave GROUP BY status;
--   SELECT column_name FROM information_schema.columns
--    WHERE table_name = 'outb_wave' ORDER BY ordinal_position;
