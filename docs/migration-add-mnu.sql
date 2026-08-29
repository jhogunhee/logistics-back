-- =====================================================================
-- 메뉴·역할 권한 도입 — 메뉴 카탈로그(mnu)와 역할별 메뉴 권한(mnu_role)을 만든다.
--   전제: docs/migration-usr.sql 까지 적용된 DB(usr_role의 역할 CHECK와 같은 값을 쓴다).
--
--   ▣ 전체가 DO 블록 하나다 (BEGIN;/COMMIT; 을 쓰지 않는다) — CLAUDE.md 규칙.
--     전 구간에 존재 확인이 걸려 있어 몇 번을 돌려도 안전하다.
--
--   FK는 걸지 않는다. 시드는 mnu가 비어 있을 때만 넣는다 — 재실행해도
--   관리자가 메뉴 관리 화면에서 편집한 값을 덮지 않는다.
--
--   시드 내용은 docs/seed-mnu.sql과 완전히 같다(그 파일이 시드의 주인이고,
--   docs/seed-dev.sql · MnuSeedCoverageTest도 같은 내용을 쓴다). 화면 여럿이 같은
--   API 접두를 가질 수 있다 — 그 경우 하나라도 켜져 있으면 통과다.
--
--   실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--     - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--     - 이 스크립트를 적용하는 배포에는 애플리케이션 코드 변경(MnuAccessFilter,
--       /auth/me 응답에 menus 추가)도 같이 나가야 한다.
-- =====================================================================

DO $mnu$
BEGIN
    -- 1. 테이블 -------------------------------------------------------
    -- 옛 초안이 남긴 mnu가 있으면 여기서 끊는다 — CREATE를 건너뛴 채 시드 INSERT가
    -- 42703(없는 컬럼)으로 깨지면 원인이 안 보인다
    IF to_regclass('mnu') IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM pg_attribute
             WHERE attrelid = to_regclass('mnu') AND attname = 'icon_nm' AND NOT attisdropped) THEN
        RAISE EXCEPTION 'mnu 테이블이 이 스크립트와 다른 모양이다(icon_nm 없음). 비어 있으면 DROP TABLE mnu_role, mnu; 후 다시 실행할 것';
    END IF;

    IF to_regclass('mnu') IS NULL THEN
        CREATE TABLE mnu (
            mnu_cd      VARCHAR(30)     NOT NULL,
            mnu_nm      VARCHAR(50)     NOT NULL,
            dvsn        VARCHAR(10)     NOT NULL,
            grp_nm      VARCHAR(30)     NOT NULL,
            srt_seq     INTEGER         NOT NULL,
            icon_nm     VARCHAR(30)     NOT NULL,
            scrn_pth    VARCHAR(60)     NOT NULL,
            api_prfx    VARCHAR(50),
            kywd        VARCHAR(200),
            created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
            created_by  VARCHAR(30)     DEFAULT 'admin' NOT NULL,
            updated_at  TIMESTAMP,
            updated_by  VARCHAR(30),
            CONSTRAINT pk_mnu PRIMARY KEY (mnu_cd),
            CONSTRAINT uq_mnu_scrn_pth UNIQUE (scrn_pth),
            -- api_prfx에는 UNIQUE가 없다 — 같은 API를 나눠 쓰는 화면이 여럿이다
            -- (입고 검수·입고확정, WEB·PDA 실행 화면 7쌍 등)
            CONSTRAINT ck_mnu_dvsn CHECK (dvsn IN ('WEB', 'PDA'))
        );
        COMMENT ON TABLE  mnu IS '메뉴 카탈로그 (사이드바·PDA 홈의 주인)';
        COMMENT ON COLUMN mnu.dvsn IS 'WEB 데스크톱 / PDA 현장 단말';
        COMMENT ON COLUMN mnu.icon_nm IS 'lucide 아이콘 이름. 프론트 menuIcons.js가 컴포넌트로 바꾼다';
        COMMENT ON COLUMN mnu.scrn_pth IS '프론트 라우트. App.jsx에 같은 경로가 있어야 한다';
        COMMENT ON COLUMN mnu.api_prfx IS '이 화면의 쓰기 API 이름공간. 여러 화면이 같은 값을 가질 수 있고 NULL이면 조회 전용이다';
        RAISE NOTICE 'mnu 테이블 생성';
    ELSE
        RAISE NOTICE 'mnu 테이블 이미 존재 — 건너뜀';
    END IF;

    -- 옛 판으로 이미 만든 DB 보정 — api_prfx의 UNIQUE는 걷어낸다.
    -- 같은 API를 나눠 쓰는 화면이 여럿이라(입고검수·입고확정, WEB·PDA 7쌍) 화면당 하나로 묶을 수 없다
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_mnu_api_prfx') THEN
        ALTER TABLE mnu DROP CONSTRAINT uq_mnu_api_prfx;
        RAISE NOTICE 'uq_mnu_api_prfx 제거 — 접두를 화면 여럿이 나눠 가진다';
    END IF;

    IF to_regclass('mnu_role') IS NULL THEN
        CREATE TABLE mnu_role (
            mnu_cd      VARCHAR(30)     NOT NULL,
            role        VARCHAR(20)     NOT NULL,
            created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
            created_by  VARCHAR(30)     DEFAULT 'admin' NOT NULL,
            updated_at  TIMESTAMP,
            updated_by  VARCHAR(30),
            CONSTRAINT pk_mnu_role PRIMARY KEY (mnu_cd, role),
            CONSTRAINT ck_mnu_role_role CHECK (role IN
                ('CENT_ADMR', 'ODR_PIC', 'IB_PIC', 'INV_PIC', 'OUTB_PIC', 'INQ'))
        );
        COMMENT ON TABLE  mnu_role IS '역할별 메뉴 권한. 켜진 것만 행으로 있다';
        COMMENT ON COLUMN mnu_role.role IS 'ADMR은 매핑 대상이 아니다 — 항상 전 메뉴를 보므로 CHECK에서 뺐다';
        RAISE NOTICE 'mnu_role 테이블 생성';
    ELSE
        RAISE NOTICE 'mnu_role 테이블 이미 존재 — 건너뜀';
    END IF;

    -- 2. 메뉴·권한 시드 (비어 있을 때만 — 관리자 편집분을 덮지 않는다) -----
    IF NOT EXISTS (SELECT 1 FROM mnu) THEN
        INSERT INTO mnu (mnu_cd, mnu_nm, dvsn, grp_nm, srt_seq, icon_nm, scrn_pth, api_prfx, kywd) VALUES
        ('DASHBOARD', '대시보드', 'WEB', '모니터링', 10, 'LayoutDashboard', '/', NULL, 'dashboard 홈 메인'),
('WRK_ACRST', '작업자 실적', 'WEB', '모니터링', 20, 'Users', '/monitoring/worker', NULL, 'worker 작업자 실적 생산성 집계 건수 처리량 피킹 적치 검수 누가 담당자 productivity'),
        ('OMS_IB_ODR', '입고주문', 'WEB', 'OMS', 100, 'FileInput', '/oms/inbound-order', '/oms/inbound-orders', '발주 po purchase order 등록'),
        ('OMS_IB_ODR_LIST', '입고주문 관리', 'WEB', 'OMS', 110, 'ClipboardList', '/oms/inbound-orders', '/oms/inbound-orders', '발주 목록 확정 취소 삭제'),
        ('OMS_ATO_ODR', '자동발주 산정', 'WEB', 'OMS', 120, 'Sparkles', '/oms/ato-odr', '/oms/ato-odr', 'ato auto 자동 발주점 순재고 제안 스케줄'),
        ('OMS_OUTB_ODR', '출고주문', 'WEB', 'OMS', 130, 'FileOutput', '/oms/outbound-order', '/oms/outbound-orders', '수주 so 점포 등록'),
        ('OMS_OUTB_ODR_LIST', '출고주문 관리', 'WEB', 'OMS', 140, 'FilePlus', '/oms/outbound-orders', '/oms/outbound-orders', '수주 목록 취소'),
        ('IB_ASN', '입고예정(ASN) 관리', 'WEB', '입고', 200, 'Truck', '/inbound/asn', NULL, 'asn 예정 inbound'),
        ('IB_RECEIVING', '입고검수', 'WEB', '입고', 210, 'ClipboardCheck', '/inbound/receiving', '/inbound/asns', '검수 수령 receiving lot 제조일자'),
        ('IB_PTAWY_ODR', '적치지시', 'WEB', '입고', 220, 'ListChecks', '/inbound/putaway-order', '/inbound/putaway', 'putaway 지시 로케이션 배정'),
        ('IB_PTAWY', '적치', 'WEB', '입고', 230, 'PackageOpen', '/inbound/putaway', '/inbound/putaway', 'putaway 이동 보관'),
        ('IB_CONFIRM', '입고확정', 'WEB', '입고', 240, 'CheckCircle2', '/inbound/confirm', '/inbound/asns', '확정 confirm 결품 마감'),
        ('STK_STATUS', '현재고 조회', 'WEB', '재고', 300, 'Box', '/stock/status', NULL, 'inventory 재고 현황 수량 map 맵 점유 로케이션 평면도 구조도 랙 베이 레벨 빈자리 occupancy'),
        ('STK_HIST', '재고 이력 조회', 'WEB', '재고', 310, 'History', '/stock/history', NULL, 'inventory history 원장 입출고'),
        ('STK_ATTR', '재고 속성변경', 'WEB', '재고', 320, 'Tags', '/stock/attribute', '/inventory/lot-attrs', 'lot 유통기한 제조일자 정정 변경 전량 라벨 유지'),
        ('STK_LOT_CHNG', '재고 로트변경', 'WEB', '재고', 330, 'Split', '/stock/lot-change', '/inventory/lot-chngs', 'lot 로트 분할 병합 부분 수량 정정 split merge'),
        ('STK_HOLD', '재고 보류', 'WEB', '재고', 340, 'PauseCircle', '/stock/hold', '/inventory/holds', 'hold 출고 금지'),
        ('STK_MOVE', '재고 이동', 'WEB', '재고', 350, 'ArrowLeftRight', '/stock/move', '/inventory/moves', 'move 로케이션 이동 지시 예약 등록 확정 취소'),
        ('MST_FXNG_LOC', '고정 로케이션 관리', 'WEB', '재고', 360, 'Pin', '/master/fxng-loc', '/master/fxng-locs', 'fxng fixed 고정 피킹존 보충 재보충점 마스터'),
        ('STK_SPMT', '정기 보충', 'WEB', '재고', 370, 'Repeat', '/stock/spmt', '/inventory/spmt', '보충 replenish spmt min max 피킹존 고정로케이션 fefo 재보충점'),
        ('STK_STKTK', '재고조사', 'WEB', '재고', 380, 'Calculator', '/stock/count', '/inventory/stocktakes', '실사 count 차이 오차 전산수량 블라인드'),
        ('STK_ADJ', '재고조정', 'WEB', '재고', 390, 'SlidersHorizontal', '/stock/adjust', '/inventory/adjs', 'adjust 조정 폐기 스크랩 불량 반품 견본 처분 증감 scrap'),
        ('OUTB_ODR', '출고예정 관리', 'WEB', '출고', 400, 'PackageCheck', '/outbound/order', NULL, '출고예정 출고주문 obs outbound order 예정 창고 문서 조회'),
        ('OUTB_WAV', '웨이브 편성', 'WEB', '출고', 410, 'Layers', '/outbound/wave', '/outbound/waves', 'wave 묶음 출고주문 담기 전략 실행 피킹지시 발행단위'),
        ('OUTB_ALOC', '할당', 'WEB', '출고', 420, 'Shuffle', '/outbound/allocation', '/outbound/allocations', 'allocation 재고 배정 fefo'),
        ('OUTB_PIKNG_ODR', '피킹지시', 'WEB', '출고', 430, 'ScrollText', '/outbound/pick-order', '/outbound/picking-tasks', 'picking 지시'),
        ('OUTB_RPLN', '수시보충', 'WEB', '출고', 440, 'PackagePlus', '/outbound/replenishment', '/outbound/replenishment', 'replenishment 보충 피킹존 보관존 이동'),
        ('OUTB_PIKNG', '피킹', 'WEB', '출고', 450, 'PackageOpen', '/outbound/picking', '/outbound/picking', 'picking 집품'),
        ('OUTB_SHMT', '출고확정', 'WEB', '출고', 460, 'Send', '/outbound/shipping', '/outbound/shipping', 'shipping 상차 출하'),
        ('MST_ZON', '존 관리', 'WEB', '창고', 500, 'LayoutGrid', '/master/zone', '/master/zons', 'zone 존 보관유형'),
        ('MST_LOC', '로케이션 관리', 'WEB', '창고', 510, 'MapPin', '/master/location', '/master/locs', 'location 로케이션 랙'),
        ('MST_PROD', '상품 관리', 'WEB', '마스터', 600, 'Barcode', '/master/prod', '/master/prods', 'product 상품 기준정보 온도대'),
        ('MST_UOM', '단위 관리', 'WEB', '마스터', 610, 'Ruler', '/master/uom', '/master/prod-uoms', 'uom 포장 낱개수량 중량 박스 파렛트'),
        ('MST_PROD_VNDR', '상품 거래처 관리', 'WEB', '마스터', 620, 'Handshake', '/master/prod-vndr', '/master/prod-vndrs', 'prod vendor 공급 발주점 발주상한 자동발주 moq 최소주문 리드타임'),
        ('MST_VNDR', '벤더 관리', 'WEB', '마스터', 630, 'Truck', '/master/vendor', '/master/vendors', 'vendor 거래처 납품처'),
        ('MST_STORE', '점포 관리', 'WEB', '마스터', 640, 'Store', '/master/store', '/master/stores', 'store 점포 매장'),
        ('MST_NBR_RULE', '채번규칙 관리', 'WEB', '마스터', 650, 'Hash', '/master/nbr-rules', '/master/nbr-rules', 'nbr 채번 번호 규칙 패턴 시퀀스'),
        ('MST_CODE', '공통코드 관리', 'WEB', '마스터', 660, 'ListTree', '/master/codes', '/master/codes', 'code 공통코드 그룹 코드값 온도대 보관유형 업무구분 발주구분 계량단위'),
        ('MST_LABEL', '라벨 인쇄', 'WEB', '마스터', 670, 'Printer', '/master/labels', NULL, 'label 라벨 barcode 바코드 code128 인쇄 print 출력 로케이션 상품 lot pda 스캔'),
        ('MST_USR', '사용자 관리', 'WEB', '마스터', 680, 'Users', '/master/usr', '/master/usrs', 'user 사용자 계정 로그인 역할 role 권한 비밀번호'),
        ('MNU_MST', '메뉴 관리', 'WEB', '마스터', 685, 'List', '/master/menu', '/master/mnus', 'menu 메뉴 등록 순서'),
        ('MNU_AUTH', '권한별 메뉴 관리', 'WEB', '마스터', 690, 'ShieldCheck', '/master/menu-auth', '/master/mnus/roles', 'auth 권한 역할 메뉴'),
        ('STGY_INSP', '검수 정책관리', 'WEB', '전략', 700, 'ShieldCheck', '/strategy/inspection', '/strategy/inspection-policy', 'inspection 검수 제약 정책 역순제한 유통기한 잔여비율 전략 입고'),
        ('STGY_PTAWY', '적치 전략관리', 'WEB', '전략', 710, 'Settings2', '/strategy/putaway', '/strategy/putaway-strategies', 'putaway strategy 전략 추천 단계 로케이션 입고'),
        ('STGY_WAV', '웨이브 전략관리', 'WEB', '전략', 720, 'Waves', '/strategy/wave', '/strategy/wave-strategies', 'wave strategy 웨이브 편성 출고 조건그룹 출고유형 차량편수 전략'),
        ('STGY_ALOC', '할당 전략관리', 'WEB', '전략', 730, 'Shuffle', '/strategy/allocation', '/strategy/allocation-strategies', 'allocation strategy 할당 분배 재고 배정 fefo 전략 출고'),
        ('PDA_ENTRY', '현장 작업', 'WEB', 'PDA', 800, 'Smartphone', '/m', NULL, 'pda 모바일 mobile 스캐너 barcode rf 현장 실행 피킹 적치 재고이동 재고조사'),
        ('PDA_RECEIVING', '입고검수', 'PDA', '입고', 810, 'ClipboardCheck', '/m/receiving', '/inbound/asns', '검수 수령 receiving 스캔 제조일자'),
        ('PDA_PTAWY', '적치', 'PDA', '입고', 815, 'Layers', '/m/putaway', '/inbound/putaway', 'putaway 이동 보관 스캔'),
        ('PDA_STK_INQ', '현재고 조회', 'PDA', '재고', 820, 'Search', '/m/stock-inquiry', NULL, 'inventory 재고 조회 스캔'),
        ('PDA_STK_MOVE', '재고이동', 'PDA', '재고', 824, 'ArrowLeftRight', '/m/stock-move', '/inventory/moves', 'move 이동 확정 스캔'),
        ('PDA_STKTK', '재고조사', 'PDA', '재고', 828, 'Calculator', '/m/stock-count', '/inventory/stocktakes', '실사 count 블라인드 스캔'),
        ('PDA_RPLN', '보충', 'PDA', '출고', 830, 'PackagePlus', '/m/replenishment', '/outbound/replenishment', 'replenishment 보충 확정 스캔'),
        ('PDA_PIKNG', '피킹', 'PDA', '출고', 834, 'PackageOpen', '/m/picking', '/outbound/picking', 'pikng 집품'),
        ('PDA_SHMT', '출고확정', 'PDA', '출고', 838, 'Send', '/m/shipping', '/outbound/shipping', 'shipping 상차 확정 스캔');

        INSERT INTO mnu_role (mnu_cd, role) VALUES
        ('DASHBOARD', 'CENT_ADMR'),
        ('DASHBOARD', 'ODR_PIC'),
        ('DASHBOARD', 'IB_PIC'),
        ('DASHBOARD', 'INV_PIC'),
        ('DASHBOARD', 'OUTB_PIC'),
        ('DASHBOARD', 'INQ'),
('WRK_ACRST', 'CENT_ADMR'),
        ('OMS_IB_ODR', 'ODR_PIC'),
        ('OMS_IB_ODR', 'INQ'),
        ('OMS_IB_ODR_LIST', 'ODR_PIC'),
        ('OMS_IB_ODR_LIST', 'INQ'),
        ('OMS_ATO_ODR', 'ODR_PIC'),
        ('OMS_ATO_ODR', 'INQ'),
        ('OMS_OUTB_ODR', 'ODR_PIC'),
        ('OMS_OUTB_ODR', 'INQ'),
        ('OMS_OUTB_ODR_LIST', 'ODR_PIC'),
        ('OMS_OUTB_ODR_LIST', 'INQ'),
        ('IB_ASN', 'CENT_ADMR'),
        ('IB_ASN', 'IB_PIC'),
        ('IB_ASN', 'INQ'),
        ('IB_RECEIVING', 'CENT_ADMR'),
        ('IB_RECEIVING', 'IB_PIC'),
        ('IB_RECEIVING', 'INQ'),
        ('IB_PTAWY_ODR', 'CENT_ADMR'),
        ('IB_PTAWY_ODR', 'IB_PIC'),
        ('IB_PTAWY_ODR', 'INQ'),
        ('IB_PTAWY', 'CENT_ADMR'),
        ('IB_PTAWY', 'IB_PIC'),
        ('IB_PTAWY', 'INQ'),
        ('IB_CONFIRM', 'CENT_ADMR'),
        ('IB_CONFIRM', 'IB_PIC'),
        ('IB_CONFIRM', 'INQ'),
        ('STK_STATUS', 'CENT_ADMR'),
        ('STK_STATUS', 'INV_PIC'),
        ('STK_STATUS', 'INQ'),
        ('STK_HIST', 'CENT_ADMR'),
        ('STK_HIST', 'INV_PIC'),
        ('STK_HIST', 'INQ'),
        ('STK_ATTR', 'CENT_ADMR'),
        ('STK_ATTR', 'INV_PIC'),
        ('STK_ATTR', 'INQ'),
        ('STK_LOT_CHNG', 'CENT_ADMR'),
        ('STK_LOT_CHNG', 'INV_PIC'),
        ('STK_LOT_CHNG', 'INQ'),
        ('STK_HOLD', 'CENT_ADMR'),
        ('STK_HOLD', 'INV_PIC'),
        ('STK_HOLD', 'INQ'),
        ('STK_MOVE', 'CENT_ADMR'),
        ('STK_MOVE', 'INV_PIC'),
        ('STK_MOVE', 'INQ'),
        ('MST_FXNG_LOC', 'CENT_ADMR'),
        ('MST_FXNG_LOC', 'INV_PIC'),
        ('MST_FXNG_LOC', 'INQ'),
        ('STK_SPMT', 'CENT_ADMR'),
        ('STK_SPMT', 'INV_PIC'),
        ('STK_SPMT', 'INQ'),
        ('STK_STKTK', 'CENT_ADMR'),
        ('STK_STKTK', 'INV_PIC'),
        ('STK_STKTK', 'INQ'),
        ('STK_ADJ', 'CENT_ADMR'),
        ('STK_ADJ', 'INV_PIC'),
        ('STK_ADJ', 'INQ'),
        ('OUTB_ODR', 'CENT_ADMR'),
        ('OUTB_ODR', 'OUTB_PIC'),
        ('OUTB_ODR', 'INQ'),
        ('OUTB_WAV', 'CENT_ADMR'),
        ('OUTB_WAV', 'OUTB_PIC'),
        ('OUTB_WAV', 'INQ'),
        ('OUTB_ALOC', 'CENT_ADMR'),
        ('OUTB_ALOC', 'OUTB_PIC'),
        ('OUTB_ALOC', 'INQ'),
        ('OUTB_PIKNG_ODR', 'CENT_ADMR'),
        ('OUTB_PIKNG_ODR', 'OUTB_PIC'),
        ('OUTB_PIKNG_ODR', 'INQ'),
        ('OUTB_RPLN', 'CENT_ADMR'),
        ('OUTB_RPLN', 'OUTB_PIC'),
        ('OUTB_RPLN', 'INQ'),
        ('OUTB_PIKNG', 'CENT_ADMR'),
        ('OUTB_PIKNG', 'OUTB_PIC'),
        ('OUTB_PIKNG', 'INQ'),
        ('OUTB_SHMT', 'CENT_ADMR'),
        ('OUTB_SHMT', 'OUTB_PIC'),
        ('OUTB_SHMT', 'INQ'),
        ('MST_ZON', 'CENT_ADMR'),
        ('MST_ZON', 'INQ'),
        ('MST_LOC', 'CENT_ADMR'),
        ('MST_LOC', 'INQ'),
        ('MST_PROD', 'INQ'),
        ('MST_UOM', 'INQ'),
        ('MST_PROD_VNDR', 'INQ'),
        ('MST_VNDR', 'INQ'),
        ('MST_STORE', 'INQ'),
        ('MST_NBR_RULE', 'INQ'),
        ('MST_CODE', 'INQ'),
        ('MST_LABEL', 'INQ'),
        ('STGY_INSP', 'CENT_ADMR'),
        ('STGY_INSP', 'INQ'),
        ('STGY_PTAWY', 'CENT_ADMR'),
        ('STGY_PTAWY', 'INQ'),
        ('STGY_WAV', 'CENT_ADMR'),
        ('STGY_WAV', 'INQ'),
        ('STGY_ALOC', 'CENT_ADMR'),
        ('STGY_ALOC', 'INQ'),
        ('PDA_ENTRY', 'CENT_ADMR'),
        ('PDA_ENTRY', 'ODR_PIC'),
        ('PDA_ENTRY', 'IB_PIC'),
        ('PDA_ENTRY', 'INV_PIC'),
        ('PDA_ENTRY', 'OUTB_PIC'),
        ('PDA_ENTRY', 'INQ'),
        ('PDA_RECEIVING', 'CENT_ADMR'),
        ('PDA_RECEIVING', 'IB_PIC'),
        ('PDA_PTAWY', 'CENT_ADMR'),
        ('PDA_PTAWY', 'IB_PIC'),
        ('PDA_STK_INQ', 'CENT_ADMR'),
        ('PDA_STK_INQ', 'IB_PIC'),
        ('PDA_STK_INQ', 'INV_PIC'),
        ('PDA_STK_INQ', 'OUTB_PIC'),
        ('PDA_STK_MOVE', 'CENT_ADMR'),
        ('PDA_STK_MOVE', 'INV_PIC'),
        ('PDA_STKTK', 'CENT_ADMR'),
        ('PDA_STKTK', 'INV_PIC'),
        ('PDA_RPLN', 'CENT_ADMR'),
        ('PDA_RPLN', 'OUTB_PIC'),
        ('PDA_PIKNG', 'CENT_ADMR'),
        ('PDA_PIKNG', 'OUTB_PIC'),
        ('PDA_SHMT', 'CENT_ADMR'),
        ('PDA_SHMT', 'OUTB_PIC');
        -- MNU_MST · MNU_AUTH는 mnu_role 행이 하나도 없다 — 관리자 전용이고 ADMR은 매핑 대상이 아니다

        RAISE NOTICE '메뉴 55건 · 메뉴 권한 127건 반영';
    ELSE
        RAISE NOTICE 'mnu에 이미 행이 있음 — 시드 건너뜀 (관리자 편집분 보존)';
    END IF;

    -- 3. 시드가 이미 들어간 DB에 나중 화면을 더한다 -----------------------
    --    위 시드는 mnu가 비어 있을 때만 도므로, 먼저 적용해 둔 DB에는 이 블록이 넣는다.
    --    관리자가 지운 메뉴를 되살리지 않게 「그 코드가 아예 없을 때만」 넣는다.
    IF NOT EXISTS (SELECT 1 FROM mnu WHERE mnu_cd = 'WRK_ACRST') THEN
        INSERT INTO mnu (mnu_cd, mnu_nm, dvsn, grp_nm, srt_seq, icon_nm, scrn_pth, api_prfx, kywd) VALUES
        ('WRK_ACRST', '작업자 실적', 'WEB', '모니터링', 20, 'Users', '/monitoring/worker', NULL,
         'worker 작업자 실적 생산성 집계 건수 처리량 피킹 적치 검수 누가 담당자 productivity');
        -- 개인별 생산성이라 조회(INQ)에게도 열지 않는다 — 백엔드 /wrkr 규칙과 같은 범위다
        INSERT INTO mnu_role (mnu_cd, role) VALUES ('WRK_ACRST', 'CENT_ADMR');
        RAISE NOTICE '작업자 실적 메뉴 추가';
    END IF;

    RAISE NOTICE '메뉴·역할 권한 마이그레이션 완료';
END
$mnu$;

-- =====================================================================
-- 적용 후 확인
--   1) 메뉴·권한 건수
--      SELECT (SELECT COUNT(*) FROM mnu) AS mnu_cnt, (SELECT COUNT(*) FROM mnu_role) AS mnu_role_cnt;
--      -- 54, 126 이어야 한다(관리자가 아직 편집하지 않았다면).
--   2) 역할 CHECK가 걸려 있는지 (ADMR 거부 확인)
--      INSERT INTO mnu_role (mnu_cd, role) VALUES ('DASHBOARD', 'ADMR');
--      -- ck_mnu_role_role 위반으로 실패해야 한다.
--   3) 접두를 나눠 쓰는 화면 (정상이다 — 어느 화면들이 묶여 있는지 확인용)
--      SELECT api_prfx, COUNT(*), string_agg(mnu_cd, ', ') FROM mnu WHERE api_prfx IS NOT NULL
--       GROUP BY api_prfx HAVING COUNT(*) > 1;
--      -- 입고검수·입고확정 등 위 머리말이 적은 쌍만 나와야 한다.
--   4) FK는 여전히 0건
--      SELECT COUNT(*) FROM pg_constraint WHERE contype = 'f';
--   5) 옛 시드(접두를 한쪽에만 주던 판)로 이미 채운 DB라면 아래를 한 번만 돌린다.
--      시드 재실행은 mnu가 비어 있을 때만 도므로 자동으로는 안 채워진다.
--      UPDATE mnu SET api_prfx = v.prfx FROM (VALUES
--        ('OMS_IB_ODR','/oms/inbound-orders'), ('OMS_OUTB_ODR','/oms/outbound-orders'),
--        ('IB_PTAWY_ODR','/inbound/putaway'), ('IB_CONFIRM','/inbound/asns'),
--        ('PDA_RECEIVING','/inbound/asns'), ('PDA_PTAWY','/inbound/putaway'),
--        ('PDA_STK_MOVE','/inventory/moves'), ('PDA_STKTK','/inventory/stocktakes'),
--        ('PDA_RPLN','/outbound/replenishment'), ('PDA_PIKNG','/outbound/picking'),
--        ('PDA_SHMT','/outbound/shipping')) AS v(cd, prfx)
--       WHERE mnu.mnu_cd = v.cd AND mnu.api_prfx IS NULL;
-- =====================================================================
