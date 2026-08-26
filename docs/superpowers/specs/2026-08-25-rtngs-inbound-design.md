# 반품입고 설계 (2026-08-25)

점포가 돌려보내는 물건을 받는 흐름. 반품출고(센터 → 벤더)는 제외 — 불량 재고가 쌓이기 시작한 뒤 「그걸 어떻게 내보낼까」로 따로 붙인다.

## 1. 문서 흐름

```
OMS 입고주문(odr_dvsn = RTNGS, 상대 = 점포) ──확정──▶ ib_order(odr_dvsn = RTNGS, 상대 = 점포)
                                                        │
                                                검수 — 라인별 양품 / 불량 판정
                                                 ├ 양품 → RCV-STAGE → 적치지시 → 보관존   (기존 그대로)
                                                 └ 불량 → 반품존 로케이션에 직행 + 같은 트랜잭션에서 보류(inv_hld)
                                                        │
                                                     입고확정 (결품 = 예정 − 양품 − 불량)
```

- **새 문서를 만들지 않는다.** 반품은 입고주문의 구분 하나다 — `ODR_DVSN`에 `RTNGS 반품입고`가 이미 입고주문 컬럼값으로 있었고, 헤더-라인 CRUD · 확정 · 확정취소 · 일괄확정을 한 벌 더 만들 이유가 없다. 바뀌는 것은 「상대가 점포일 수 있다」와 「라인에 반품사유가 붙는다」 둘이다.
- `omsback → wmsback` 의존은 여전히 `IbOrder` 하나뿐이다.
- 불량을 스테이징에 받지 않는 이유: 보류된 재고는 가용수량(보유 − 예약 − 보류)에서 빠져 적치지시를 걸 수 없다. 스테이징에 받고 보류하면 거기 갇힌다.

## 2. 데이터

### 상대처 일반화 — `oms_ib_order` · `ib_order` 같은 모양

```sql
ALTER TABLE oms_ib_order ALTER COLUMN vendor_id DROP NOT NULL;
ALTER TABLE oms_ib_order ADD COLUMN store_id BIGINT;
ALTER TABLE oms_ib_order ADD COLUMN ref_outb_no VARCHAR(30);   -- 원 출고번호. 느슨한 참조, 반품만, 선택
ALTER TABLE oms_ib_order ADD CONSTRAINT ck_oms_ib_order_vndr_store
    CHECK ((vendor_id IS NOT NULL) <> (store_id IS NOT NULL));

ALTER TABLE ib_order ALTER COLUMN vendor_id DROP NOT NULL;
ALTER TABLE ib_order ADD COLUMN store_id BIGINT;
ALTER TABLE ib_order ADD CONSTRAINT ck_ib_order_vndr_store
    CHECK ((vendor_id IS NOT NULL) <> (store_id IS NOT NULL));
```

- 「구분이 반품이면 점포, 아니면 벤더」는 엔티티가 지킨다 — `OmsIbOrder` 생성자 · `update()`, `IbOrder` 생성자에서 `odrDvsn`과 상대를 함께 검증한다. DB CHECK는 「둘 중 하나」까지만 본다(구분과 짝을 맞추는 건 애플리케이션 책임 — FK가 없는 것과 같은 판단).
- `ref_outb_no`는 `ref` 표기다 — 새 컬럼은 사전의 `참조 REF`를 따른다(`stgy_exec_log.tgt_ref`와 같은 결정). 원 출고를 골랐을 때 라인을 미리 채우는 용도이고, 출고수량 초과 검사는 하지 않는다(부분 반품 누계 추적은 다음 단계). 정상 발주에는 NULL.
- 응답(`OmsIbOrderResponse` · `IbOrderResponse`)에 `storeId` · `storeNm` · (`OmsIbOrderResponse`엔) `refOutbNo` 추가. 화면은 `vndrNm ?? storeNm`을 「상대처」 한 열로 보여준다.

### 반품사유 — `oms_ib_line`

```sql
ALTER TABLE oms_ib_line ADD COLUMN rsn_cd   VARCHAR(10);    -- RTNGS_RSN. 반품 라인만
ALTER TABLE oms_ib_line ADD COLUMN rsn_dscr VARCHAR(200);   -- ETC일 때만
```

- 공통코드 `RTNGS_RSN`(반품사유) 신설: `MISDLV 오배송` · `DAMG 파손` · `EXPIRY 유통기한임박` · `UNSOLD 미판매` · `ETC 기타`. `ETC`일 때만 `rsn_dscr`를 받는다(`HLD_RSN`과 같은 규칙, `RsnValidator` 재사용).
- 반품 라인은 `rsn_cd` 필수, 정상 발주 라인은 NULL — `OmsIbOrderSaveRequest`가 구분을 보고 검사한다.
- 수량 단위: 정상 발주 `odr_qty`는 입고단위, **반품은 출고단위**(점포가 받은 단위로 돌아온다). 확정 시 환산이 `prod.toEaQty(odrQty, 구분에 따라 inbUomCd / outbUomCd)`로 갈린다 — 단위가 갈리는 경계는 지금도 확정 한 곳이라 그 자리만 바뀐다.
- ASN으로는 반품사유를 넘기지 않는다(`rmk`를 넘기지 않는 것과 같은 판단 — 창고 작업엔 판정이 따로 있다).

### `ib_line.rjct_qty`

```sql
ALTER TABLE ib_line ADD COLUMN rjct_qty BIGINT DEFAULT 0 NOT NULL;
-- ck_ib_line_qty 에 rjct_qty >= 0 추가
```

- `rcvd_qty`는 **양품만**이다. 그래서 입고확정 조건 `ptawy == rcvd`, 적치 대상 산출, `IbLine#putaway` 전부 그대로다.
- 바뀌는 곳은 셋 — 과입고 검사 잔량 `expct − rcvd − rjct`, 결품 `expct − rcvd − rjct`(`IbOrderCfmResponse`), 진행 파생(아래).
- `IbLine#progressStatus`: `rcvd + rjct == 0`이면 `SCHEDULED`, `rcvd + rjct < expct`면 `RECEIVING`. 불량만 온 라인이 「예정」으로 보이면 안 된다. 이후 두 분기(`ptawy < rcvd` → `PTAWY_DRCT`, 아니면 `PTAWY_CMPL`)는 그대로.
- 원천 대사: `rjct_qty = SUM(qty)` of `inv_hist` where `ib_line_id = ?` and 로케이션이 반품존. `rcvd_qty`의 원천 식은 「로케이션이 반품존이 아닌」 조건이 붙는다.

### 반품존

시드에 존 3개 + 로케이션 각 1개.

| 존 | 온도 | 보관유형 | 업무구분 | 로케이션 |
|---|---|---|---|---|
| `RTN-DRY` 상온 반품존 | DRY | FLAT | RTNGS | `RTN-DRY-01` (STORAGE) |
| `RTN-CHL` 냉장 반품존 | CHL | FLAT | RTNGS | `RTN-CHL-01` (STORAGE) |
| `RTN-FRZ` 냉동 반품존 | FRZ | FLAT | RTNGS | `RTN-FRZ-01` (STORAGE) |

- 로케이션 유형이 `STORAGE`인 이유: 보류(`InvHldService`)가 v1에서 보관 로케이션만 받는다.
- 불량 도착지는 상수가 아니라 **해석**이다 — `RtngsLocResolver.resolve(prod)`: `biz_dvsn = RTNGS` 존 중 `tmp_zon = prod.tmpZon`인 존의 `STORAGE` 로케이션을 `pikng_prty` 순으로 하나. 없으면 예외("온도대 반품존이 없습니다"). `RCV-STAGE`처럼 코드값을 네 파일에 박는 방식을 늘리지 않는다.
- 반품존 판정 `inRtngsZon(loc)`은 이 resolver 한 곳에 둔다(피킹존 판정 `RplnDestinationResolver.inPikngZon`과 같은 자리 잡기). 검수 취소 · 검수 이력의 판정 열 · 원천 대사가 이걸 쓴다.

## 3. 검수 (`ReceivingService`)

### 요청

`ReceiveRequest.Line`에 추가 — `rjctQty`(입고단위), `rjctRsnCd`(`HLD_RSN`), `rjctRsnDscr`.

- `inspectQty + rjctQty ≥ 1` (기존 `inspectQty ≥ 1`을 완화). 합계 환산이 `expct − rcvd − rjct` 이내.
- 반품 ASN의 「입고단위」는 출고단위다 — 검수 화면과 서버 환산이 구분에 따라 `inbUomCd` / `outbUomCd`를 고른다(`IbOrder#rcvUomCd(prod)` 같은 한 곳).
- 제조일자는 양품 · 불량 공통 — 같은 Lot이다. 불량만 있는 라인도 유통기한 관리 상품이면 제조일자 필수.
- 정상 입고(`odr_dvsn ≠ RTNGS`)에 `rjctQty > 0`이 오면 거부 — 정상 검수는 「불합격 수량을 두지 않는다」(design.md)를 유지한다. 화면도 정상 입고엔 불량 열을 안 보여준다.
- `rjctQty > 0`이면 `rjctRsnCd` 필수, `ETC`일 때만 `rjctRsnDscr`.

### 저장

양품 경로는 지금 그대로(`RCV-STAGE`에 `RECEIVE`). 불량 경로:

1. `RtngsLocResolver.resolve(prod)`로 반품존 로케이션.
2. `invStore.increase(prod, rtngsLoc, lot, rjct, RECEIVE, ofIbLine(INBOUND, ibNo, ibLineId))` — 이력의 `ib_line_id`는 양품과 같다. 어느 쪽인지는 로케이션(반품존)이 말한다.
3. `ibLine.reject(rjct)`. 보류는 바로 걸지 않고 「보류 예정」으로 모아 둔다.
4. 요청의 모든 라인을 처리한 뒤 모아 둔 보류를 `InvHldService.holdOn(inv, qty, rsnCd, rsnDscr)`로 건다.

`increase`가 `findByKeyForUpdate`로 재고 행을 잠그므로(`InvStore#findOrCreate`) 별도 락이 필요 없다. 보류를 라인 루프 뒤로 미루는 이유는 채번(`HLD_NO`, `nbr_seq` 락) 때문이다 — 라인 사이에서 채번하면 뒤 라인의 재고 락이 채번 락 뒤에 오게 되어 「채번은 재고 락을 전부 잡은 뒤」(락 순서)를 어긴다. `rsn_dscr`는 `HLD_RSN` 규칙 그대로다(ETC일 때만) — 어느 반품에서 왔는지는 같은 재고 행의 `inv_hist RECEIVE`(입고번호)가 말한다.

`InvHldService.registerOne`의 「검증 → `invStore.hold` → `InvHld` + `InvHldAcrst` 저장」을 `holdOn`으로 꺼내 화면 등록과 검수가 같이 쓴다. 보류 채번도 거기서 한다.

### 검수 취소

`cancelReceipt`에서 이력의 로케이션이 반품존이면 불량 취소 경로:
- 보류가 살아 있으면 `inv.avalQty() < qty`라 기존 가용수량 검사에 걸린다. 메시지만 갈라준다 — "보류를 해제한 뒤 취소할 수 있습니다".
- `ibLine.cancelReject(qty)` + `ADJUST(−)`. 이력의 문서 참조는 양품 취소와 같다.

### 검수 규칙

- `InspectionContext`에 `rtngs`(boolean) 추가. `LOT_DATE_REVERSE.skipReason`이 `rtngs`면 "반품은 역순 제한 대상이 아님" — 오래된 Lot이 FEFO 앞으로 가는 것이 반품에서는 맞다. 실행 로그에 skip 사유로 남는다.
- `SHELF_LIFE_PCT`는 그대로 판정하되 **양품 수량이 0인 라인은 건너뛴다**(skipReason "양품 없음"). 불량으로 받는 물건에 잔여수명 하한을 걸 이유가 없다.
- `checkReceive`는 반품 여부를 `order.getOdrDvsn()`에서 읽는다.

### 검수 이력

`ReceiptResponse`에 `dcsn`(양품 `GOOD` / 불량 `RJCT`) 추가 — 로케이션이 반품존인지로 파생. 화면 열 「판정」.

## 4. 주변 영향

- **`OmsIbOrderService.confirm`**: `IbOrder.builder()`에 `vendor` 대신 `store`를 넘기는 분기와 환산 단위 분기. ASN 생성 경로는 여전히 여기 하나다.
- **적치 전략 적용대상**: `StrategyOptionService` · `PtawyStgyService`의 RTNGS 제외 필터를 없앤다(주석이 "재도입 시 필터만 풀면 된다"고 적어둔 자리). 반품 양품도 전략으로 적치한다.
- **할당 후보에서 반품존 제외**: `AlocQueryRepository` 후보 조회에 `zon.bizDvsn ≠ RTNGS` 한 줄. 보류를 풀자마자 반품존 재고가 FEFO 최우선으로 나가면 안 된다. 양품으로 재판정하려면 「보류 해제 → 재고 이동(보관존)」 두 단계다.
- **삭제 가드**: 점포 참조 검사기에 `oms_ib_order.store_id` · `ib_order.store_id` 추가. 상품 쪽은 기존 검사기가 이미 두 테이블을 본다.
- 적치 · 보류 · 이동 화면은 수정 없음. 보류 목록에 반품 불량 건이 다른 보류와 나란히 보인다.

## 5. API

| | 경로 |
|---|---|
| 입고주문 | 기존 `/oms/inbound-orders` 그대로 — 저장 요청에 `storeId` · `refOutbNo`, 라인에 `rsnCd` · `rsnDscr`; 응답에 `storeId` · `storeNm` · `refOutbNo` |
| 원 출고 후보 | 기존 `GET /outbound/orders?storeId=&status=SHIPPED` 재사용 — 라인은 `GET /outbound/orders/{id}` |
| 입고예정 목록 | 기존 `GET /inbound/asns`에 `odrDvsn` 조건, 응답에 `storeNm` |
| 검수 | 기존 `POST /inbound/asns/{id}/receive` — 요청 라인 필드 3개 추가 |

새 엔드포인트 없음.

## 6. 화면 (`C:\wms-front`)

- **입고주문 등록** — 구분을 `반품입고`로 고르면:
  1. 상대처 칸이 벤더 → **점포**로 바뀐다(선택 컴포넌트 교체). 그 아래 **원 출고**(선택)가 나타나고, 점포의 `SHIPPED` 출고 목록에서 고르면 라인이 들어오고 수량만 고친다.
  2. 라인에 **반품사유** · 사유상세(ETC) 열이 나타난다.
  3. 수량 열 단위 표시와 환산 열 기준이 출고단위로 바뀐다.
  구분을 다시 정상으로 바꾸면 점포 · 원 출고 · 사유를 비운다.
- **입고주문 관리**: 벤더 열 → 상대처, 구분 필터는 이미 있으면 그대로.
- **입고예정 관리**: 벤더 열 → 상대처, 구분 필터.
- **입고검수**: 반품 문서일 때만 불량수량 · 불량사유 · 사유상세 열. 저장 전 검사(합계 ≤ 잔량, 사유 필수)는 서버와 같은 식. 단위 라벨은 문서 구분을 따른다.
- **입고확정**: 불량 열, 결품 = 예정 − 검수 − 불량.
- **검수 이력**: 판정 열.
- 사이드바 · `screen-list.html`: 새 항목 없음. 입고주문 두 화면의 설명에 반품 구분을 추가.

## 7. 테스트

- `ReceivingServiceTest` — 반품: 양품/불량 분기(이력 2건 · 로케이션 · 보류 생성), 불량만 온 라인, 합계 과입고 거부, 정상 입고에 불량 거부, 불량 취소 시 보류 미해제 거부, 반품 환산이 출고단위.
- `OmsIbOrderServiceTest` — 반품 확정이 `store` · `RTNGS` · 출고단위 환산으로 `IbOrder`를 만드는지, 정상 확정은 그대로.
- `OmsIbOrderTest` · `IbOrderTest` — 구분과 상대 짝 검증(반품인데 벤더 / 정상인데 점포 / 둘 다 / 둘 다 아님 거부).
- `OmsIbOrderSaveRequestTest` — 반품 라인 사유 필수, 정상 라인 사유 거부.
- `InspectionRuleTest` — `rtngs`면 `LOT_DATE_REVERSE` skip, 양품 0이면 `SHELF_LIFE_PCT` skip.
- `IbLineTest` — `progressStatus`가 `rjct`를 반영.

## 8. 문서

- `docs/schema.sql`(`oms_ib_order` · `oms_ib_line` · `ib_order` · `ib_line` · 코드 · 존/로케이션 시드) + `docs/migration-rtngs.sql`(DO 블록, 존재 확인) + `docs/seed-dev.sql`.
- `docs/design.md` 「반품입고」 절 — 왜 입고주문의 구분인가(별도 문서가 아닌 이유), 왜 불량은 스테이징을 거치지 않는가, 왜 `rcvd`는 양품만인가, 검수 규칙을 어떻게 갈랐나, 반품출고를 미룬 이유. 「v1에서 제외하는 것」 표의 반품 행을 승격 표기. 「발주구분은 표시·분류용」 문단을 갱신 — 이제 상대처 · 단위 · 검수 판정을 가른다.
- `docs/screen-list.html`, `CLAUDE.md`의 `RCV-STAGE` 문단 옆에 반품존은 상수가 아니라 resolver라는 한 줄.
- 사전 추가 없음 — 반품 `RTNGS` · 사유 `RSN` · 참조 `REF` · 판정 `DCSN` 모두 있다.

## 9. 순서

1. 스키마 · 마이그레이션 · 시드 · 엔티티(`OmsIbOrder.store/refOutbNo` · `OmsIbLine.rsnCd` · `IbOrder.store` · `IbLine.rjctQty`) + 엔티티 테스트
2. `RtngsLocResolver` · `InvHldService.holdOn` · 검수 규칙 skip + 테스트
3. `ReceivingService` 반품 분기 · 취소 · 이력 판정 + 테스트
4. `OmsIbOrderService` 저장 검증 · 확정 분기 · 점포 삭제 가드 + 테스트
5. 적치 전략 대상 · 할당 후보 제외
6. 프론트 — 입고주문 등록/관리 → 입고예정 · 검수 · 확정 · 이력
7. 문서
