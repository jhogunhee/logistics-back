-- =====================================================================
-- 전략 커널·입고 수량 컬럼 주석 동기화 — 라이브 DB에 빠진 컬럼 주석 22개를 채운다.
--
-- 배경: 라이브 DB의 컬럼 주석을 전수 조회해 보니 292개가 비어 있었는데, 그중
--   216개는 의도된 것이고(감사 컬럼 4종 176개 + 자기 테이블 PK 40개),
--   54개는 FK·자명한 이름 컬럼이라 schema.sql도 주석을 달지 않는다.
--   남은 22개만 「schema.sql엔 있는데 DB엔 없는」 진짜 어긋남이고, 출처가 둘이다 —
--
--     (1) 전략 커널 6개 테이블의 컬럼 20개
--         migration-add-stgy-inspection-putaway.sql 이 테이블 주석 6개만 달고
--         컬럼 주석은 하나도 달지 않았다. schema.sql 은 그 뒤 전부 문서화했다.
--         → insp_plcy · insp_plcy_rule · ptawy_stgy · ptawy_stgy_stg
--           stgy_rvsn · stgy_exec_log
--
--     (2) ib_line 의 수량 컬럼 2개
--         migration-catchup-to-schema.sql 의 개명 루프가 ptwy_qty → ptawy_qty 식으로
--         이름만 바꿨다. 옛 이름 시절 주석이 없었으니 새 이름에도 없는 상태다.
--         → ib_line.rcvd_qty · ib_line.ptawy_qty
--
--   적용이 끝난 증분은 「그때 무엇을 적용했나」의 기록이라 고쳐 쓰지 않는다. 대신
--   「현재 라이브 상태 → schema.sql 상태」로 이 증분을 새로 쓴다
--   (migration-uom-comment-sync.sql · migration-outb-wave-comment.sql 과 같은 형태).
--
-- 무엇이 바뀌나: 컬럼 주석 22개뿐이다. 컬럼·제약·데이터·인덱스는 건드리지 않는다.
--   문구는 docs/schema.sql 에서 그대로 옮겼다 — 새로 지어낸 문장이 없다.
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — COMMENT 는 덮어쓰기이고, 테이블이 없으면 건너뛴다
-- =====================================================================
DO $mig$
DECLARE
    v_done int := 0;
BEGIN
    -- ---------------------------------------------------------------
    -- (1) 입고 라인 — 개명 루프가 이름만 바꾸고 지나간 수량 누계 둘
    -- ---------------------------------------------------------------
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name = 'ib_line') THEN

        COMMENT ON COLUMN ib_line.rcvd_qty  IS '검수(개수 확인) 완료된 실제 입고(스테이징 입) 수량 누계. 실무 검수는 개수 대조 수준이라 불합격 수량은 관리하지 않는다';
        COMMENT ON COLUMN ib_line.ptawy_qty IS '적치 완료 수량 누계 (스테이징 → 보관 MOVE 반영분)';

        v_done := v_done + 2;
    ELSE
        RAISE NOTICE 'ib_line 없음 — 건너뜀';
    END IF;

    -- ---------------------------------------------------------------
    -- (2) 전략 리비전 — 유형 공용 이력
    -- ---------------------------------------------------------------
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name = 'stgy_rvsn') THEN

        COMMENT ON COLUMN stgy_rvsn.stgy_typ IS 'INSP 검수 / PTAWY 적치 (2차: WAV 웨이브 / ALOC 할당)';
        COMMENT ON COLUMN stgy_rvsn.stgy_id  IS '유형별 헤더 PK (insp_plcy_id 또는 ptawy_stgy_id). FK 없음 — 원본 삭제 후에도 남는 느슨한 참조';
        COMMENT ON COLUMN stgy_rvsn.rvsn_no  IS '전략별 1부터 증가. 헤더 행 잠금 하에 last_rvsn_no+1로 부여, uq_stgy_rvsn이 최후 방어선';
        COMMENT ON COLUMN stgy_rvsn.snpsht   IS '정의 전체(헤더+하위 구성)의 JSON 직렬화. 직렬화 형태의 주인은 저장 서비스이고, 읽을 때도 같은 DTO로 역직렬화한다';

        v_done := v_done + 4;
    ELSE
        RAISE NOTICE 'stgy_rvsn 없음 — 건너뜀';
    END IF;

    -- ---------------------------------------------------------------
    -- (3) 전략 실행 로그 — 유형 공용
    -- ---------------------------------------------------------------
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name = 'stgy_exec_log') THEN

        COMMENT ON COLUMN stgy_exec_log.rvsn_no   IS '실행에 사용된 리비전. stgy_rvsn과 조합해 판정 당시의 정의를 재구성한다';
        COMMENT ON COLUMN stgy_exec_log.tgt_ref   IS '대상 문서 번호 (입고번호 IB-…). 가상 입력 미리보기는 NULL. inv_hist.rfn_doc_no와 같은 느슨한 참조 — 새 컬럼이라 ref 표기(사전 결정)';
        COMMENT ON COLUMN stgy_exec_log.rslt_smry IS '사람용 한 줄 요약. 예: "라인 3건 중 위반 1건"';
        COMMENT ON COLUMN stgy_exec_log.dcsn_trc  IS '건별 판정 상세(라인×규칙 / 단계×후보). 관리 화면의 "왜 차단/배정됐나" 표의 원본이고, 구조는 판정 결과 DTO가 정의한다';

        v_done := v_done + 4;
    ELSE
        RAISE NOTICE 'stgy_exec_log 없음 — 건너뜀';
    END IF;

    -- ---------------------------------------------------------------
    -- (4) 검수 정책 — 헤더와 규칙
    -- ---------------------------------------------------------------
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name = 'insp_plcy') THEN

        COMMENT ON COLUMN insp_plcy.stgy_nm      IS '정책명. 표시용 — 실행에 사용하지 않는다 (이름/동작 불일치는 미리보기가 보완)';
        COMMENT ON COLUMN insp_plcy.last_rvsn_no IS '마지막 저장 리비전 (stgy_rvsn.rvsn_no 최신값)';

        v_done := v_done + 2;
    ELSE
        RAISE NOTICE 'insp_plcy 없음 — 건너뜀';
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name = 'insp_plcy_rule') THEN

        COMMENT ON COLUMN insp_plcy_rule.insp_plcy_id IS '소속 정책. FK 없음 — 정책 삭제 시 규칙 정리는 서비스(cascade)가 한다';
        COMMENT ON COLUMN insp_plcy_rule.srt_seq      IS '평가·표시 순서. 전 규칙이 실행되므로 결과에는 영향 없고 위반 목록 정렬에 쓰인다';
        COMMENT ON COLUMN insp_plcy_rule.rule_cd      IS '코드 레지스트리의 규칙 code (1차: LOT_DATE_REVERSE 역순제한 / SHELF_LIFE_PCT 유통기한 잔여비율). CHECK 없음 — 값 목록의 주인은 코드 레지스트리';
        COMMENT ON COLUMN insp_plcy_rule.para         IS '규칙 파라미터. 저장 시 ParamSpec으로 검증된 값만 담는다. 예: {"minPercent": 30}, {"excludeSameDay": true}';

        v_done := v_done + 4;
    ELSE
        RAISE NOTICE 'insp_plcy_rule 없음 — 건너뜀';
    END IF;

    -- ---------------------------------------------------------------
    -- (5) 적치 전략 — 헤더와 단계
    -- ---------------------------------------------------------------
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name = 'ptawy_stgy') THEN

        COMMENT ON COLUMN ptawy_stgy.stgy_nm      IS '전략명. 표시용 — 실행에 사용하지 않는다';
        COMMENT ON COLUMN ptawy_stgy.unt_splt_yn  IS '입수 단위 배수 절사. 입수 = ea_qty(입고단위) — 재고가 낱개(EA)라 입고단위 낱개수량이 곧 배수다. true면 로케이션별 배정수량을 입수 배수로 내림(낱개 혼적 방지), 몫 0인 로케이션은 스킵';
        COMMENT ON COLUMN ptawy_stgy.last_rvsn_no IS '마지막 저장 리비전';

        v_done := v_done + 3;
    ELSE
        RAISE NOTICE 'ptawy_stgy 없음 — 건너뜀';
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name = 'ptawy_stgy_stg') THEN

        COMMENT ON COLUMN ptawy_stgy_stg.ptawy_stgy_id IS '소속 전략. FK 없음 — 삭제 연쇄는 서비스가 수행';
        COMMENT ON COLUMN ptawy_stgy_stg.srt_seq       IS '실행 순서 (화면 drag&drop 순서 그대로)';
        COMMENT ON COLUMN ptawy_stgy_stg.mthd_para     IS '방식 파라미터 (1차 방식들은 빈 객체 — 확장 대비)';

        v_done := v_done + 3;
    ELSE
        RAISE NOTICE 'ptawy_stgy_stg 없음 — 건너뜀';
    END IF;

    RAISE NOTICE '전략·입고 컬럼 주석 동기화 완료 — % 건 (schema.sql 기준으로 수렴)', v_done;
END $mig$;

-- 확인 (0건이면 완료 — 감사 컬럼·PK·FK는 원래 주석을 달지 않으므로 제외):
--   SELECT c.relname, a.attname
--     FROM pg_class c
--     JOIN pg_namespace n ON n.oid = c.relnamespace
--     JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
--    WHERE c.relkind = 'r' AND n.nspname = current_schema()
--      AND c.relname IN ('ib_line','insp_plcy','insp_plcy_rule','ptawy_stgy',
--                        'ptawy_stgy_stg','stgy_rvsn','stgy_exec_log')
--      AND col_description(c.oid, a.attnum) IS NULL
--      AND a.attname NOT IN ('created_at','created_by','updated_at','updated_by')
--      AND a.attname <> c.relname || '_id'
--    ORDER BY 1, a.attnum;
