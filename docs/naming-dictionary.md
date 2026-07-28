# 표준 단어 사전

`CLAUDE.md`의 「명명규칙」이 가리키는 단어 사전이다. **변수명과 필드명은 여기 있는 단어를 조합해서 만든다.**

DB 컬럼명은 `docs/schema.sql`이 자체 약어 사전을 이미 들고 있다. 필드와 컬럼이 1:1로 붙어 있는 구조라 두 사전이 겹치는 자리가 생기는데, 어긋나는 항목은 아래 「이미 쓰고 있는 이름과 어긋나는 곳」에 모아뒀다.

216개 단어이며 한글·약어 모두 중복이 없다.

## 이름 만드는 규칙

**1. 재료는 약어다.** 이름은 `약어`를 이어 붙여 만든다. 영문명은 그 약어가 무슨 뜻인지 밝히려고 적어둔 것이지 이름의 재료가 아니다.

| 조합 | Java | DB 컬럼 |
|---|---|---|
| 예정(`EXPCT`) + 수량(`QTY`) | `expctQty` | `expct_qty` |
| 로트(`LOT`) + 번호(`NO`) | `lotNo` | `lot_no` |
| 로케이션(`LOC`) + 코드(`CD`) | `locCd` | `loc_cd` |

**2. 단어는 같고 표기만 계층마다 다르다.**

- Java 필드 · 지역변수 · 파라미터 → lowerCamelCase (`expctQty`)
- Java 클래스 → UpperCamelCase (`IbLine`)
- enum 상수 → UPPER_SNAKE_CASE (`PARTIALLY_ALLOC` 같은 상태는 만들지 않는다 — `CLAUDE.md` 「상태와 수량의 분담」 참고)
- DB 테이블 · 컬럼 → snake_case (`expct_qty`)

**3. 어순은 수식어 → 핵심어.** 마지막 단어가 그 이름의 정체를 말한다 — `수량(QTY)` · `일자(DE)` · `코드(CD)` · `명(NM)` · `여부(YN)` · `아이디(ID)`.

**4. 불리언은 `여부(YN)`로 끝낸다.** DB는 `use_yn`, Java는 `useYn`. `is~` / `has~` 접두는 쓰지 않는다.

**5. PK · FK 이름은 `CLAUDE.md` 규칙이 이 사전보다 우선한다.** PK는 `{테이블명}_id`, FK 컬럼은 참조 테이블의 PK명을 그대로 쓴다. 테이블 접두(`OMS_*` · `IB_*` · `OUTB_*` · `INV*`)도 마찬가지로 그대로 둔다.

**6. 사전에 없는 단어는 이름에 쓰지 않는다.** 새 단어가 필요하면 이 파일에 먼저 추가한다 — 한글 · 약어 · 영문명 세 칸을 채우고, 기존 약어와 겹치지 않는지 확인한 뒤 쓴다.

## 단어 사전 (가나다순)

| 단어 | 약어 | 영문명 |
|---|---|---|
| ID | `ID` | Identification |
| 가능 | `PSBL` | Possible |
| 가로 | `WDTH` | Width |
| 가용 | `AVAL` | Available |
| 값 | `VAL` | Value |
| 거래처 | `VNDR` | Vendor |
| 결품 | `SHOTGE` | Shortage |
| 경도 | `LNGT` | Longitude |
| 경로 | `PTH` | Path |
| 고객 | `CUST` | Customer |
| 고정 | `FXNG` | Fixing |
| 공급사 | `SUPLER` | Supplier |
| 공통 | `COMN` | Common |
| 관리자 | `ADMR` | Administrator |
| 구분 | `DVSN` | Division |
| 구분자 | `DLMT` | Delimiter |
| 구성 | `CMPS` | Composition |
| 구역 | `ARA` | Area |
| 국문 | `KOR` | Korean |
| 권역 | `RGN` | Region |
| 규격 | `STND` | Standard |
| 그룹 | `GRP` | Group |
| 기간 | `TRM` | Term |
| 기능 | `FUNC` | Function |
| 기본 | `BAS` | Basic |
| 기사 | `DRVR` | Driver |
| 기준 | `CRTR` | Criteria |
| 기타 | `ETC` | Etc |
| 기한 | `PERD` | Period |
| 길이 | `LEN` | Length |
| 납품처 | `DLVPLC` | Delivery Place |
| 낱개 | `EA` | Each |
| 내용 | `CONTT` | Contents |
| 높이 | `HGT` | Height |
| 단 | `STP` | Step |
| 단계 | `STG` | Stage |
| 단위 | `UNT` | Unit |
| 담당자 | `PIC` | Person In Charge |
| 당 | `PR` | Per |
| 대상 | `TGT` | Target |
| 대표 | `REP` | Representative |
| 대표자 | `CEO` | Chief executive officer |
| 데이터 | `DAT` | Data |
| 동적 | `DYNC` | Dynamic |
| 뒤 | `BK` | Back |
| 등록 | `REG` | Registration |
| 등록자 | `REGR` | Register |
| 디폴트 | `DFT` | Default |
| 라인 | `LN` | Line |
| 로그 | `LOG` | Log |
| 로케이션 | `LOC` | Location |
| 로트 | `LOT` | Lot |
| 마감 | `CLOS` | Closing |
| 마지막 | `LAST` | Last |
| 매핑 | `MPP` | Mapping |
| 메뉴 | `MNU` | Menu |
| 명 | `NM` | Name |
| 모듈 | `MDUL` | Module |
| 물류 | `LGIST` | Logistics |
| 바코드 | `BARCD` | Barcode |
| 박스 | `BX` | Box |
| 반품 | `RTNGS` | Returning Goods |
| 배송 | `DLVR` | Delivery |
| 배송처 | `DLADDR` | Delivery Address |
| 번호 | `NO` | Number Order |
| 법인 | `CORP` | Corporation |
| 변경 | `CHNG` | Change |
| 보관 | `STRG` | Storage |
| 보류 | `HLD` | Holding |
| 보충 | `SPMT` | Supplement |
| 부서 | `DEPT` | Department |
| 부자재 | `SSMTRL` | Subsidiary materials |
| 부피 | `VOL` | Volume |
| 분류 | `CLS` | Classification |
| 비고 | `RMK` | Remark |
| 비교 | `CMPR` | Compare |
| 비밀번호 | `PWD` | Password |
| 사업자 | `BIZMN` | Businessman |
| 사용 | `US` | Use |
| 사용자 | `USR` | User |
| 사원 | `EMP` | Employee |
| 사유 | `RSN` | Reason |
| 상세 | `DTL` | Detail |
| 상위 | `UPR` | Upper |
| 상태 | `ST` | Status |
| 상품 | `PROD` | Product |
| 생산 | `PRODN` | Production |
| 생성 | `CRT` | Creation |
| 서비스 | `SVC` | Service |
| 설명 | `DSCR` | Description |
| 설정 | `CFG` | Configuration |
| 세로 | `VRTCL` | Vertical |
| 센터 | `CENT` | Center |
| 센터장 | `CENTCHF` | Center Chief |
| 속성 | `ATTR` | Attribute |
| 수 | `CNT` | Count |
| 수량 | `QTY` | Quantity |
| 수신 | `RCPTN` | Reception |
| 수정 | `UPD` | Update |
| 수정자 | `UPDR` | Updater |
| 순서 | `SEQ` | Sequence |
| 순위 | `RNK` | Rank |
| 시스템 | `SYS` | System |
| 시작 | `STRT` | Start |
| 실적 | `ACRST` | Actual Result |
| 실패 | `FAIL` | Failure |
| 암호화 | `ENCR` | Encryption |
| 앞 | `FNT` | Front |
| 약어 | `ABRV` | Abbreviation |
| 언어 | `LANG` | Language |
| 업무 | `BIZ` | Business |
| 업종 | `TPBIZ` | Type Of Business |
| 업태 | `BIZCON` | Business Conditions |
| 여부 | `YN` | Yes Or No |
| 연계 | `IF` | Interface |
| 열 | `CLM` | Column |
| 영어 | `ENG` | English |
| 예외 | `EXCP` | Exception |
| 예정 | `EXPCT` | Expectation |
| 오류 | `ERR` | Error |
| 온도 | `TMP` | Temperature |
| 완료 | `CMPL` | Completion |
| 요청 | `REQ` | Request |
| 용적 | `CPCT` | Capacity |
| 우선 | `PRTY` | Priority |
| 우편 | `POST` | Post |
| 운송사 | `TRSCP` | A Transport(Shipping, Freight) Company |
| 운송장 | `WAYBIL` | Waybill |
| 운영 | `OPER` | Operation |
| 웨이브 | `WAV` | Wave |
| 위도 | `LTTD` | Latitude |
| 유통 | `DSTB` | Distribution |
| 유형 | `TYP` | Type |
| 유효 | `VLDT` | Validity |
| 응답 | `RSPS` | Response |
| 이동 | `MOV` | Move |
| 이메일 | `EMAIL` | Id |
| 일련번호 | `SN` | Serial Number |
| 일시 | `DT` | Date And Time |
| 일자 | `DE` | Date |
| 입고 | `INB` | Inbound |
| 입력 | `INS` | Insert |
| 자동 | `ATO` | Automatic |
| 자리 | `PSTN` | Position |
| 작업 | `WRK` | Work |
| 장비 | `EQP` | Equipment |
| 재고 | `INVN` | Inventory |
| 적용 | `APLY` | Apply |
| 적재 | `LOD` | Load |
| 적치 | `PTAWY` | Put Away |
| 전 | `BFR` | Before |
| 전략 | `STGY` | Strategy |
| 전송 | `TRNS` | Transmission |
| 전표 | `SLP` | Slip |
| 전화 | `TEL` | Telephone |
| 접속 | `CONN` | Connection |
| 접수 | `ACCP` | Acceptance |
| 정렬 | `SRT` | Sort |
| 정보 | `INF` | Information |
| 정산 | `EXCAL` | Exact Calculation |
| 정의 | `DEF` | Definition |
| 제약 | `RSTRCT` | Restriction |
| 조건 | `COND` | Condition |
| 조정 | `ADJ` | Adjustment |
| 조회 | `INQ` | Inquiry |
| 존 | `ZON` | Zone |
| 종료 | `END` | End |
| 좌우정렬 | `ALGN` | Align |
| 좌표 | `CRDN` | Coordinate |
| 주문 | `ODR` | Order |
| 주문처 | `ODRPLC` | Order Place |
| 주소 | `ADDR` | Address |
| 중량 | `WGT` | Weight |
| 지시 | `DRCT` | Direction |
| 진행 | `PRGR` | Progress |
| 차량 | `VHCL` | Vehicle |
| 차수 | `TMO` | Time Ordinary |
| 참조 | `RFN` | Reference |
| 채번 | `NBR` | Numbering |
| 처 | `PLC` | Place |
| 처리 | `PROC` | Process |
| 총 | `TOT` | The Total |
| 최대 | `MAX` | Maximum |
| 최소 | `MIN` | Minimum |
| 출고 | `OUTB` | Outbound |
| 출고처 | `OUTBPLC` | Outbound Place |
| 출하 | `SHMT` | Shipment |
| 취소 | `CNCL` | Cancellation |
| 코드 | `CD` | Code |
| 쿼리 | `QRY` | Query |
| 키 | `KY` | Key |
| 통합 | `INTG` | Integration |
| 툴팁 | `TLTP` | Tooltip |
| 파라미터 | `PARA` | Parameter |
| 팝업 | `PPUP` | Popup |
| 팩스 | `FAX` | Fax |
| 편수 | `FLTNO` | Fltno |
| 포장 | `PKGNG` | Packaging |
| 표시 | `MRK` | Marking |
| 프로그램 | `PGM` | Program |
| 피킹 | `PIKNG` | Picking |
| 필드 | `FLD` | Field |
| 필터 | `FLTR` | Filter |
| 할당 | `ALOC` | Allocation |
| 해제 | `RLZ` | Release |
| 행 | `ROW` | Row |
| 현재 | `NOW` | Now |
| 혼적 | `MXLOD` | Mixed Loading |
| 화물 | `CAGO` | Cargo |
| 화주 | `CNSG` | Consignor |
| 확정 | `CFM` | Confirm |
| 회사 | `COMP` | Company |
| 회차 | `TME` | Times |
| 후 | `AFT` | After |
| 휴대전화 | `HP` | HandPhone |
| 희망 | `HOP` | Hope |


## 약어 역인덱스 (알파벳순)

코드에서 만난 약어를 되짚을 때 쓴다.

| 약어 | 단어 | 영문명 |
|---|---|---|
| `ABRV` | 약어 | Abbreviation |
| `ACCP` | 접수 | Acceptance |
| `ACRST` | 실적 | Actual Result |
| `ADDR` | 주소 | Address |
| `ADJ` | 조정 | Adjustment |
| `ADMR` | 관리자 | Administrator |
| `AFT` | 후 | After |
| `ALGN` | 좌우정렬 | Align |
| `ALOC` | 할당 | Allocation |
| `APLY` | 적용 | Apply |
| `ARA` | 구역 | Area |
| `ATO` | 자동 | Automatic |
| `ATTR` | 속성 | Attribute |
| `AVAL` | 가용 | Available |
| `BARCD` | 바코드 | Barcode |
| `BAS` | 기본 | Basic |
| `BFR` | 전 | Before |
| `BIZ` | 업무 | Business |
| `BIZCON` | 업태 | Business Conditions |
| `BIZMN` | 사업자 | Businessman |
| `BK` | 뒤 | Back |
| `BX` | 박스 | Box |
| `CAGO` | 화물 | Cargo |
| `CD` | 코드 | Code |
| `CENT` | 센터 | Center |
| `CENTCHF` | 센터장 | Center Chief |
| `CEO` | 대표자 | Chief executive officer |
| `CFG` | 설정 | Configuration |
| `CFM` | 확정 | Confirm |
| `CHNG` | 변경 | Change |
| `CLM` | 열 | Column |
| `CLOS` | 마감 | Closing |
| `CLS` | 분류 | Classification |
| `CMPL` | 완료 | Completion |
| `CMPR` | 비교 | Compare |
| `CMPS` | 구성 | Composition |
| `CNCL` | 취소 | Cancellation |
| `CNSG` | 화주 | Consignor |
| `CNT` | 수 | Count |
| `COMN` | 공통 | Common |
| `COMP` | 회사 | Company |
| `COND` | 조건 | Condition |
| `CONN` | 접속 | Connection |
| `CONTT` | 내용 | Contents |
| `CORP` | 법인 | Corporation |
| `CPCT` | 용적 | Capacity |
| `CRDN` | 좌표 | Coordinate |
| `CRT` | 생성 | Creation |
| `CRTR` | 기준 | Criteria |
| `CUST` | 고객 | Customer |
| `DAT` | 데이터 | Data |
| `DE` | 일자 | Date |
| `DEF` | 정의 | Definition |
| `DEPT` | 부서 | Department |
| `DFT` | 디폴트 | Default |
| `DLADDR` | 배송처 | Delivery Address |
| `DLMT` | 구분자 | Delimiter |
| `DLVPLC` | 납품처 | Delivery Place |
| `DLVR` | 배송 | Delivery |
| `DRCT` | 지시 | Direction |
| `DRVR` | 기사 | Driver |
| `DSCR` | 설명 | Description |
| `DSTB` | 유통 | Distribution |
| `DT` | 일시 | Date And Time |
| `DTL` | 상세 | Detail |
| `DVSN` | 구분 | Division |
| `DYNC` | 동적 | Dynamic |
| `EA` | 낱개 | Each |
| `EMAIL` | 이메일 | Id |
| `EMP` | 사원 | Employee |
| `ENCR` | 암호화 | Encryption |
| `END` | 종료 | End |
| `ENG` | 영어 | English |
| `EQP` | 장비 | Equipment |
| `ERR` | 오류 | Error |
| `ETC` | 기타 | Etc |
| `EXCAL` | 정산 | Exact Calculation |
| `EXCP` | 예외 | Exception |
| `EXPCT` | 예정 | Expectation |
| `FAIL` | 실패 | Failure |
| `FAX` | 팩스 | Fax |
| `FLD` | 필드 | Field |
| `FLTNO` | 편수 | Fltno |
| `FLTR` | 필터 | Filter |
| `FNT` | 앞 | Front |
| `FUNC` | 기능 | Function |
| `FXNG` | 고정 | Fixing |
| `GRP` | 그룹 | Group |
| `HGT` | 높이 | Height |
| `HLD` | 보류 | Holding |
| `HOP` | 희망 | Hope |
| `HP` | 휴대전화 | HandPhone |
| `ID` | ID | Identification |
| `IF` | 연계 | Interface |
| `INB` | 입고 | Inbound |
| `INF` | 정보 | Information |
| `INQ` | 조회 | Inquiry |
| `INS` | 입력 | Insert |
| `INTG` | 통합 | Integration |
| `INVN` | 재고 | Inventory |
| `KOR` | 국문 | Korean |
| `KY` | 키 | Key |
| `LANG` | 언어 | Language |
| `LAST` | 마지막 | Last |
| `LEN` | 길이 | Length |
| `LGIST` | 물류 | Logistics |
| `LN` | 라인 | Line |
| `LNGT` | 경도 | Longitude |
| `LOC` | 로케이션 | Location |
| `LOD` | 적재 | Load |
| `LOG` | 로그 | Log |
| `LOT` | 로트 | Lot |
| `LTTD` | 위도 | Latitude |
| `MAX` | 최대 | Maximum |
| `MDUL` | 모듈 | Module |
| `MIN` | 최소 | Minimum |
| `MNU` | 메뉴 | Menu |
| `MOV` | 이동 | Move |
| `MPP` | 매핑 | Mapping |
| `MRK` | 표시 | Marking |
| `MXLOD` | 혼적 | Mixed Loading |
| `NBR` | 채번 | Numbering |
| `NM` | 명 | Name |
| `NO` | 번호 | Number Order |
| `NOW` | 현재 | Now |
| `ODR` | 주문 | Order |
| `ODRPLC` | 주문처 | Order Place |
| `OPER` | 운영 | Operation |
| `OUTB` | 출고 | Outbound |
| `OUTBPLC` | 출고처 | Outbound Place |
| `PARA` | 파라미터 | Parameter |
| `PERD` | 기한 | Period |
| `PGM` | 프로그램 | Program |
| `PIC` | 담당자 | Person In Charge |
| `PIKNG` | 피킹 | Picking |
| `PKGNG` | 포장 | Packaging |
| `PLC` | 처 | Place |
| `POST` | 우편 | Post |
| `PPUP` | 팝업 | Popup |
| `PR` | 당 | Per |
| `PRGR` | 진행 | Progress |
| `PROC` | 처리 | Process |
| `PROD` | 상품 | Product |
| `PRODN` | 생산 | Production |
| `PRTY` | 우선 | Priority |
| `PSBL` | 가능 | Possible |
| `PSTN` | 자리 | Position |
| `PTAWY` | 적치 | Put Away |
| `PTH` | 경로 | Path |
| `PWD` | 비밀번호 | Password |
| `QRY` | 쿼리 | Query |
| `QTY` | 수량 | Quantity |
| `RCPTN` | 수신 | Reception |
| `REG` | 등록 | Registration |
| `REGR` | 등록자 | Register |
| `REP` | 대표 | Representative |
| `REQ` | 요청 | Request |
| `RFN` | 참조 | Reference |
| `RGN` | 권역 | Region |
| `RLZ` | 해제 | Release |
| `RMK` | 비고 | Remark |
| `RNK` | 순위 | Rank |
| `ROW` | 행 | Row |
| `RSN` | 사유 | Reason |
| `RSPS` | 응답 | Response |
| `RSTRCT` | 제약 | Restriction |
| `RTNGS` | 반품 | Returning Goods |
| `SEQ` | 순서 | Sequence |
| `SHMT` | 출하 | Shipment |
| `SHOTGE` | 결품 | Shortage |
| `SLP` | 전표 | Slip |
| `SN` | 일련번호 | Serial Number |
| `SPMT` | 보충 | Supplement |
| `SRT` | 정렬 | Sort |
| `SSMTRL` | 부자재 | Subsidiary materials |
| `ST` | 상태 | Status |
| `STG` | 단계 | Stage |
| `STGY` | 전략 | Strategy |
| `STND` | 규격 | Standard |
| `STP` | 단 | Step |
| `STRG` | 보관 | Storage |
| `STRT` | 시작 | Start |
| `SUPLER` | 공급사 | Supplier |
| `SVC` | 서비스 | Service |
| `SYS` | 시스템 | System |
| `TEL` | 전화 | Telephone |
| `TGT` | 대상 | Target |
| `TLTP` | 툴팁 | Tooltip |
| `TME` | 회차 | Times |
| `TMO` | 차수 | Time Ordinary |
| `TMP` | 온도 | Temperature |
| `TOT` | 총 | The Total |
| `TPBIZ` | 업종 | Type Of Business |
| `TRM` | 기간 | Term |
| `TRNS` | 전송 | Transmission |
| `TRSCP` | 운송사 | A Transport(Shipping, Freight) Company |
| `TYP` | 유형 | Type |
| `UNT` | 단위 | Unit |
| `UPD` | 수정 | Update |
| `UPDR` | 수정자 | Updater |
| `UPR` | 상위 | Upper |
| `US` | 사용 | Use |
| `USR` | 사용자 | User |
| `VAL` | 값 | Value |
| `VHCL` | 차량 | Vehicle |
| `VLDT` | 유효 | Validity |
| `VNDR` | 거래처 | Vendor |
| `VOL` | 부피 | Volume |
| `VRTCL` | 세로 | Vertical |
| `WAV` | 웨이브 | Wave |
| `WAYBIL` | 운송장 | Waybill |
| `WDTH` | 가로 | Width |
| `WGT` | 중량 | Weight |
| `WRK` | 작업 | Work |
| `YN` | 여부 | Yes Or No |
| `ZON` | 존 | Zone |


## 이미 쓰고 있는 이름과 어긋나는 곳 (확인 필요)

`docs/schema.sql`과 엔티티가 이미 쓰는 약어 중 이 사전과 다른 것들이 있다. **`CLAUDE.md`는 문서와 코드가 어긋날 때 조용히 한쪽으로 맞추지 말라고 못박았으니, 아래 항목은 손대기 전에 물어볼 것.** 이 파일을 근거로 기존 컬럼명을 일괄 개명하지 않는다.

| 단어 | 이 사전 | 지금 쓰는 이름 | 나타나는 곳 |
|---|---|---|---|
| 적치 | `PTAWY` | `ptwy` | `ib_line.ptwy_qty`, `IbLine.ptwyQty` |
| 할당 | `ALOC` | `alloc` | `outb_line.alloc_qty`, `outb_alloc` |
| 재고 | `INVN` | `inv` | `inv`, `inv_hist`, `inv_id` |
| 상태 | `ST` | `status` | `ib_order.status` 등 헤더 전반 |
| 설명 | `DSCR` | `description` | `code_group.description` |
| 유형 | `TYP` | `type` | `loc_type`, `ref_doc_type`, `tx_type` |
| 존 | `ZON` | `zone` | `zone_cd`, `temp_zone` |
| 온도 | `TMP` | `temp` | `temp_zone` |
| 코드 | `CD` | `code` | `code_detail.code_cd` · `code_nm` (테이블명 `code_detail` 자체) |
| 그룹 | `GRP` | `group` | `code_group.group_cd` · `group_nm` (테이블명 `code_group` 자체) |
| 웨이브 | `WAV` | `wave` | `wave_no`, `wave_id`, `outb_wave` |
| 피킹 | `PIKNG` | `pick` / `picked` | `pick_prty`, `picked_qty` |
| 정렬 + 순서 | `SRT` + `SEQ` | `sort_ord` (`ord`는 사전에 없다) | `code_detail.sort_ord` |
| 사용 | `US` | `use` | `code_detail.use_yn` |
| 일자 · 일시 | `DE` · `DT` | `_dt` / `_at` 혼용 | `expct_dt`, `order_dt` vs `created_at`, `closed_at` |
| 생성 · 수정 | `CRT` · `UPD` | `created` · `updated` | `BaseEntity`의 감사 컬럼 4종 |

사전에 아예 없는데 스키마가 쓰는 약어도 있다 — `rcvd`(received) · `rjct`(rejected) · `hist`(history) · `ord`(order) · `tx`(transaction) · `ref_doc` · `on_hand` · `mfg` · `expiry` · `shelf_life`. 이 중 `expct` · `rcvd` · `rjct` · `ptwy` · `alloc` · `loc` · `hist`는 `CLAUDE.md`와 `docs/schema.sql` 머리말에 별도 약어 사전으로 이미 박혀 있다. 즉 **약어 사전이 지금 두 벌**이고, `적치`와 `할당` 두 단어에서 서로 충돌한다.

## 원본 표에 대한 메모

옮기면서 손대지 않고 그대로 둔 것들이다.

- `이메일(EMAIL)`의 영문명이 원본에 `Id`로 적혀 있다. `Email`의 오기로 보이지만 임의로 고치지 않았다.
- `편수(FLTNO)`의 영문명 `Fltno`는 영문 단어가 아니라 약어를 그대로 옮긴 값이다. 이것도 원본대로 뒀다.
- `참조`의 약어는 `REF`가 아니라 **`RFN`**이다. 오타처럼 보여서 확대해 다시 확인했고 원본이 실제로 `RFN`이다. 같은 이유로 `해제 RLZ`(`RLS` 아님) · `화물 CAGO`(`CRGO` 아님) · `일자 DE`(`DT`는 `일시`가 이미 쓴다) · `회차 TME` / `차수 TMO`도 확인을 거친 값이다.
- 원본의 `중복여부` 컬럼은 216행 전부 `N`이라 옮기지 않았다. 실제로 한글·약어 어느 쪽에도 중복이 없음을 확인했다.
