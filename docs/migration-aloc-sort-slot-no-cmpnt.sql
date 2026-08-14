-- =====================================================================
-- 증분 마이그레이션: 할당 정렬 슬롯의 구현체 축 제거
--   대상: docs/schema.sql 상태로 유지 중인 라이브 DB
--
--   내용: ① aloc_stgy_slot의 정렬 슬롯(INVN_SRT·ODR_SRT) cmpnt_cd를 NULL로 비움
--         ② ck_aloc_slot_cmpnt를 「구현체 축이 있는 슬롯은 RSTRCT·DSTRB뿐」으로 교체
--         ③ stgy_exec_log.trgr_typ 주석 동기화 (PREVIEW가 실제로 기록되기 시작했다)
--
--   왜: 정렬 슬롯의 구현체는 'MULTI_SORT' 하나뿐이었다. 고를 것이 없는 축은 정의를
--       설명하지 않고 자리만 차지한다 — 정렬의 정의는 처음부터 para.criteria 목록이었고,
--       구현체 칸은 화면에 「선택지 1개짜리 피커」를, 코드에 상수 1개짜리 enum을 남겼다.
--       재고위치 슬롯(INVN_FLTR)이 이미 구현체 없이 조건만으로 정의되는 것과 같은 형태로 맞춘다.
--
--   para는 건드리지 않는다 — criteria 목록이 곧 정렬의 정의이므로 그대로 살아야 한다.
--   저장 서비스는 남아 있는 'MULTI_SORT' 값을 거부하지 않고 버리므로, 이 스크립트 적용 전에
--   저장이 일어나도 오류가 나지 않는다(다만 CHECK 교체 전에는 NULL 저장이 막히니 함께 적용한다).
--
--   재실행 안전: 갱신은 이미 NULL인 행을 건드리지 않고, CHECK는 DROP IF EXISTS 후 재생성한다.
--   BEGIN/COMMIT 없이 DO 블록 하나다 — DBeaver에서 실패 시 죽은 트랜잭션(25P02)이
--   연결에 남지 않게 하기 위함 (CLAUDE.md 「데이터베이스」).
--
--   stgy_rvsn의 옛 스냅샷은 손대지 않는다. 리비전은 「그때 저장된 정의」를 그대로 보여주는
--   조회 전용 감사 이력이라 지금 형태로 고쳐 쓰면 그 시점의 사실이 아니게 된다.
-- =====================================================================

DO $mig$
BEGIN

    -- 1. CHECK를 먼저 떼어낸다 — 걸린 채로는 NULL 갱신이 막힌다
    ALTER TABLE aloc_stgy_slot DROP CONSTRAINT IF EXISTS ck_aloc_slot_cmpnt;

    -- 2. 정렬 슬롯의 구현체 코드 제거
    UPDATE aloc_stgy_slot
       SET cmpnt_cd = NULL
     WHERE slot_typ IN ('INVN_SRT', 'ODR_SRT')
       AND cmpnt_cd IS NOT NULL;

    -- 3. 새 CHECK
    ALTER TABLE aloc_stgy_slot ADD CONSTRAINT ck_aloc_slot_cmpnt CHECK (
        (slot_typ IN ('INVN_FLTR','INVN_SRT','ODR_SRT') AND cmpnt_cd IS NULL)
     OR (slot_typ IN ('RSTRCT','DSTRB')                 AND cmpnt_cd IS NOT NULL)
    );

    -- 4. 컬럼 주석 동기화 (schema.sql이 주인)
    COMMENT ON COLUMN aloc_stgy_slot.cmpnt_cd IS '구현체 code (enum name — SHELF_LIFE_PCT · SEQUENTIAL · RATIO · EQUAL). CHECK 없음: 구현체 추가로 DDL을 고치지 않기 위함이고 존재 검증은 저장 서비스가 한다. 구현체 축이 없는 INVN_FLTR·INVN_SRT·ODR_SRT는 NULL';
    COMMENT ON COLUMN aloc_stgy_slot.para     IS '슬롯 파라미터. 정렬 슬롯(INVN_SRT·ODR_SRT)은 {"criteria":[{"field","dir"},…]}, SHELF_LIFE_PCT는 {"basis":"STORE"} 또는 {"basis":"FIXED","minPct":40}';

    -- 5. 실행 로그 트리거 주석 — PREVIEW가 「미기록」이 아니게 됐다. CHECK는 이미 PREVIEW를
    --    허용하고 있어 손댈 것이 없다 (ck_stgy_exec_log_trgr)
    COMMENT ON COLUMN stgy_exec_log.trgr_typ IS 'MANUAL 화면 조작 / AUTO 자동 실행(2차 웨이브용 선반영) / PREVIEW 결과를 반영하지 않은 산정 — 적치 일괄 추천만 기록한다(지시 생성 경로가 산정을 다시 돌리지 않아 근거가 거기밖에 없다). 조회 기본값은 MANUAL·AUTO';

END
$mig$;
