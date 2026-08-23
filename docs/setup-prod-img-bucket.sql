-- =====================================================================
-- 설정 스크립트: 상품 이미지 저장소(Supabase Storage 버킷 prod-img)
--
--   ★ 선택 사항이다 — 지금 기본 방식이 아니다.
--     상품 이미지는 프론트와 함께 배포되는 정적 파일(wms-front/public/prod-img/)로 굴리고
--     prod.img_url 에는 '/prod-img/PROD-0001.svg' 같은 루트 상대경로가 들어간다.
--     상품이 시더(docs/seed-dev.sql)로 고정된 데모라 그림도 시더 데이터의 일부로 본 것이고,
--     그래서 외부 저장소·키·정책이 하나도 필요 없다.
--
--     이 파일은 「화면에서 파일을 골라 올리는」 기능을 켤 때만 돌린다. 켜는 절차는
--     이 스크립트 실행 + wms-front/.env.local 에 VITE_SUPABASE_* 두 값 채우기가 전부다
--     (코드는 이미 들어가 있고, 환경변수가 비면 업로드 버튼만 스스로 막힌다).
--
--   대상: Supabase 프로젝트의 storage 스키마 (우리 업무 테이블이 아니라 Storage 자원이다)
--   근거: prod.img_url 이 가리키는 파일이 실제로 놓일 자리. 업로드는 프론트가
--         supabase-js 로 직접 하고 백엔드는 URL 문자열만 다룬다.
--
--   ※ 이 파일은 docs/schema.sql 의 일부가 아니다 — 그래서 migration-* 이 아니라 setup-* 이다.
--     schema.sql 은 우리가 만든 테이블의 주인이고, 여기 건드리는 storage.buckets ·
--     storage.objects 는 Supabase 가 소유한 테이블이다.
--
--   실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--     - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--     - 재실행 안전 — 이미 있으면 건너뛴다
--     - BEGIN/COMMIT 없이 DO 블록 하나 (DBeaver 25P02 방지)
--
--   ⚠️ 보안: anon 롤에 INSERT 를 열면 anon key 를 아는 누구나 이 버킷에 올릴 수 있다.
--     인증 모델이 없는 현 단계(AuditorAware 가 'admin' 고정)에서는 균형이 맞지만,
--     인증이 붙는 시점에 아래 정책의 `TO anon` 을 `TO authenticated` 로 좁힐 것.
--     그때까지의 방파제가 아래 file_size_limit · allowed_mime_types 다 —
--     프론트(prodImgApi.js)의 같은 검사는 브라우저에서 우회할 수 있어 서버 쪽에도 둔다.
-- =====================================================================

DO $setup$
DECLARE
    v_bucket CONSTANT text := 'prod-img';
BEGIN

    -- ── 1) 버킷 ────────────────────────────────────────────────
    -- public = true 인 이유: <img src> 가 인증 없이 읽어야 한다. 비공개로 두면 서명 URL 이
    -- 필요한데 그건 만료되므로, DB(prod.img_url)에 넣어둔 주소가 며칠 뒤 죽는다.
    IF NOT EXISTS (SELECT 1 FROM storage.buckets WHERE id = v_bucket) THEN
        INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
        VALUES (v_bucket, v_bucket, true, 2097152,   -- 2MB — prodImgApi.js 의 MAX_BYTES 와 한 벌
                ARRAY['image/png', 'image/jpeg', 'image/webp', 'image/gif', 'image/avif']);
        RAISE NOTICE '버킷 % 생성 (public, 2MB, 이미지 MIME 만)', v_bucket;
    ELSE
        -- 이미 있으면 설정만 현재 기준으로 맞춘다 (손으로 만들어둔 경우까지 흡수)
        UPDATE storage.buckets
           SET public = true,
               file_size_limit = 2097152,
               allowed_mime_types = ARRAY['image/png', 'image/jpeg', 'image/webp', 'image/gif', 'image/avif']
         WHERE id = v_bucket;
        RAISE NOTICE '버킷 % 이미 존재 — 설정만 갱신', v_bucket;
    END IF;

END
$setup$;


-- ── 2) 업로드 정책 ─────────────────────────────────────────────
-- storage.objects 는 RLS 가 기본 전면 차단이라 정책이 없으면 업로드가 401 로 막힌다.
-- CREATE POLICY 에는 IF NOT EXISTS 가 없어서 pg_policies 를 직접 확인한다.
--
-- 블록을 나눈 이유 — storage.objects 의 소유자는 supabase_storage_admin 이라 접속 롤에 따라
-- 「must be owner of table objects」로 막힐 수 있다. 한 블록에 묶으면 그때 위의 버킷 생성까지
-- 함께 되돌아간다. 읽기 정책은 두지 않는다 — public 버킷의 /object/public/ 경로는 RLS 를
-- 타지 않고, 화면이 이미지를 그리는 데 필요한 건 그 경로 하나뿐이다.
DO $policy$
BEGIN

    IF EXISTS (
        SELECT 1 FROM pg_policies
         WHERE schemaname = 'storage' AND tablename = 'objects'
           AND policyname = 'prod_img_anon_insert'
    ) THEN
        RAISE NOTICE '정책 prod_img_anon_insert 이미 존재 — 건너뜀';
        RETURN;
    END IF;

    CREATE POLICY prod_img_anon_insert ON storage.objects
        FOR INSERT TO anon WITH CHECK (bucket_id = 'prod-img');
    RAISE NOTICE '정책 prod_img_anon_insert 생성 (anon 업로드 허용)';

EXCEPTION
    WHEN insufficient_privilege THEN
        RAISE NOTICE '---------------------------------------------------------------';
        RAISE NOTICE '정책을 만들 권한이 없습니다 (storage.objects 소유자가 아님).';
        RAISE NOTICE '버킷은 위에서 이미 만들어졌으니 정책만 아래 둘 중 하나로 처리하세요.';
        RAISE NOTICE '  (가) 새 SQL 편집기에서 다음 두 줄을 먼저 실행:';
        RAISE NOTICE '       SET ROLE supabase_storage_admin;';
        RAISE NOTICE '       CREATE POLICY prod_img_anon_insert ON storage.objects';
        RAISE NOTICE '           FOR INSERT TO anon WITH CHECK (bucket_id = ''prod-img'');';
        RAISE NOTICE '  (나) 대시보드 → Storage → prod-img → Policies → New policy';
        RAISE NOTICE '       (INSERT 허용, 대상 롤 anon)';
        RAISE NOTICE '---------------------------------------------------------------';
END
$policy$;

-- 확인용 (선택) — 위 블록을 돌린 뒤 따로 실행해 결과를 눈으로 본다.
-- SELECT id, public, file_size_limit, allowed_mime_types FROM storage.buckets WHERE id = 'prod-img';
-- SELECT policyname, roles, cmd FROM pg_policies WHERE schemaname='storage' AND tablename='objects';
