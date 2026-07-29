-- =====================================================================
-- 채번관리(nbr_rule/nbr_seq) 도입 — 기존 6개 시퀀스 채번을 이관한다.
--   전제: docs/migration-catchup-to-schema.sql 까지 적용된 DB.
--
--   대상: prod_cd_seq, vndr_cd_seq, oms_ib_no_seq, ib_no_seq, outb_no_seq, outb_wave_no_seq
--   (lot_no는 상품+입고일자 복합 리셋이라 이번 이관 대상이 아니다 — 지금 방식 유지)
--
--   ▣ 전체가 DO 블록 하나다 (BEGIN;/COMMIT; 을 쓰지 않는다) — CLAUDE.md 규칙.
--     전 구간에 존재 확인이 걸려 있어 몇 번을 돌려도 안전하다.
--
--   FK는 걸지 않는다. nbr_seq 시딩은 옛 시퀀스의 내부 상태(last_value)를 믿지 않고
--   실제 발급된 업무 데이터(prod_cd/vndr_cd/oms_ib_no/ib_no/outb_no/wav_no)에서
--   직접 최대값을 뽑는다 — 시퀀스의 is_called 여부 같은 곁가지를 신경 쓸 필요가 없다.
--
--   날짜가 들어가는 4개(OMS_IB_NO/IB_NO/OUTB_NO/OUTB_WAV_NO)는 "오늘 발급분"만이 아니라
--   기존 번호에 실제로 박혀 있는 날짜마다 각각 시딩한다 — oms_ib_no/ib_no는 예정일,
--   outb_no는 주문일 기준이라 아직 안 끝난 미래예정 주문이나 소급 등록 건이 여러 날짜에
--   걸쳐 있을 수 있다. wav_no는 항상 생성 당일이지만 특례를 두지 않고 같은 방식으로 훑는다.
--
--   실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--     - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--     - 이 스크립트를 적용하는 배포에는 애플리케이션 코드 변경(6개 호출부를
--       nbrService.issue(...)로 교체, 옛 시퀀스 nextval 리포지토리 메서드 제거)도
--       같이 나가야 한다 — 옛 코드가 남은 채로 이 스크립트만 먼저 적용하면
--       DROP SEQUENCE 이후 옛 코드의 nextval 호출이 즉시 실패한다.
-- =====================================================================

DO $nbr$
BEGIN
    -- 1. 테이블 -------------------------------------------------------
    IF to_regclass('nbr_rule') IS NULL THEN
        CREATE TABLE nbr_rule (
            rule_cd     VARCHAR(30)     NOT NULL,
            rule_nm     VARCHAR(100)    NOT NULL,
            ptrn        VARCHAR(200)    NOT NULL,
            dync_ky_typ VARCHAR(10)     NOT NULL,
            us_yn       CHAR(1)         DEFAULT 'Y' NOT NULL,
            created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
            created_by  VARCHAR(30)     DEFAULT 'admin' NOT NULL,
            updated_at  TIMESTAMP,
            updated_by  VARCHAR(30),
            CONSTRAINT pk_nbr_rule PRIMARY KEY (rule_cd),
            CONSTRAINT ck_nbr_rule_dync_ky_typ CHECK (dync_ky_typ IN ('NONE', 'DATE')),
            CONSTRAINT ck_nbr_rule_us_yn CHECK (us_yn IN ('Y', 'N'))
        );
        COMMENT ON TABLE  nbr_rule IS '채번 규칙. 패턴 문자열 하나로 형식을 정의';
        COMMENT ON COLUMN nbr_rule.rule_cd     IS '채번 규칙 코드 (업무 식별자, 예: PROD_CD)';
        COMMENT ON COLUMN nbr_rule.ptrn        IS '채번 패턴. {SEQ:n} 정확히 1개 + 날짜 토큰({yyyyMMdd} 등)';
        COMMENT ON COLUMN nbr_rule.dync_ky_typ IS 'NONE=카운터 전역 공유 / DATE=호출자가 넘긴 날짜 기준 분리';
        COMMENT ON COLUMN nbr_rule.us_yn       IS '사용 여부. N이면 발급 요청 거부';
        RAISE NOTICE 'nbr_rule 테이블 생성';
    ELSE
        RAISE NOTICE 'nbr_rule 테이블 이미 존재 — 건너뜀';
    END IF;

    IF to_regclass('nbr_seq') IS NULL THEN
        CREATE TABLE nbr_seq (
            rule_cd     VARCHAR(30)     NOT NULL,
            dync_ky     VARCHAR(30)     NOT NULL,
            seq         BIGINT          DEFAULT 0 NOT NULL,
            created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
            created_by  VARCHAR(30)     DEFAULT 'admin' NOT NULL,
            updated_at  TIMESTAMP,
            updated_by  VARCHAR(30),
            CONSTRAINT pk_nbr_seq PRIMARY KEY (rule_cd, dync_ky)
        );
        COMMENT ON TABLE  nbr_seq IS '채번 카운터. rule_cd+dync_ky별 현재 발급값';
        COMMENT ON COLUMN nbr_seq.dync_ky IS 'NONE이면 고정값 "-", DATE면 yyyyMMdd';
        COMMENT ON COLUMN nbr_seq.seq     IS '현재 발급값. updated_at이 곧 최종 발급 시각';
        RAISE NOTICE 'nbr_seq 테이블 생성';
    ELSE
        RAISE NOTICE 'nbr_seq 테이블 이미 존재 — 건너뜀';
    END IF;

    -- 2. 규칙 6건 시드 --------------------------------------------------
    INSERT INTO nbr_rule (rule_cd, rule_nm, ptrn, dync_ky_typ) VALUES
        ('PROD_CD',     '상품 코드',        'PROD-{SEQ:4}',           'NONE'),
        ('VNDR_CD',     '벤더 코드',        'VD-{SEQ:4}',             'NONE'),
        ('OMS_IB_NO',   '입고주문 번호',    'PO-{yyyyMMdd}-{SEQ:3}',  'DATE'),
        ('IB_NO',       '입고 번호',        'IB-{yyyyMMdd}-{SEQ:3}',  'DATE'),
        ('OUTB_NO',     '출고 번호',        'OB-{yyyyMMdd}-{SEQ:3}',  'DATE'),
        ('OUTB_WAV_NO', '출고 웨이브 번호', 'WV-{yyyyMMdd}-{SEQ:3}',  'DATE')
    ON CONFLICT (rule_cd) DO NOTHING;
    RAISE NOTICE '채번 규칙 6건 반영';

    -- 3. 카운터 시딩 (번호가 끊기지 않도록 실제 발급 데이터에서 최대값을 뽑는다) ----
    INSERT INTO nbr_seq (rule_cd, dync_ky, seq)
    SELECT 'PROD_CD', '-', COALESCE(MAX(split_part(prod_cd, '-', 2)::bigint), 0) FROM prod
    ON CONFLICT (rule_cd, dync_ky) DO NOTHING;

    INSERT INTO nbr_seq (rule_cd, dync_ky, seq)
    SELECT 'VNDR_CD', '-', COALESCE(MAX(split_part(vndr_cd, '-', 2)::bigint), 0) FROM vendor
    ON CONFLICT (rule_cd, dync_ky) DO NOTHING;

    INSERT INTO nbr_seq (rule_cd, dync_ky, seq)
    SELECT 'OMS_IB_NO', split_part(oms_ib_no, '-', 2), MAX(split_part(oms_ib_no, '-', 3)::bigint)
      FROM oms_ib_order GROUP BY split_part(oms_ib_no, '-', 2)
    ON CONFLICT (rule_cd, dync_ky) DO NOTHING;

    INSERT INTO nbr_seq (rule_cd, dync_ky, seq)
    SELECT 'IB_NO', split_part(ib_no, '-', 2), MAX(split_part(ib_no, '-', 3)::bigint)
      FROM ib_order GROUP BY split_part(ib_no, '-', 2)
    ON CONFLICT (rule_cd, dync_ky) DO NOTHING;

    INSERT INTO nbr_seq (rule_cd, dync_ky, seq)
    SELECT 'OUTB_NO', split_part(outb_no, '-', 2), MAX(split_part(outb_no, '-', 3)::bigint)
      FROM outb_order GROUP BY split_part(outb_no, '-', 2)
    ON CONFLICT (rule_cd, dync_ky) DO NOTHING;

    INSERT INTO nbr_seq (rule_cd, dync_ky, seq)
    SELECT 'OUTB_WAV_NO', split_part(wav_no, '-', 2), MAX(split_part(wav_no, '-', 3)::bigint)
      FROM outb_wave GROUP BY split_part(wav_no, '-', 2)
    ON CONFLICT (rule_cd, dync_ky) DO NOTHING;

    RAISE NOTICE '채번 카운터 시딩 완료';

    -- 4. 옛 시퀀스 제거 ---------------------------------------------------
    DROP SEQUENCE IF EXISTS prod_cd_seq;
    DROP SEQUENCE IF EXISTS vndr_cd_seq;
    DROP SEQUENCE IF EXISTS oms_ib_no_seq;
    DROP SEQUENCE IF EXISTS ib_no_seq;
    DROP SEQUENCE IF EXISTS outb_no_seq;
    DROP SEQUENCE IF EXISTS outb_wave_no_seq;
    RAISE NOTICE '옛 채번 시퀀스 6개 제거 완료';

    RAISE NOTICE '채번관리 마이그레이션 완료';
END
$nbr$;

-- =====================================================================
-- 적용 후 확인
--   1) 규칙 6건
--      SELECT * FROM nbr_rule ORDER BY rule_cd;
--   2) 카운터가 실제 최대 발급값과 일치하는지 (예: IB_NO)
--      SELECT dync_ky, seq FROM nbr_seq WHERE rule_cd = 'IB_NO' ORDER BY dync_ky;
--      SELECT split_part(ib_no,'-',2) AS de, MAX(split_part(ib_no,'-',3)::bigint) AS mx
--        FROM ib_order GROUP BY 1 ORDER BY 1;
--      -- 두 결과의 dync_ky/de별 seq/mx가 같아야 한다.
--   3) 옛 시퀀스 0건
--      SELECT sequencename FROM pg_sequences
--       WHERE sequencename IN ('prod_cd_seq','vndr_cd_seq','oms_ib_no_seq',
--                               'ib_no_seq','outb_no_seq','outb_wave_no_seq');
-- =====================================================================
