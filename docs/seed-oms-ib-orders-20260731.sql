-- 입고주문 추가 시드 (2026-07-31 작성) — 화면 테스트용 CREATED 주문 5건.
--   seed-dev.sql이 적용된 DB에 DBeaver로 실행한다. 재실행 안전 — 이미 있는 PO 번호는 건너뛴다.
--   전부 CREATED로 넣는다: 확정(ASN 생성)은 화면의 '주문확정' 버튼으로 진행해야
--   OmsIbOrderService.convert()의 환산·채번 경로를 그대로 태울 수 있다.
-- 환산 주석은 참고용이다 (odr_qty는 입고단위, 확정 시 출고단위로 환산).

WITH new_order AS (
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_de, odr_dvsn, pic_nm, rmk)
    SELECT 'PO-20260731-001', 'CREATED', v.vendor_id, DATE '2026-07-31', 'NRML', '김상현', '냉장 데일리 정기 발주'
    FROM vendor v WHERE v.vndr_cd = 'VD-0001'
      AND NOT EXISTS (SELECT 1 FROM oms_ib_order o WHERE o.oms_ib_no = 'PO-20260731-001')
    RETURNING oms_ib_order_id
)
INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, odr_qty)
SELECT new_order.oms_ib_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('서울우유 1L', 30),           -- 30 BOX  -> 360 EA
    ('슬라이스 치즈 20매', 12),     -- 12 BOX  -> 180 EA
    ('국산콩 두부 300g', 15),      -- 15 BOX  -> 300 EA
    ('참치마요 삼각김밥', 10)       -- 10 TRAY -> 200 EA
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

WITH new_order AS (
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_de, odr_dvsn, pic_nm, rmk)
    SELECT 'PO-20260731-002', 'CREATED', v.vendor_id, DATE '2026-07-31', 'URGT', '이정민', '냉동 치즈 결품 — 당일 하차 요청'
    FROM vendor v WHERE v.vndr_cd = 'VD-0002'
      AND NOT EXISTS (SELECT 1 FROM oms_ib_order o WHERE o.oms_ib_no = 'PO-20260731-002')
    RETURNING oms_ib_order_id
)
INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, odr_qty)
SELECT new_order.oms_ib_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('모짜렐라 피자치즈 1kg', 10),  -- 10 BOX -> 100 EA
    ('냉동 블루베리 1kg', 8),      --  8 BOX ->  80 EA
    ('왕교자 만두 1kg', 15)        -- 15 BOX -> 120 EA
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

WITH new_order AS (
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_de, odr_dvsn, pic_nm, rmk)
    SELECT 'PO-20260801-001', 'CREATED', v.vendor_id, DATE '2026-08-01', 'NRML', '박도현', '음료 파렛트 2종 — 도크 2번 하차'
    FROM vendor v WHERE v.vndr_cd = 'VD-0003'
      AND NOT EXISTS (SELECT 1 FROM oms_ib_order o WHERE o.oms_ib_no = 'PO-20260801-001')
    RETURNING oms_ib_order_id
)
INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, odr_qty)
SELECT new_order.oms_ib_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('제주 삼다수 2L', 1),             -- 1 PLT -> 84 BOX (PLT 504 / BOX 6)
    ('코카콜라 350ml (24입)', 1),      -- 1 PLT -> 60 BOX (PLT 1440 / BOX 24)
    ('진라면 순한맛 멀티팩 (5입)', 20)   -- 20 BOX -> 160 EA
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

WITH new_order AS (
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_de, odr_dvsn, pic_nm, rmk)
    SELECT 'PO-20260801-002', 'CREATED', v.vendor_id, DATE '2026-08-01', 'NRML', '최유진', NULL
    FROM vendor v WHERE v.vndr_cd = 'VD-0004'
      AND NOT EXISTS (SELECT 1 FROM oms_ib_order o WHERE o.oms_ib_no = 'PO-20260801-002')
    RETURNING oms_ib_order_id
)
INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, odr_qty)
SELECT new_order.oms_ib_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('백설 밀가루 1kg', 10),       -- 10 BOX -> 120 EA
    ('물티슈 캡형 100매', 12),      -- 12 BOX -> 120 EA
    ('스팸 클래식 200g', 20)       -- 20 BOX -> 480 EA
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

WITH new_order AS (
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_de, odr_dvsn, pic_nm, rmk)
    SELECT 'PO-20260802-001', 'CREATED', v.vendor_id, DATE '2026-08-02', 'RTNGS', '정성호', '점포 반품분 재입고 — 상태 확인 후 검수'
    FROM vendor v WHERE v.vndr_cd = 'VD-0005'
      AND NOT EXISTS (SELECT 1 FROM oms_ib_order o WHERE o.oms_ib_no = 'PO-20260802-001')
    RETURNING oms_ib_order_id
)
INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, odr_qty)
SELECT new_order.oms_ib_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('바나나우유 240ml', 8),       --  8 BOX -> 192 EA
    ('딸기 요거트 4입', 10)        -- 10 BOX -> 120 EA
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

-- 채번 카운터를 날짜별 건수만큼 맞춘다. 안 하면 화면에서 같은 예정일로 등록할 때
-- PO-...-001부터 다시 채번돼 uq_oms_ib_no 유니크 위반이 난다.
INSERT INTO nbr_seq (rule_cd, dync_ky, seq) VALUES
    ('OMS_IB_NO', '20260731', 2),
    ('OMS_IB_NO', '20260801', 2),
    ('OMS_IB_NO', '20260802', 1)
ON CONFLICT (rule_cd, dync_ky) DO UPDATE SET seq = GREATEST(nbr_seq.seq, EXCLUDED.seq);
