# TODO: 점포(Store) 관리 — 직접 구현

SKU/Loc과 같은 패턴의 세 번째 반복. 참고 파일은 적어두되, 먼저 안 보고 시도 → 막히면 참고 → 그래도 막히면 힌트 요청 순서로.

## 0. 시작 전 확인
- [ ] `docs/schema.sql`의 `store` 테이블 읽기 — 컬럼 3개뿐이지만 `outb_life_rate`의 의미(FEFO 앞단 필터)와 CHECK 제약(0~100) 확인
- [ ] 시드 5건 확인: `SELECT * FROM store;` (ST-0001~0005)

## 1. 설계 결정 (코드 치기 전에 먼저 답 정하기)
- [ ] **store_cd를 누가 만드나?** SKU처럼 서버 채번(시퀀스)인지, Loc처럼 사용자 입력+중복검증인지.
      힌트: 시드가 `ST-0001` 형식 → 채번 쪽이 자연스러운데, DB에 `store_cd_seq`가 없다. 채번으로 간다면 시퀀스 생성 + schema.sql 반영까지가 한 세트 (START WITH 주의 — 시드가 이미 5번까지 씀).
- [ ] **검색 조건은 뭘로?** (점포코드/점포명 정도면 충분한지)
- [ ] **outb_life_rate 검증 규칙** — 서버에서 어디까지 막을지 (필수? 범위?)

## 2. 백엔드 (참고: master 패키지의 Sku* 6개 파일)
- [ ] `StoreSearchCond` — 빈 조건은 무시되는 DTO
- [ ] `StoreResponse` — 엔티티 → 응답 변환 (`from` 정적 팩토리)
- [ ] `StoreSaveRequest` — `_status`(C/U/D) 매핑 포함
- [ ] `StoreRepositoryCustom` + `StoreRepositoryImpl` — QueryDSL 동적 검색
- [ ] `StoreRepository` — JpaRepository + (채번이면 시퀀스 쿼리 / 입력이면 existsBy)
- [ ] `StoreService` — list / saveAll(C·U·D switch + flush) / validate
- [ ] `StoreController` — GET `/master/stores`, POST `/master/stores/bulk`
- [ ] **확인**: 재시작 후 브라우저에서 `http://localhost:8080/master/stores` → 시드 5건 JSON

### 스스로 답해보기 (면접 질문이라고 생각하고)
- saveAll 끝에서 `flush()`를 왜 직접 부르나?
- QueryDSL 조건 메서드가 null을 반환하면 무슨 일이 일어나나?
- `@Transactional(readOnly = true)`가 클래스에, `@Transactional`이 saveAll에만 붙는 이유는?

## 3. 프론트 (참고: SkuMaster.jsx — Loc보다 Sku가 원본)
- [ ] `src/api/storeApi.js` — list / saveAll (axios)
- [ ] `StoreMaster.jsx` — 검색바 + 그리드 + 툴바(No.컬럼 / 행추가 / 삭제 / 저장 확인 모달). 엑셀은 선택(나중에 붙여도 됨)
- [ ] 라우터/메뉴에 StoreMaster 등록 (기존 SKU/Loc 메뉴 등록 위치 참고)
- [ ] **확인**: 조회 → 행추가 → 저장(채번 확인) → 수정 → 삭제 → 재조회 한 바퀴

### 스스로 답해보기
- 행 데이터의 주인이 React 상태가 아니라 그리드인 이유는? (applyTransaction / forEachNode)
- 건수 표시가 `rowData.length`가 아니라 별도 state인 이유는?
- `onCellValueChanged`에서 `_status` 컬럼 변경을 무시하는 이유는?

## 4. 마무리
- [ ] 저장 검증 에러 케이스 직접 만들어보기 (빈 이름, 범위 밖 비율 → 400 메시지 확인)
- [ ] 커밋 (간결한 메시지로)
- [ ] 여유 있으면: SKU/Loc/Store 세 화면의 중복을 보고 "이제는 공통화할 때인가?"를 스스로 판단해보기 — 세 번 반복됐으면 후보다
