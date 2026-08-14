# TODO: 점포(Store) 관리 — 직접 구현

**구현 완료 (2026-08-14).** 아래 체크리스트는 이력으로 남긴다 — §1의 결정 내용이 코드의 근거다.

상품/Loc과 같은 패턴의 세 번째 반복. 참고 파일은 적어두되, 먼저 안 보고 시도 → 막히면 참고 → 그래도 막히면 힌트 요청 순서로.

## 0. 시작 전 확인
- [x] `docs/schema.sql`의 `store` 테이블 읽기 — 컬럼 3개뿐이지만 `outb_life_rate`의 의미(FEFO 앞단 필터)와 CHECK 제약(0~100) 확인
- [x] 시드 5건 확인: `SELECT * FROM store;` (ST-0001~0005)

## 1. 설계 결정 (코드 치기 전에 먼저 답 정하기)
- [x] **store_cd를 누가 만드나?** → **서버 채번** (벤더·상품과 동일). 시드가 이미 `ST-0001` 형식이고,
      사용자 입력으로 두면 `uq_store_cd` 위반이 409 "다른 데이터가 참조하고 있어…" 라는 엉뚱한
      메시지로 나간다. 전용 시퀀스가 아니라 채번 모듈로 — `nbr_rule STORE_CD`(prfx `ST`, 4자리,
      NONE) + `nbr_seq` 카운터 5부터(시드가 5번까지 씀). `schema.sql`·`seed-dev.sql` 반영,
      증분은 `migration-add-store-cd-nbr.sql`. `reset-dev.sql` 되감기 대상에는 넣지 않는다
      (점포를 남기는 스크립트라 VNDR_CD와 같은 카버아웃).
- [x] **검색 조건은 뭘로?** → 점포코드/점포명 둘 (벤더와 동일). 컬럼이 셋뿐이라 이걸로 충분.
- [x] **outb_life_rate 검증 규칙** → 필수 + 0~100. DB CHECK에 맡기면
      `DataIntegrityViolationException` → 409 오해 메시지가 나가므로 서비스 `validate()`에서 먼저 막는다.
- [x] **(추가 결정) 삭제 가드** — 벤더처럼 flush에 맡기면 FK가 0건이라 조용히 지워져 출고주문이
      유령 점포를 가리킨다. 상품의 `ProdRefChecker` 선례를 따라 `StoreRefChecker` 포트를 두고
      `WmsStoreRefChecker`(창고 출고주문)·`OmsOutbStoreRefChecker`(OMS 출고주문)가 신고한다.

## 2. 백엔드 (참고: `com.project.mdm.prod` 패키지의 Prod* 6개 파일 — 점포도 마스터라 `com.project.mdm.store`에 만든다)
- [x] `StoreSearchCond` — 빈 조건은 무시되는 DTO
- [x] `StoreResponse` — 엔티티 → 응답 변환 (`from` 정적 팩토리) + 감사 컬럼 4종 추가
- [x] `StoreSaveRequest` — `_status`(C/U/D) 매핑 포함
- [x] `StoreRepositoryCustom` + `StoreRepositoryImpl` — QueryDSL 동적 검색.
      정렬은 `store_cd` 오름차순 — 납품처 선택 팝업이 빈 조건으로 이 쿼리를 타므로 id순으로 가면
      팝업 정렬이 조용히 바뀐다 (기존 `findAllByOrderByStoreCdAsc()`는 검색으로 흡수돼 제거)
- [x] `StoreRepository` — JpaRepository + Custom 상속 (채번은 시퀀스 쿼리가 아니라 NbrService)
- [x] `StoreService` — list / saveAll(C·U·D switch + flush) / validate
- [x] `StoreController` — GET `/master/stores`(`@ModelAttribute` 검색 조건), POST `/master/stores/bulk`
- [ ] **확인**: 재시작 후 브라우저에서 `http://localhost:8080/master/stores` → 시드 5건 JSON
      (구동 확인만 남음 — `migration-add-store-cd-nbr.sql`을 라이브 DB에 먼저 적용할 것)

### 스스로 답해보기 (면접 질문이라고 생각하고)
- saveAll 끝에서 `flush()`를 왜 직접 부르나?
- QueryDSL 조건 메서드가 null을 반환하면 무슨 일이 일어나나?
- `@Transactional(readOnly = true)`가 클래스에, `@Transactional`이 saveAll에만 붙는 이유는?

## 3. 프론트 (참고: ProdMaster.jsx — Loc보다 Prod가 원본)
- [x] `src/api/storeApi.js` — list / saveAll (axios). `list(cond = {})`로 넓혀 납품처 선택 팝업의
      인자 없는 호출과 하위호환 유지
- [x] `StoreMaster.jsx` — 검색바 + 그리드 + 툴바(No.컬럼 / 행추가 / 삭제 / 저장 확인 모달). 엑셀은 선택(나중에 붙여도 됨) — 벤더처럼 안 붙였다
- [x] 라우터/메뉴에 StoreMaster 등록 — 라우트 `/master/store`와 사이드바 메뉴는 이미 있었고 Placeholder만 교체
- [ ] **확인**: 조회 → 행추가 → 저장(채번 확인) → 수정 → 삭제 → 재조회 한 바퀴

### 스스로 답해보기
- 행 데이터의 주인이 React 상태가 아니라 그리드인 이유는? (applyTransaction / forEachNode)
- 건수 표시가 `rowData.length`가 아니라 별도 state인 이유는?
- `onCellValueChanged`에서 `_status` 컬럼 변경을 무시하는 이유는?

## 4. 마무리
- [ ] 저장 검증 에러 케이스 직접 만들어보기 (빈 이름, 범위 밖 비율 → 400 메시지 확인)
- [ ] 커밋 (간결한 메시지로)
- [x] 여유 있으면: 상품/Loc/Store 세 화면의 중복을 보고 "이제는 공통화할 때인가?"를 스스로 판단해보기 — 세 번 반복됐으면 후보다.
      → 공통 컴포넌트 추출(`Badge.jsx`·`format.js`·`index.css @layer`)은 이미 수행됨
      (`설계일관성-전수조사-20260803.md` 참고). 점포 화면도 그 공통 자산 위에 얹었다.
