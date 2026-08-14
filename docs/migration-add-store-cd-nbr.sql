-- =====================================================================
-- 점포 코드 채번 도입 — nbr_rule STORE_CD + nbr_seq 카운터 시드.
--
-- 현재 라이브 상태 → schema.sql 상태.
-- 근거: 점포 관리 화면 구현(todo-store.md §1) — store_cd는 벤더·상품처럼 서버 채번으로 결정.
--   - 시드가 이미 ST-0001 형식을 쓰고 있고, 사용자 입력으로 두면 uq_store_cd 위반이
--     "다른 데이터가 참조하고 있어…" 라는 엉뚱한 409 메시지로 나간다.
--   - 접두어 ST는 STKTK_NO와 같지만 형식이 다르다(ST-0001 vs ST-20260814-001) —
--     prfx는 UNIQUE가 아니고, 기존 시드 형식을 바꿀 수 없어 그대로 쓴다.
--   - 카운터는 5부터 — 시드(seed-dev.sql)가 ST-0005까지 이미 썼다. 0으로 두면
--     화면 첫 등록에서 ST-0001이 다시 발급돼 즉시 uq_store_cd 위반.
--   - reset-dev.sql의 카운터 되감기 대상에 넣지 않는다 — 그 스크립트는 점포를 남긴다
--     (VNDR_CD와 같은 카버아웃).
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — 존재 확인을 걸어 이미 적용된 항목은 건너뛴다
-- =====================================================================
DO $mig$
BEGIN
    -- 1. 채번 규칙 시드 -----------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM nbr_rule WHERE rule_cd = 'STORE_CD') THEN
        INSERT INTO nbr_rule (rule_cd, rule_nm, prfx, prfx_dlmt, de_dlmt, seq_dgt, dync_ky_typ)
        VALUES ('STORE_CD', '점포 코드', 'ST', '-', '-', 4, 'NONE');
        RAISE NOTICE 'nbr_rule STORE_CD 시드';
    ELSE
        RAISE NOTICE 'nbr_rule STORE_CD 이미 존재 — 건너뜀';
    END IF;

    -- 2. 채번 카운터 --------------------------------------------------------
    -- 시드 최대치가 아니라 라이브 store의 실제 최대 번호에 맞춘다 — 시드 이후 손으로
    -- 넣은 ST-000N 이 있어도 다음 발급이 그 위에서 시작하게.
    INSERT INTO nbr_seq (rule_cd, dync_ky, seq)
    SELECT 'STORE_CD', '-', COALESCE(MAX(SUBSTRING(store_cd FROM '^ST-(\d+)$')::INT), 0)
      FROM store
    ON CONFLICT (rule_cd, dync_ky) DO UPDATE SET seq = GREATEST(nbr_seq.seq, EXCLUDED.seq);
    RAISE NOTICE 'nbr_seq STORE_CD 카운터 = %', (SELECT seq FROM nbr_seq WHERE rule_cd = 'STORE_CD');
END
$mig$;

-- 확인:
--   SELECT * FROM nbr_rule WHERE rule_cd = 'STORE_CD';
--   SELECT * FROM nbr_seq  WHERE rule_cd = 'STORE_CD';   -- seq >= 5 (시드 기준)
