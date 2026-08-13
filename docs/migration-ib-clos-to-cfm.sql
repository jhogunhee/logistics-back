-- =====================================================================
-- 입고예정(ASN) 확정 시각 컬럼 개명 — ib_order.clos_dt → cfm_dt
--   전제: docs/migration-catchup-to-schema.sql 까지 적용된 DB
--         (closed_at → clos_dt 개명이 그 파일의 컬럼 개명 루프에서 끝나 있다).
--
--   배경 —
--   컬럼 이름은 「마감(CLOS)」인데 이 값이 가리키는 사건은 「입고확정」이다.
--   입고 흐름의 마지막 단계를 마감이 아니라 확정으로 재정의하기로 이미 결정했고
--   (docs/screen-list.html 「결정 변경」: "입고확정으로 재정의하고 흐름의 마지막
--   단계로 이동"), 응답 DTO와 화면은 그 방향으로 먼저 옮겨가 cfmDt · 「확정일시」로
--   내보내고 있었다. DB 컬럼과 엔티티 필드만 옛 이름으로 남아 있던 것을 맞춘다.
--
--   ▣ 이름은 사전대로 확정 CFM + 일시 DT 다.
--     docs/naming-dictionary.md 에 「확정 = CFM = Confirm」이 이미 있고 CFM 은 확정
--     단독 배정이라 충돌이 없다. 같은 형태의 개명을 OMS 에서 이미 한 번 했다 —
--     converted_at → cfm_dt (docs/migration-oms-ib-drop-cancelled.sql).
--
--   ▣ 이번엔 컬럼 이름만 바꾼다.
--     상태값 RECEIVED, IbOrder.close() 메서드,
--     POST /inbound/asns/{id}/close 엔드포인트는 그대로 둔다. 그것들은 상태 모델
--     변경(전량 검수 자동 전이 제거)과 미구현 「입고확정」 화면이 함께 와야 하는
--     별개 작업이고, screen-list.html 이 "의미가 달라 재작성 필요"로 이미 분리해 뒀다.
--     그래서 개명 후에도 RECEIVED 상태에서 cfm_dt 가 찍히는 조합이 당분간 남는다.
--     다만 <<화면 라벨은 「입고확정」으로 함께 옮겼다>> — 사이드바·라우트가 이미 그 말을 쓰고
--     있었는데 상태 뱃지만 「입고마감」이라 한 화면 안에서 두 이름이 부딪혔다. 라벨은 저장값이
--     아니라 바꿔도 데이터에 영향이 없다 (IbStatus.RECEIVED 의 label, badgeMeta.js).
--
--   ▣ 데이터는 건드리지 않는다. 이름만 바뀌므로 값·NULL 여부가 그대로다.
--
--   ▣ 전체가 DO 블록 하나다 (BEGIN;/COMMIT; 을 쓰지 않는다) — CLAUDE.md 규칙.
--     전 구간에 존재 확인이 걸려 있어 몇 번을 돌려도 안전하다.
--
--   실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--     - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
-- =====================================================================

DO $ib_cfm$
DECLARE
    n int;
BEGIN
    -- 1. 컬럼 개명 -------------------------------------------------------
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'ib_order' AND column_name = 'clos_dt') THEN

        -- 개명 전에 목적지가 비어 있는지 본다. 둘 다 있으면 손으로 정리해야 한다 —
        -- 어느 쪽이 최신인지 스크립트가 판단할 수 없다.
        IF EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'ib_order' AND column_name = 'cfm_dt') THEN
            RAISE EXCEPTION 'ib_order 에 clos_dt 와 cfm_dt 가 함께 있다 — 어느 쪽이 유효한지 확인 후 수동 정리할 것';
        END IF;

        ALTER TABLE ib_order RENAME COLUMN clos_dt TO cfm_dt;
        RAISE NOTICE 'ib_order.clos_dt → cfm_dt';
    ELSE
        RAISE NOTICE 'ib_order.cfm_dt 이미 적용됨 — 건너뜀';
    END IF;

    -- 2. 주석 재설정 -----------------------------------------------------
    COMMENT ON COLUMN ib_order.cfm_dt IS '입고확정 시각 — 미입고 잔량이 확정되어 「얼마나 왔나」가 더는 바뀌지 않는 시점. 지금은 명시적 마감과 전량 검수 자동 전이 양쪽이 채운다(RECEIVED 진입 시각과 같다). oms_ib_order.cfm_dt(발주 확정 = ASN 생성)와는 다른 사건이다';

    -- 3. 검증 -----------------------------------------------------------
    -- 확정 시각은 RECEIVED 진입이 채우므로, RECEIVED · COMPLETED 인데 비어 있으면
    -- 개명 이전에 이미 어긋나 있던 행이다 (이 스크립트가 만든 문제가 아니다).
    SELECT count(*) INTO n FROM ib_order
     WHERE status IN ('RECEIVED', 'COMPLETED') AND cfm_dt IS NULL;
    IF n > 0 THEN
        RAISE NOTICE '주의 — 확정 이후 상태인데 확정시각이 비어 있는 입고건이 % 건 있다', n;
    END IF;

    -- 반대 방향: 아직 확정 전인데 시각이 찍혀 있으면 되돌림(reopen)이 빠진 흔적이다.
    SELECT count(*) INTO n FROM ib_order
     WHERE status IN ('SCHEDULED', 'RECEIVING') AND cfm_dt IS NOT NULL;
    IF n > 0 THEN
        RAISE NOTICE '주의 — 확정 전 상태인데 확정시각이 남아 있는 입고건이 % 건 있다', n;
    END IF;

    RAISE NOTICE '입고 확정시각 컬럼 개명 완료';
END
$ib_cfm$;

-- =====================================================================
-- 적용 후 확인
--   1) 컬럼 개명 확인 (cfm_dt 1건 / clos_dt 0건)
--      SELECT column_name FROM information_schema.columns
--       WHERE table_name = 'ib_order' AND column_name IN ('cfm_dt', 'clos_dt');
--   2) 값이 그대로 살아 있는지 (개명 전 건수와 같아야 한다)
--      SELECT status, count(*) FILTER (WHERE cfm_dt IS NOT NULL) AS cfm_dt_있음, count(*) AS 전체
--        FROM ib_order GROUP BY status ORDER BY status;
--   3) CLOS 를 쓰는 컬럼이 스키마에 남아 있지 않은지 (0건이어야 한다)
--      SELECT table_name, column_name FROM information_schema.columns
--       WHERE table_schema = 'public' AND column_name LIKE '%clos%';
-- =====================================================================
