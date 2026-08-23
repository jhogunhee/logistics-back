-- =====================================================================
-- 상품 이미지를 이모지로 — 시더 상품 21개의 img_url 을 정적 파일 경로에서 emoji:… 로 바꾼다.
--
-- 배경: 상품 이미지를 화면에서 정하는 방법이 「아이콘(이모지) 고르기」 하나로 정리됐다.
--   그림 파일(/prod-img/{상품코드}.svg)을 연결하던 버튼은 뺐다 — 파일을 미리 소스 폴더에
--   넣어 둔 상품에만 통해서, 방금 만든 상품에서 누르면 「파일이 없습니다」로 끝났다.
--   화면이 할 수 없는 일을 버튼으로 세워 두면 그 자체가 미완성으로 읽힌다.
--
--   그러면 시더 21개만 그림이고 그 밖에는 전부 이모지가 되는데, 그림은 사용자가 만들 수
--   없는 형태라 「보기엔 좋은데 따라 할 수는 없는」 상태가 남는다. 그래서 시더도 이모지로 맞춘다.
--
--   이모지를 고른 이유(아이콘 세트 대신) — 라이선스 의무가 없고(무료 아이콘 사이트는 대개
--   출처 표기를 요구한다), 설치·번들 비용이 0이며, 「없는 아이콘 이름」 같은 런타임 실패가
--   생기지 않는다. 단색 외곽선으로는 우유·요거트·치즈가 한 그림으로 뭉개지는데 🥛·🍓·🧀는 갈린다.
--
--   SVG 파일(wms-front/public/prod-img/)은 지우지 않았다 — 되돌리고 싶어질 때를 위해 남긴다.
--   ProdThumb 은 여전히 세 형태(emoji: · /경로 · https://)를 모두 그리므로 되돌리려면
--   이 UPDATE 를 반대로 한 번 돌리면 된다.
--
-- 무엇이 바뀌나: prod.img_url 값 21건뿐이다. 컬럼·제약·다른 테이블은 건드리지 않는다.
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — 이미 이모지면 건너뛴다(정적 경로인 행만 바꾼다)
-- =====================================================================
DO $mig$
DECLARE
    r      record;
    v_done int := 0;
BEGIN
    FOR r IN
        SELECT * FROM (VALUES
            ('PROD-0001', 'emoji:💧'),   -- 제주 삼다수 2L
            ('PROD-0002', 'emoji:🍜'),   -- 신라면 멀티팩 (5입)
            ('PROD-0003', 'emoji:🍚'),   -- 햇반 백미 210g
            ('PROD-0004', 'emoji:☕'),   -- 일회용 종이컵 1000입
            ('PROD-0005', 'emoji:🥛'),   -- 서울우유 1L
            ('PROD-0006', 'emoji:🍓'),   -- 딸기 요거트 4입
            ('PROD-0007', 'emoji:🍙'),   -- 참치마요 삼각김밥
            ('PROD-0008', 'emoji:🫘'),   -- 국산콩 두부 300g
            ('PROD-0009', 'emoji:🥟'),   -- 왕교자 만두 1kg
            ('PROD-0010', 'emoji:🍤'),   -- 냉동 새우살 500g
            ('PROD-0011', 'emoji:🍦'),   -- 붕어싸만코
            ('PROD-0012', 'emoji:🥤'),   -- 코카콜라 350ml (24입)
            ('PROD-0013', 'emoji:🍜'),   -- 진라면 순한맛 멀티팩 (5입)
            ('PROD-0014', 'emoji:🌾'),   -- 백설 밀가루 1kg
            ('PROD-0015', 'emoji:🥓'),   -- 스팸 클래식 200g
            ('PROD-0016', 'emoji:🧻'),   -- 물티슈 캡형 100매
            ('PROD-0017', 'emoji:🍌'),   -- 바나나우유 240ml
            ('PROD-0018', 'emoji:🧀'),   -- 슬라이스 치즈 20매
            ('PROD-0019', 'emoji:🥗'),   -- 닭가슴살 샐러드
            ('PROD-0020', 'emoji:🍕'),   -- 모짜렐라 피자치즈 1kg
            ('PROD-0021', 'emoji:🫐')    -- 냉동 블루베리 1kg
        ) AS t(prod_cd, img)
    LOOP
        UPDATE prod SET img_url = r.img
         WHERE prod_cd = r.prod_cd
           AND (img_url IS NULL OR img_url NOT LIKE 'emoji:%');
        IF FOUND THEN
            v_done := v_done + 1;
        END IF;
    END LOOP;

    RAISE NOTICE '상품 이미지 이모지 전환 완료 — % 건 (이미 이모지인 행은 건너뜀)', v_done;
END $mig$;

-- 확인:
--   SELECT prod_cd, prod_nm, img_url FROM prod ORDER BY prod_cd;
--   SELECT img_url, COUNT(*) FROM prod GROUP BY img_url ORDER BY 2 DESC;
