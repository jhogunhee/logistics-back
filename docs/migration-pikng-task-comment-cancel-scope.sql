-- =====================================================================
-- pikng_task 주석 갱신 — 「취소는 웨이브 단위」 시절에 멈춰 있던 세 자리를 고친다.
--
-- 배경: migration-add-pikng-task.sql 이 이 테이블을 만들 당시 지시취소는 웨이브 단위
--   하나뿐이었다. 그 뒤 지시 단위 취소가 더해지면서(실적이 섞인 웨이브에서 한 개도
--   못 집은 지시를 닫을 문이 없었다) 취소 단위가 둘이 됐는데, 주석은 따라가지 않았다.
--
--   방치 비용이 특히 큰 자리들이다. 취소 단위·항등식 유지 근거·상태값의 뜻은
--   CHECK 로 표현되지 않아 이 주석이 DB 쪽의 유일한 안내이고, 「낡은 주석을 근거로
--   설계했다」가 바로 지시 단위 취소를 낳은 결함의 원인이었다.
--
--   migration-add-pikng-task.sql 은 「그때 무엇을 적용했나」의 기록이라 고쳐 쓰지 않는다.
--   그 규칙을 그대로 따라 이 증분을 새로 쓴다.
--
-- 무엇이 바뀌나: 주석 셋뿐이다. 컬럼·제약·데이터는 건드리지 않는다.
--   ① 테이블      — 발행은 웨이브 단위, 취소는 웨이브·지시 두 단위
--   ② drct_qty    — 항등식이 유지되는 근거를 「해제도 막힌다」에서 「해제는 살아 있는
--                    지시가 있는 할당을 거부한다」로 정정 (해제 판정은 할당 단위다)
--   ③ status      — CANCELLED 의 조건을 「그 지시 자신의 cmpl_qty = 0」으로 정정
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — COMMENT 는 덮어쓰기이고, 테이블이 없으면 건너뛴다
-- =====================================================================

DO $mig$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name = 'pikng_task') THEN

        COMMENT ON TABLE pikng_task IS '피킹 지시. putaway_task·inv_mov_task와 동등한 위치의 작업지시 문서 — 웨이브 발행 시 outb_alloc과 1:1로 생성된다(상품별 집약 없음). 등록은 예약을 만들지 않는다 — 예약은 할당이 이미 잡았고 실행(PICK)이 소진한다. 발행은 웨이브 단위(웨이브가 발행 문서)이고, 취소는 웨이브 단위와 지시 단위 둘 다 — 다른 것은 실적 판정 범위뿐이다';

        COMMENT ON COLUMN pikng_task.drct_qty IS '지시 수량 = 발행 시점의 aloc_qty. 발행 후 재할당이 막히고 해제는 살아 있는 지시가 있는 할당을 거부하므로 항등식 drct_qty = aloc_qty가 유지된다. 결품 종결은 이 값과 outb_alloc.aloc_qty를 같이 cmpl_qty까지 낮추므로 항등식이 그대로 성립한다';

        COMMENT ON COLUMN pikng_task.status IS 'DIRECTED 지시(부분 실행 포함) / DONE 완료 — 전량 집품 또는 결품 종결(shotge_rsn_cd로 구분) / CANCELLED 취소(웨이브 단위·지시 단위 둘 다, 그 지시 자신의 cmpl_qty=0일 때만). 「진행」 같은 부분 상태는 두지 않는다 — 진행도는 수량 파생';

        RAISE NOTICE 'pikng_task 주석 갱신 — 취소 단위 2종(웨이브·지시) 반영, 항등식 근거와 CANCELLED 조건 정정';
    ELSE
        RAISE NOTICE 'pikng_task 없음 — 건너뜀 (신규 DB는 schema.sql 이 이미 최신 주석으로 만든다)';
    END IF;
END $mig$;
