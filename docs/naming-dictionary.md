# 표준 단어 사전

`CLAUDE.md`의 「명명규칙」이 가리키는 단어 사전이다. **변수명과 필드명은 여기 있는 단어를 조합해서 만든다.**

DB 컬럼명은 `docs/schema.sql`이 자체 약어 사전을 이미 들고 있다. 필드와 컬럼이 1:1로 붙어 있는 구조라 두 사전이 겹치는 자리가 생기는데, 어긋나는 항목은 아래 「이미 쓰고 있는 이름과 어긋나는 곳」에 모아뒀다.

247개 단어이며 한글·약어 모두 중복이 없다. (2026-08-02 전략 시스템 설계에서 15개 추가 · 2026-08-03 재고조사 설계에서 `실사 STKTK` 1개 추가 · 2026-08-13 재고 로트변경 설계에서 `신규 NEW` 1개 추가 · 2026-08-21 로케이션 점유 맵 설계에서 `맵 MAP` 1개 추가 · 2026-08-23 상품 이미지 설계에서 `이미지 IMG` · `URL URL` 2개 추가 · 2026-08-25 자동발주 설계에서 `리드타임 LEAD` 1개 추가 · 2026-08-26 서버 페이징 설계에서 `페이지 PAGE` · `크기 SIZE` 2개 추가 · 2026-08-26 인증·역할 설계에서 `로그인 LOGIN` · `역할 ROLE` 2개 추가)

> 개수는 두 표를 실측한 값이다. 2026-08-23에 맞췄다 — `MAP`이 가나다순 표에만 있고 역인덱스에 빠져 있었고, 머리말 숫자도 실제 행 수보다 낮게 밀려 있었다. 단어를 추가하면 **두 표에 모두** 넣고 이 숫자를 함께 올린다.

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
| URL | `URL` | Uniform Resource Locator |
| 가능 | `PSBL` | Possible |
| 가로 | `WDTH` | Width |
| 가용 | `AVAL` | Available |
| 값 | `VAL` | Value |
| 거래처 | `VNDR` | Vendor |
| 검수 | `INSP` | Inspection |
| 결과 | `RSLT` | Result |
| 결품 | `SHOTGE` | Shortage |
| 경도 | `LNGT` | Longitude |
| 경로 | `PTH` | Path |
| 계량단위 | `UOM` | Unit of Measure |
| 고객 | `CUST` | Customer |
| 고정 | `FXNG` | Fixing |
| 공급사 | `SUPLER` | Supplier |
| 공통 | `COMN` | Common |
| 관리자 | `ADMR` | Administrator |
| 구분 | `DVSN` | Division |
| 구분자 | `DLMT` | Delimiter |
| 구성 | `CMPS` | Composition |
| 구성요소 | `CMPNT` | Component |
| 구역 | `ARA` | Area |
| 국문 | `KOR` | Korean |
| 권역 | `RGN` | Region |
| 규격 | `STND` | Standard |
| 규칙 | `RULE` | Rule |
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
| 로그인 | `LOGIN` | Login |
| 로케이션 | `LOC` | Location |
| 로트 | `LOT` | Lot |
| 리드타임 | `LEAD` | Lead Time |
| 리비전 | `RVSN` | Revision |
| 마감 | `CLOS` | Closing |
| 마지막 | `LAST` | Last |
| 매핑 | `MPP` | Mapping |
| 맵 | `MAP` | Map |
| 메뉴 | `MNU` | Menu |
| 명 | `NM` | Name |
| 모듈 | `MDUL` | Module |
| 물류 | `LGIST` | Logistics |
| 바코드 | `BARCD` | Barcode |
| 박스 | `BX` | Box |
| 반품 | `RTNGS` | Returning Goods |
| 방식 | `MTHD` | Method |
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
| 분배 | `DSTRB` | Distribute |
| 분할 | `SPLT` | Split |
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
| 스냅샷 | `SNPSHT` | Snapshot |
| 슬롯 | `SLOT` | Slot |
| 시스템 | `SYS` | System |
| 시작 | `STRT` | Start |
| 신규 | `NEW` | New |
| 실사 | `STKTK` | Stock Taking |
| 실적 | `ACRST` | Actual Result |
| 실패 | `FAIL` | Failure |
| 실행 | `EXEC` | Execution |
| 암호화 | `ENCR` | Encryption |
| 앞 | `FNT` | Front |
| 약어 | `ABRV` | Abbreviation |
| 언어 | `LANG` | Language |
| 업무 | `BIZ` | Business |
| 업종 | `TPBIZ` | Type Of Business |
| 업태 | `BIZCON` | Business Conditions |
| 여부 | `YN` | Yes Or No |
| 역할 | `ROLE` | Role |
| 연계 | `IF` | Interface |
| 열 | `CLM` | Column |
| 영어 | `ENG` | English |
| 예외 | `EXCP` | Exception |
| 예정 | `EXPCT` | Expectation |
| 오류 | `ERR` | Error |
| 온도 | `TMP` | Temperature |
| 완료 | `CMPL` | Completion |
| 요약 | `SMRY` | Summary |
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
| 이미지 | `IMG` | Image |
| 일련번호 | `SN` | Serial Number |
| 일시 | `DT` | Date And Time |
| 일자 | `DE` | Date |
| 입고 | `INB` | Inbound |
| 입력 | `INS` | Insert |
| 자동 | `ATO` | Automatic |
| 자리 | `PSTN` | Position |
| 자릿수 | `DGT` | Digit |
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
| 접두 | `PRFX` | Prefix |
| 접속 | `CONN` | Connection |
| 접수 | `ACCP` | Acceptance |
| 정렬 | `SRT` | Sort |
| 정보 | `INF` | Information |
| 정산 | `EXCAL` | Exact Calculation |
| 정의 | `DEF` | Definition |
| 정책 | `PLCY` | Policy |
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
| 참조 | `REF` | Reference |
| 채번 | `NBR` | Numbering |
| 처 | `PLC` | Place |
| 처리 | `PROC` | Process |
| 총 | `TOT` | The Total |
| 최대 | `MAX` | Maximum |
| 최소 | `MIN` | Minimum |
| 추적 | `TRC` | Trace |
| 출고 | `OUTB` | Outbound |
| 출고처 | `OUTBPLC` | Outbound Place |
| 출하 | `SHMT` | Shipment |
| 취소 | `CNCL` | Cancellation |
| 코드 | `CD` | Code |
| 쿼리 | `QRY` | Query |
| 크기 | `SIZE` | Size |
| 키 | `KY` | Key |
| 통합 | `INTG` | Integration |
| 툴팁 | `TLTP` | Tooltip |
| 트리거 | `TRGR` | Trigger |
| 파라미터 | `PARA` | Parameter |
| 판정 | `DCSN` | Decision |
| 팝업 | `PPUP` | Popup |
| 패턴 | `PTRN` | Pattern |
| 팩스 | `FAX` | Fax |
| 페이지 | `PAGE` | Page |
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
| 환산 | `CNVR` | Conversion |
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
| `CMPNT` | 구성요소 | Component |
| `CMPR` | 비교 | Compare |
| `CMPS` | 구성 | Composition |
| `CNCL` | 취소 | Cancellation |
| `CNSG` | 화주 | Consignor |
| `CNT` | 수 | Count |
| `CNVR` | 환산 | Conversion |
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
| `DCSN` | 판정 | Decision |
| `DE` | 일자 | Date |
| `DEF` | 정의 | Definition |
| `DEPT` | 부서 | Department |
| `DFT` | 디폴트 | Default |
| `DGT` | 자릿수 | Digit |
| `DLADDR` | 배송처 | Delivery Address |
| `DLMT` | 구분자 | Delimiter |
| `DLVPLC` | 납품처 | Delivery Place |
| `DLVR` | 배송 | Delivery |
| `DRCT` | 지시 | Direction |
| `DRVR` | 기사 | Driver |
| `DSCR` | 설명 | Description |
| `DSTB` | 유통 | Distribution |
| `DSTRB` | 분배 | Distribute |
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
| `EXEC` | 실행 | Execution |
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
| `IMG` | 이미지 | Image |
| `INB` | 입고 | Inbound |
| `INF` | 정보 | Information |
| `INQ` | 조회 | Inquiry |
| `INS` | 입력 | Insert |
| `INSP` | 검수 | Inspection |
| `INTG` | 통합 | Integration |
| `INVN` | 재고 | Inventory |
| `KOR` | 국문 | Korean |
| `KY` | 키 | Key |
| `LANG` | 언어 | Language |
| `LAST` | 마지막 | Last |
| `LEAD` | 리드타임 | Lead Time |
| `LEN` | 길이 | Length |
| `LGIST` | 물류 | Logistics |
| `LN` | 라인 | Line |
| `LNGT` | 경도 | Longitude |
| `LOC` | 로케이션 | Location |
| `LOD` | 적재 | Load |
| `LOG` | 로그 | Log |
| `LOGIN` | 로그인 | Login |
| `LOT` | 로트 | Lot |
| `LTTD` | 위도 | Latitude |
| `MAX` | 최대 | Maximum |
| `MAP` | 맵 | Map |
| `MDUL` | 모듈 | Module |
| `MIN` | 최소 | Minimum |
| `MNU` | 메뉴 | Menu |
| `MOV` | 이동 | Move |
| `MPP` | 매핑 | Mapping |
| `MRK` | 표시 | Marking |
| `MTHD` | 방식 | Method |
| `MXLOD` | 혼적 | Mixed Loading |
| `NBR` | 채번 | Numbering |
| `NEW` | 신규 | New |
| `NM` | 명 | Name |
| `NO` | 번호 | Number Order |
| `NOW` | 현재 | Now |
| `ODR` | 주문 | Order |
| `ODRPLC` | 주문처 | Order Place |
| `OPER` | 운영 | Operation |
| `OUTB` | 출고 | Outbound |
| `OUTBPLC` | 출고처 | Outbound Place |
| `PAGE` | 페이지 | Page |
| `PARA` | 파라미터 | Parameter |
| `PERD` | 기한 | Period |
| `PGM` | 프로그램 | Program |
| `PIC` | 담당자 | Person In Charge |
| `PIKNG` | 피킹 | Picking |
| `PKGNG` | 포장 | Packaging |
| `PLC` | 처 | Place |
| `PLCY` | 정책 | Policy |
| `POST` | 우편 | Post |
| `PPUP` | 팝업 | Popup |
| `PR` | 당 | Per |
| `PRFX` | 접두 | Prefix |
| `PRGR` | 진행 | Progress |
| `PROC` | 처리 | Process |
| `PROD` | 상품 | Product |
| `PRODN` | 생산 | Production |
| `PRTY` | 우선 | Priority |
| `PSBL` | 가능 | Possible |
| `PSTN` | 자리 | Position |
| `PTAWY` | 적치 | Put Away |
| `PTH` | 경로 | Path |
| `PTRN` | 패턴 | Pattern |
| `PWD` | 비밀번호 | Password |
| `QRY` | 쿼리 | Query |
| `QTY` | 수량 | Quantity |
| `RCPTN` | 수신 | Reception |
| `REG` | 등록 | Registration |
| `REGR` | 등록자 | Register |
| `REP` | 대표 | Representative |
| `REQ` | 요청 | Request |
| `REF` | 참조 | Reference |
| `RGN` | 권역 | Region |
| `RLZ` | 해제 | Release |
| `RMK` | 비고 | Remark |
| `RNK` | 순위 | Rank |
| `ROLE` | 역할 | Role |
| `ROW` | 행 | Row |
| `RSLT` | 결과 | Result |
| `RSN` | 사유 | Reason |
| `RSPS` | 응답 | Response |
| `RSTRCT` | 제약 | Restriction |
| `RTNGS` | 반품 | Returning Goods |
| `RULE` | 규칙 | Rule |
| `RVSN` | 리비전 | Revision |
| `SEQ` | 순서 | Sequence |
| `SHMT` | 출하 | Shipment |
| `SHOTGE` | 결품 | Shortage |
| `SIZE` | 크기 | Size |
| `SLOT` | 슬롯 | Slot |
| `SLP` | 전표 | Slip |
| `SMRY` | 요약 | Summary |
| `SN` | 일련번호 | Serial Number |
| `SNPSHT` | 스냅샷 | Snapshot |
| `SPLT` | 분할 | Split |
| `SPMT` | 보충 | Supplement |
| `SRT` | 정렬 | Sort |
| `SSMTRL` | 부자재 | Subsidiary materials |
| `ST` | 상태 | Status |
| `STG` | 단계 | Stage |
| `STGY` | 전략 | Strategy |
| `STKTK` | 실사 | Stock Taking |
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
| `TRC` | 추적 | Trace |
| `TRGR` | 트리거 | Trigger |
| `TRM` | 기간 | Term |
| `TRNS` | 전송 | Transmission |
| `TRSCP` | 운송사 | A Transport(Shipping, Freight) Company |
| `TYP` | 유형 | Type |
| `UNT` | 단위 | Unit |
| `UOM` | 계량단위 | Unit of Measure |
| `UPD` | 수정 | Update |
| `UPDR` | 수정자 | Updater |
| `UPR` | 상위 | Upper |
| `URL` | URL | Uniform Resource Locator |
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


## 이미 쓰고 있는 이름과 어긋나는 곳

원래 여기 16개 항목이 「확인 필요」로 올라와 있었다. `docs/migration-catchup-to-schema.sql`의 컬럼 개명 루프(§2, 35쌍)가 그중 해소 가능한 것을 사전 쪽으로 개명했다(컬럼 · 엔티티 필드 · DTO · 프론트 JSON 필드까지 함께).

### 해소됨 — 사전대로 개명 완료

| 단어 | 사전 | 옛 이름 → 새 이름 |
|---|---|---|
| 적치 | `PTAWY` | `ptwy_qty` → `ptawy_qty` |
| 할당 | `ALOC` | `alloc_qty` → `aloc_qty` |
| 설명 | `DSCR` | `description` → `dscr` |
| 유형 | `TYP` | `loc_type`·`ref_doc_type`·`tx_type` → `loc_typ`·`rfn_doc_typ`·`tx_typ` |
| 존 | `ZON` | `zone_cd` → `zon_cd` |
| 온도 | `TMP` | `temp_zone` → `tmp_zon` |
| 그룹 | `GRP` | `group_cd`·`group_nm` → `grp_cd`·`grp_nm` |
| 웨이브 | `WAV` | `wave_no`·`wave_id` → `wav_no`·`wav_id` |
| 피킹 | `PIKNG` | `pick_prty`·`picked_qty` → `pikng_prty`·`pikng_qty` |
| 정렬 + 순서 | `SRT` + `SEQ` | `sort_ord` → `srt_seq` |
| 확정 | `CFM` | `converted_at` → `cfm_dt` (상태값도 `CONVERTED` → `CONFIRMED`). 「변환」은 사전에 없는 단어였고 `CNVR`(환산)과 헷갈렸다 — 사용자가 하는 행위가 「발주 확정」이라 사전의 확정을 쓴다.<br>이후 `ib_order.clos_dt` → `cfm_dt`도 같은 이유로 개명했다(`docs/migration-ib-clos-to-cfm.sql`) — 컬럼이 가리키는 사건이 마감이 아니라 「입고확정」이다. 미뤄뒀던 상태값도 상태 모델 개편 때 `CONFIRMED`로 정리했다(`docs/migration-ib-status-confirm.sql` — RECEIVED/COMPLETED 폐지) |
| 사용 | `US` | ~~`use_yn` → `us_yn`~~ — 이후 사용여부 컬럼 자체를 전 테이블에서 제거했다(`docs/migration-drop-us-yn.sql`). 마스터는 물리삭제로 운용하므로 지금 이 단어를 쓰는 컬럼은 없다 |
| 참조 | `REF` | ~~`ref_doc_no` → `rfn_doc_no`~~ — 이후 `REF`로 되돌렸다(아래 특기사항). 기존 `inv_hist.rfn_doc_typ`·`rfn_doc_no`는 아직 옛 표기다 |
| 주문 | `ODR` | `order_qty`·`order_dt` → `odr_qty`·`odr_de`.<br>**「발주」도 별도 약어 없이 이 단어를 쓴다** — `odr_dvsn`(발주구분) · `odr_qty`(발주수량)가 이미 그렇고, 자동발주가 더한 `min_odr_qty`(최소주문수량)도 같다. 발주점·발주 상한은 `min_qty`·`max_qty`로 `fxng_loc`(재보충점·보충 상한)의 선례를 따른다 |
| 담당자 | `PIC` | `mgr_nm` → `pic_nm` |
| 취소 | `CNCL` | `cancels_inv_hist_id` → `cncl_inv_hist_id` |
| 일자 `DE` · 일시 `DT` | | `expct_dt` → `expct_de`(DATE), `closed_at`·`completed_at`·`shipped_at` → `clos_dt`·`cmpl_dt`·`shmt_dt` |

### 따르지 않기로 결정한 것

| 단어 | 사전 | 유지하는 이름 | 이유 |
|---|---|---|---|
| 재고 | `INVN` | `inv`, `inv_hist`, `inv_id` | **규칙 5가 우선.** 테이블 접두(`INV*`)와 PK명은 사전보다 위다 |
| 상태 | `ST` | `status` | 두 글자로는 state/street/start 중 무엇인지 알 수 없다 |
| 코드 | `CD` | `code_detail.code_cd` · `code_nm` | `cd_cd`가 되어 같은 단어가 겹친다 |
| 생성 · 수정 | `CRT` · `UPD` | `created_*` · `updated_*` | 「생성자」가 사전에 없고 `CRTR`은 이미 「기준(Criteria)」이 쓴다. `BaseEntity`·`AuditorAware`와도 묶여 있다 |

### 아직 정리되지 않은 이탈

**출고(`OUTB`)는 맞지만 `store.outb_life_rate`의 나머지 단어가 사전에 없다.** `life`(수명) · `rate`(비율) 어느 쪽도 등재돼 있지 않다. 짝이던 `prod.ib_life_rate`는 개명 대신 **삭제**했고(읽는 코드가 없는 write-only 컬럼이었다 — `docs/migration-uom.sql`), 이쪽은 실제로 쓰이고 있어 남겨 뒀다. 개명하려면 「잔여」·「비율」 등재가 먼저다.

테이블 접두 `IB_*`(`ib_order` · `ib_line`)와 그 PK·FK는 **대상이 아니다** — 규칙 5가 사전보다 우선한다. 컬럼만 해당한다.

**「적치」의 세 철자 중 둘은 정리했고 하나가 남았다.** 인덱스·제약의 `ptwy`는 `ptawy`로 개명했고(`docs/migration-rename-ptwy-to-ptawy.sql`), 컬럼(`ptawy_qty` · `ptawy_prty`)과 전략 테이블(`ptawy_stgy`)은 원래부터 사전대로였다. 남은 것은 **테이블명 `putaway_task`** 하나인데, 규칙 5(테이블명은 사전보다 위)에 걸려 개명하지 않았다 — 다만 이건 규칙이 인정하는 예외라기보다 **미해결**로 봐야 한다. 같은 스키마에 `ptawy_stgy`와 `putaway_task`가 나란히 있는 상태다.

**`lot`의 일자 컬럼 셋이 `_dt`를 쓴다** — `receipt_dt` · `mfg_dt` · `expiry_dt` 모두 DATE인데 접미가 일시(`DT`)다. 사전은 일자를 `DE`로 구분하고 `ib_order.expct_de` 등은 그렇게 개명됐는데 이 셋만 남았다. 읽는 코드가 많아(엔티티 · DTO 6개 · 프론트 8곳) 개명 비용이 있어 미뤄 둔 상태다.

### 아직 사전에 없는 단어

스키마가 쓰지만 사전에 등재되지 않은 것들이다. 규칙 6에 따라 **이름을 바꾸려면 사전 등재가 먼저다.**

`rcvd`(검수/입고수량) · `rjct`(rejected) · `hist`(history) · `tx`(transaction) · `doc`(문서) · `on_hand`(보유) · `mfg`(제조) · `expiry`(유통기한) · `shelf_life` · `rate`(비율) · `days`(일수) · `version` · `converted`(전환) · `released`(발행) · `receipt`

이 중 `expct` · `rcvd` · `rjct` · `loc` · `hist`는 `CLAUDE.md`와 `docs/schema.sql` 머리말의 약어 사전에 이미 박혀 있다.

## 원본 표에 대한 메모

옮기면서 손대지 않고 그대로 둔 것들이다.

- `이메일(EMAIL)`의 영문명이 원본에 `Id`로 적혀 있다. `Email`의 오기로 보이지만 임의로 고치지 않았다.
- `편수(FLTNO)`의 영문명 `Fltno`는 영문 단어가 아니라 약어를 그대로 옮긴 값이다. 이것도 원본대로 뒀다.
- `참조`의 약어를 **`RFN` → `REF`로 되돌렸다.** 원본 사전은 실제로 `RFN`이었고(오타처럼 보여 확대해 재확인까지 했다) 한때 `ref_doc_no`를 `rfn_doc_no`로 개명하기도 했지만, `ref`가 통용되는 표기라 그쪽을 쓰기로 결정했다. **기존 `inv_hist.rfn_doc_typ`·`rfn_doc_no`는 아직 개명하지 않아 두 표기가 공존한다** — 새 컬럼은 `ref`를 쓴다. 한편 `해제 RLZ`(`RLS` 아님) · `화물 CAGO`(`CRGO` 아님) · `일자 DE`(`DT`는 `일시`가 이미 쓴다) · `회차 TME` / `차수 TMO`도 확인을 거친 값이다.
- 원본의 `중복여부` 컬럼은 216행 전부 `N`이라 옮기지 않았다. 실제로 한글·약어 어느 쪽에도 중복이 없음을 확인했다.
