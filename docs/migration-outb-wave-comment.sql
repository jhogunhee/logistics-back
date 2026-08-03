-- =====================================================================
-- outb_wave 주석 갱신 — 할당 진입 단위 번복 반영.
--
-- 배경: 라이브 DB의 outb_wave 테이블 주석이 「할당은 웨이브가 아니라 주문 단위다」로
--   남아 있다. migration-wave-issued.sql 이 그 문구로 적용했고, 그 뒤 할당을 구현하면서
--   진입 단위가 웨이브로 뒤집혔다(docs/design.md 「웨이브」 절의 번복 이력 참조).
--   docs/schema.sql 은 이미 고쳤으므로 라이브 DB만 어긋난 상태다.
--
--   적용이 끝난 증분(migration-wave-issued.sql)은 「그때 무엇을 적용했나」의 기록이라
--   고쳐 쓰지 않는다. 대신 「현재 라이브 상태 → schema.sql 상태」로 이 증분을 새로 쓴다.
--
-- 무엇이 바뀌나: 주석 두 개뿐이다. 컬럼·제약·데이터는 건드리지 않는다.
--   할당이 웨이브 단위로 실행되지만 웨이브 상태 기계는 PLANNED → ISSUED 둘 그대로이고
--   할당이 그것을 바꾸지 않는다는 점을 주석에 담는다 — 「릴리즈(=웨이브가 할당 상태를
--   갖는 것)」는 여전히 없고, 그래서 RELEASED → ISSUED 개명의 근거도 유효하다.
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — COMMENT 는 덮어쓰기이고, 테이블이 없으면 건너뛴다
-- =====================================================================
DO $mig$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name = 'outb_wave') THEN

        COMMENT ON TABLE outb_wave IS '출고 웨이브. 피킹지시의 발행 단위 — 여러 주문의 집품을 한 번에 지시하기 위한 그룹. 발행 이후 진행(피킹/확정)은 주문 단위라 웨이브는 여기서 역할이 끝난다. 할당도 이 단위로 실행하지만(2026-08-03 번복 — 피킹지시가 웨이브 단위라 할당만 주문 단위면 흐름 중간에서 단위가 어긋난다) 계산·결과는 라인 단위이고 웨이브 상태는 할당으로 바뀌지 않는다';

        -- status 주석은 문구가 그대로다. 「릴리즈」를 쓰지 않는 근거 둘(그 동작이 이 설계에
        -- 없다 / inv_hld 의 RELEASED 와 한 토큰이 두 뜻이 된다)이 번복 뒤에도 유효해서다.
        -- 다시 적어 두는 것은 라이브 DB가 어느 증분까지 적용됐든 같은 값으로 수렴시키기 위함이다.
        COMMENT ON COLUMN outb_wave.status IS 'PLANNED 편성중(주문 담기 가능) / ISSUED 피킹지시 발행 완료. RELEASED를 쓰지 않는 것은 inv_hld의 RELEASED(보류 해제)와 한 토큰이 두 뜻이 되기 때문';

        RAISE NOTICE 'outb_wave 주석 갱신 — 할당 진입 단위(웨이브) 반영';
    ELSE
        RAISE NOTICE 'outb_wave 없음 — 건너뜀 (신규 DB는 schema.sql 이 이미 최신 주석으로 만든다)';
    END IF;
END $mig$;
