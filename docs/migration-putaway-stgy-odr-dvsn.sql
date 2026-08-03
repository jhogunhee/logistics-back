-- =====================================================================
-- 증분 마이그레이션: 적치 전략 적용대상을 발주구분(odr_dvsn) 선택으로 교체
--   대상: migration-add-loc-ptawy-prty.sql 까지 적용된 라이브 DB → docs/schema.sql 상태
--   근거 (2026-08-03 확정) —
--     · 적용대상(tgt_cond 범용 조건)·우선순위(prty) 제거. 적용대상을 발주구분(공통코드
--       ODR_DVSN) 단일 선택으로 좁히고, 유형당 전략 1개(UNIQUE)가 우선순위를 대체.
--     · ib_order에 odr_dvsn 신설 — 확정 시 oms_ib_order에서 복사(wmsback→omsback import 금지라
--       조회 대신 복사). 기존 행은 여기서 backfill.
--     · ptawy_stgy_stg.loc_cond는 "적치위치 지정(BIZ_DVSN IN 최대 1건)"으로 의미가 좁아짐
--       (컬럼 구조는 그대로, 검증은 서비스가 담당). 기존에 존·로케이션코드 조건을 저장한 단계가
--       있으면 지정을 비운다 — 저장 검증이 거부하는 형태를 실행이 계속 쓰지 않게 하기 위함이며,
--       비워진 지정은 "전체 보관 로케이션"으로 동작한다 (원본은 stgy_rvsn 스냅샷에 남아 있음).
--
--   ※ 적용 순서: 이 증분을 적용한 뒤 백엔드를 재시작할 것 — IbOrder·PtawyStgy 엔티티가
--     새 컬럼을 매핑하므로 컬럼 없는 DB에서는 조회가 실패한다 (ddl-auto=none).
--   ※ ptawy_stgy에 전략이 2건 이상이면 유형당 1개 UNIQUE를 자동으로 만들 수 없어
--     명시적으로 실패한다 — 남길 전략을 정해 나머지를 삭제한 뒤 재실행할 것.
--
--   재실행 안전: IF NOT EXISTS / 존재 확인. BEGIN/COMMIT 없이 DO 블록 하나 (DBeaver 25P02 방지).
-- =====================================================================

DO $mig$
BEGIN

    -- ① ib_order.odr_dvsn 신설 + 기존 행 backfill (주문 원장의 발주구분을 복사)
    ALTER TABLE ib_order ADD COLUMN IF NOT EXISTS odr_dvsn VARCHAR(10);

    UPDATE ib_order i
       SET odr_dvsn = COALESCE(o.odr_dvsn, 'NRML')
      FROM oms_ib_order o
     WHERE o.oms_ib_order_id = i.oms_ib_order_id
       AND i.odr_dvsn IS NULL;

    -- 주문 원장 행이 없는 느슨한 참조 잔재까지 커버 (정상 데이터라면 위 UPDATE가 전부 채운다)
    UPDATE ib_order SET odr_dvsn = 'NRML' WHERE odr_dvsn IS NULL;

    ALTER TABLE ib_order ALTER COLUMN odr_dvsn SET DEFAULT 'NRML';
    ALTER TABLE ib_order ALTER COLUMN odr_dvsn SET NOT NULL;

    COMMENT ON COLUMN ib_order.odr_dvsn IS '발주구분 (공통코드 ODR_DVSN: NRML/URGT/RTNGS). 확정 시 oms_ib_order.odr_dvsn에서 복사 — wmsback이 omsback을 import할 수 없어 조회 대신 복사. 적치 전략 선택의 기준';

    -- ② ptawy_stgy: 전략 2건 이상이면 수동 정리 후 재실행 (어느 것을 남길지는 사람이 정한다)
    IF (SELECT COUNT(*) FROM ptawy_stgy) > 1 THEN
        RAISE EXCEPTION '적치 전략이 %건입니다 — 유형당 1개 체계로 전환하려면 남길 전략 1건만 두고 삭제한 뒤 재실행하세요.',
            (SELECT COUNT(*) FROM ptawy_stgy);
    END IF;

    -- 적용대상 컬럼 교체: odr_dvsn 신설(기존 전략은 NULL = 전체), prty·tgt_cond 제거
    -- (기존 tgt_cond 내용은 stgy_rvsn 스냅샷에 남는다 — 필요하면 단계 조건으로 수동 이관)
    ALTER TABLE ptawy_stgy ADD COLUMN IF NOT EXISTS odr_dvsn VARCHAR(10);
    ALTER TABLE ptawy_stgy DROP CONSTRAINT IF EXISTS ck_ptawy_stgy_prty;
    ALTER TABLE ptawy_stgy DROP CONSTRAINT IF EXISTS ck_ptawy_stgy_tgt_cond;
    ALTER TABLE ptawy_stgy DROP COLUMN IF EXISTS prty;
    ALTER TABLE ptawy_stgy DROP COLUMN IF EXISTS tgt_cond;

    CREATE UNIQUE INDEX IF NOT EXISTS ux_ptawy_stgy_odr_dvsn ON ptawy_stgy (COALESCE(odr_dvsn, 'ALL'));

    COMMENT ON TABLE  ptawy_stgy IS '적치 전략 헤더. 선택 기준은 발주구분 하나(유형당 1개 — ux_ptawy_stgy_odr_dvsn). 1차에서 전략은 추천만 한다 — 실행은 기존 즉시 MOVE 흐름 유지';
    COMMENT ON COLUMN ptawy_stgy.odr_dvsn IS '적용대상 발주구분 (공통코드 ODR_DVSN — NRML/URGT). NULL = 전체. 반품(RTNGS)은 스코프 아웃이라 저장 검증이 거부한다';

    -- ③ 단계의 loc_cond: 새 의미(BIZ_DVSN IN 지정)에 맞지 않는 기존 값은 비운다 → 전체 보관으로 동작
    UPDATE ptawy_stgy_stg
       SET loc_cond = '[]'::jsonb
     WHERE loc_cond <> '[]'::jsonb
       AND NOT (
           jsonb_array_length(loc_cond) = 1
           AND loc_cond -> 0 ->> 'fld' = 'BIZ_DVSN'
           AND loc_cond -> 0 ->> 'op'  = 'IN'
       );

    COMMENT ON COLUMN ptawy_stgy_stg.line_cond IS '조건 — 이 조건일 때만 이 단계를 적용 [{fld,op,vals}]. 빈 배열 = 항상 시도';
    COMMENT ON COLUMN ptawy_stgy_stg.loc_cond  IS '적치위치 지정 — 존 업무유형 IN 최대 1건 [{"fld":"BIZ_DVSN","op":"IN","vals":[...]}]. 조건이 아니라 적용기준값(여기에 둔다). 빈 배열 = 전체 보관 로케이션. 온도대 일치 + STORAGE는 불변 전제라 저장하지 않는다';

END
$mig$;
