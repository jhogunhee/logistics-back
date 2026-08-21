-- =====================================================================
-- pikng_task 주석 갱신 — 「발행 후 재할당이 막힌다」 시절에 멈춰 있던 세 자리를 고친다.
--
-- 배경: 발행된(ISSUED) 웨이브의 재할당과 추가 발행이 열렸다. 그전에는 지시 단위 취소 뒤
--   할당을 해제한 주문이 CREATED 인 채로 ISSUED 웨이브에 갇혀 어느 문으로도 나가지
--   못했고, 결품 종결이 사후에 키운 미할당 잔량도 채울 창구가 없었다.
--
--   늘어난 몫은 언제나 「새 할당 행 + 새 지시 행」으로 간다 — 차수 컬럼을 두지 않는
--   기존 결정 그대로다. 그래서 이 증분에 구조 변경은 없고 주석만 바뀐다.
--
-- 무엇이 바뀌나: 주석 셋뿐이다. 컬럼·제약·데이터는 건드리지 않는다.
--   (1) 테이블   — 최초 발행 외에 추가 발행이 생겼다
--   (2) drct_qty — 항등식이 유지되는 근거를 「재할당이 막혀서」에서
--                  「늘어난 몫이 새 할당·새 지시로 가서」로 정정
--   (3) srt_seq  — 추가 발행이 MAX+1 부터 이어붙이므로 웨이브 내 1..N 연속이 아니다
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

        COMMENT ON TABLE pikng_task IS '피킹 지시. putaway_task·inv_mov_task와 동등한 위치의 작업지시 문서 — 웨이브 발행 시 outb_alloc과 1:1로 생성된다(상품별 집약 없음). 등록은 예약을 만들지 않는다 — 예약은 할당이 이미 잡았고 실행(PICK)이 소진한다. 최초 발행은 웨이브 단위(웨이브가 발행 문서)이고 추가 발행은 나중에 붙은 할당만 낸다. 취소는 웨이브 단위와 지시 단위 둘 다 — 다른 것은 실적 판정 범위뿐이다';

        COMMENT ON COLUMN pikng_task.drct_qty IS '지시 수량 = 발행 시점의 aloc_qty. 발행 후 재할당이 열려 있지만 늘어난 몫은 새 할당 행 + 새 지시 행으로 가고(살아 있는 지시가 붙은 할당에는 합산하지 않는다) 해제는 살아 있는 지시가 있는 할당을 거부하므로 항등식 drct_qty = aloc_qty가 유지된다. 결품 종결은 이 값과 outb_alloc.aloc_qty를 같이 cmpl_qty까지 낮추므로 항등식이 그대로 성립한다';

        COMMENT ON COLUMN pikng_task.srt_seq IS '집품 순서. 발행 시점에 loc.pikng_prty → loc_cd → outb_alloc_id 순으로 고정한 스냅샷 — 추가 발행은 MAX+1부터 이어붙이고 취소된 지시도 자기 번호를 들고 남으므로 연속이 아니다. 작업 중 마스터가 바뀌어도 리스트 순서가 흔들리지 않는다';

        RAISE NOTICE 'pikng_task 주석 갱신 — 추가 발행 반영, 항등식 근거와 srt_seq 연속성 정정';
    ELSE
        RAISE NOTICE 'pikng_task 없음 — 건너뜀 (신규 DB는 schema.sql 이 이미 최신 주석으로 만든다)';
    END IF;
END $mig$;
