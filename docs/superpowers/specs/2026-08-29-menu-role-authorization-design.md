# 메뉴·역할 권한 관리 설계

2026-08-29 · wms-backend + wms-front

## 1. 왜 하는가

지금 「어느 역할이 어느 화면을 쓰는가」가 **세 곳에 손으로 적혀** 있다.

| 곳 | 내용 |
|---|---|
| `SecurityConfig` | URL 접두 → 역할 (실제 차단) |
| `Sidebar.jsx`의 `MENU` | 그룹·항목 → 역할 (메뉴 노출) |
| `MobileHome.jsx`의 `GROUPS` | PDA 항목 → 역할 |

같은 날(2026-08-29) 재고담당에게 고정 로케이션 관리를 열어주는 데 **세 곳을 다 고치고 배포**했다. 창고마다 담당 구분이 다른 것이 정상인데, 그때마다 배포가 필요하면 이 시스템은 창고 하나짜리다.

**목표는 화면 단위 권한을 배포 없이 조정하는 것**이고, 부수 효과로 세 곳이 한 곳(DB)으로 줄어든다.

## 2. 결정 요약

| 결정 | 값 | 근거 |
|---|---|---|
| 메뉴 카탈로그의 주인 | **DB** | 「메뉴 관리」 화면이 제 몫을 하려면 편집 대상이 있어야 한다 |
| 코드에 남는 것 | 아이콘 이름표, `App.jsx` 라우트 | 아이콘은 컴포넌트라 DB에 못 담고, 라우트는 페이지 코드가 있어야 존재한다 |
| 접근 권한의 주인 | **코드 상한 + DB 실제**(하이브리드) | 배포 없이 조정하되, 관리자 실수가 업무 구역을 넘지 못하게 |
| DB 권한의 적용 범위 | **비GET만** | 화면 여럿이 같은 조회 API를 쓴다. GET까지 보면 남의 화면이 깨진다 |
| `ADMR` | 매핑 대상 아님, 항상 전 메뉴 | 잠김 방지. DB가 비어도 관리자는 들어와 켤 수 있다 |
| 저장 못 하는 역할에 메뉴 주기 | 막지 않고 표시(👁) | 조회 전용으로 여는 것은 정당한 설정이다 |
| 멀티센터 축 | 지금은 넣지 않음 | 센터가 어느 축에 붙을지 아직 모른다. 테이블이라 나중에 붙일 수 있다 |
| PDA 8화면 | 같은 테이블(`dvsn='PDA'`) | 권한이 한 곳에서 끝난다 |
| 버튼(동작) 권한 | **미룸** | 아래 9장 |

## 3. 사전에 추가할 단어 넷

`docs/naming-dictionary.md`에 먼저 넣고 쓴다. 약어 충돌 없음을 확인했다.

| 한글 | 약어 | 영문명 |
|---|---|---|
| 화면 | `SCRN` | Screen |
| 아이콘 | `ICON` | Icon |
| 키워드 | `KYWD` | Keyword |
| API | `API` | Application Programming Interface |

`API`를 약어로 넣는 것은 `URL`(2026-08-23 추가) 선례를 따른 것이다. 두 표(가나다순·역인덱스)에 모두 넣고 머리말 개수를 함께 올린다.

## 4. 데이터 모델

`usr_role`을 본떴다 — 복합 PK, 감사 4종, `role` CHECK, **FK 없음**, `us_yn` 없음(전 테이블에서 제거된 컬럼이며 마스터는 물리삭제로 운용한다).

```sql
CREATE TABLE mnu (
    mnu_cd      VARCHAR(30)     NOT NULL,   -- STK_SPMT · PDA_PICKING
    mnu_nm      VARCHAR(50)     NOT NULL,   -- 사이드바와 관리 화면에 뜨는 이름
    dvsn        VARCHAR(10)     NOT NULL,   -- WEB · PDA
    grp_nm      VARCHAR(30)     NOT NULL,   -- 모니터링 · 입고 · 재고 · 창고 …
    srt_seq     INTEGER         NOT NULL,   -- 그룹 안 순서
    icon_nm     VARCHAR(30)     NOT NULL,   -- lucide 아이콘 이름. 프론트 이름표가 컴포넌트로 바꾼다
    scrn_pth    VARCHAR(60)     NOT NULL,   -- 프론트 라우트. /stock/spmt
    api_prfx    VARCHAR(50),                -- 이 화면의 쓰기 API 이름공간. /inventory/adjs
                                            -- NULL = 조회 전용 화면(대시보드 · 현재고 조회 · 라벨 인쇄)
    kywd        VARCHAR(200),               -- 검색 보조어. 초성·영문 별칭
    created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by  VARCHAR(30)     DEFAULT 'admin' NOT NULL,
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(30),
    CONSTRAINT pk_mnu PRIMARY KEY (mnu_cd),
    CONSTRAINT uq_mnu_scrn_pth UNIQUE (scrn_pth),
    CONSTRAINT uq_mnu_api_prfx UNIQUE (api_prfx),
    CONSTRAINT ck_mnu_dvsn CHECK (dvsn IN ('WEB', 'PDA'))
);

CREATE TABLE mnu_role (
    mnu_cd      VARCHAR(30)     NOT NULL,
    role        VARCHAR(20)     NOT NULL,
    created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by  VARCHAR(30)     DEFAULT 'admin' NOT NULL,
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(30),
    CONSTRAINT pk_mnu_role PRIMARY KEY (mnu_cd, role),
    -- ADMR이 빠져 있다. 시스템관리자는 매핑 대상이 아니라 항상 전 메뉴를 보므로,
    -- 실수로도 행이 생기지 않게 DB가 막는다
    CONSTRAINT ck_mnu_role_role CHECK (role IN
        ('CENT_ADMR', 'ODR_PIC', 'IB_PIC', 'INV_PIC', 'OUTB_PIC', 'INQ'))
);
```

- **`uq_mnu_scrn_pth`** — 같은 화면을 가리키는 메뉴가 둘이면 사이드바에 중복으로 뜬다. DB가 막는다.
- **`uq_mnu_api_prfx`** — 아래 5장의 불변식(「한 접두는 한 메뉴」)을 DB가 절반 지킨다. PostgreSQL은 `NULL`을 서로 다른 값으로 보므로 조회 전용 화면 여럿이 `NULL`인 것은 걸리지 않는다.
- **`mnu_role`에는 켜진 것만** 행으로 있다. 52메뉴 × 평균 3역할 ≈ 150행.
- **그룹에는 권한을 두지 않는다.** 항목만 걸러지고, 항목이 하나도 안 남은 그룹은 제목째 빠진다(지금 사이드바가 이미 그렇게 동작한다).

## 5. 인가 — 두 단계

```
요청
 ├ ① 상한   SecurityConfig / SecurityRules  (코드)
 │      업무 구역 경계. 통과 못하면 403, 여기서 끝
 └ ② 메뉴   MnuAccessFilter                 (DB)
        GET이면 건너뜀 — 조회는 ①이 로그인만으로 열어둔다
        ADMR이면 건너뜀
        경로가 어느 메뉴의 api_prfx에 걸리면, 그 메뉴가 내 역할에 켜져 있나 확인
        어느 메뉴에도 안 걸리는 경로(/auth 등)는 통과
```

### ②가 GET을 건너뛰는 이유

화면 여럿이 같은 조회 API를 쓴다. 재고조정 화면은 저장은 자기 이름공간(`/inventory/adjs`)으로 하지만 현재고는 `/inventory/stock`에서 읽는다. GET까지 ②가 보면 **현재고 조회 메뉴를 끈 순간 재고조정 화면이 깨진다** — 남의 화면이 무너진다.

GET을 빼면 「메뉴를 끈다」의 뜻이 **「그 화면의 저장·실행을 막는다」**로 명확해지고, 조회는 지금과 똑같이 로그인만으로 열린다(프로젝트의 기존 입장이기도 하다). 그래서 조회 전용 화면은 `api_prfx`가 `NULL`이고 ②가 아예 관여하지 않는다 — 그런 화면의 메뉴를 끄는 것은 순수한 숨김이다.

### 왜 두 단계인가

성격이 다른 둘이 지금 한 표에 섞여 있다.

| | 무엇 | 얼마나 바뀌나 | 틀리면 |
|---|---|---|---|
| 업무 구역 경계 | 입고담당은 출고를 못 만진다 | 조직이 바뀔 때나 | 재고가 틀어진다 |
| 화면 단위 권한 | 재고담당이 고정로케이션을 만지나 | 창고마다 · 수시로 | 불편할 뿐 |

전자를 DB로 내리면 체크 한 번으로 무너지고, 후자를 코드에 두면 배포가 필요하다. **전자는 코드에, 후자는 DB에** 둔다.

전략 설계의 「전략이 건드릴 수 있는 것 / 없는 것」과 같은 형태다.

### `SecurityRules` — 규칙표를 데이터로 꺼낸다

「조회만 👁」 판정과 ②가 같은 규칙을 읽어야 두 벌이 안 생긴다. 지금 규칙표는 `SecurityConfig`의 람다 안이라 읽을 수 없으므로, **쓰기 규칙 부분만** 순서 있는 목록으로 꺼내고 `SecurityConfig`가 그것을 돌며 체인을 만든다.

```java
public final class SecurityRules {
    /** 순서가 곧 규칙이다 — 먼저 걸리는 것이 이긴다. SecurityConfig가 이 순서대로 등록한다 */
    static final List<Rule> WRITE_RULES = List.of(
        new Rule(List.of("/master/fxng-locs/**"), ADMR, CENT_ADMR, INV_PIC),
        new Rule(List.of("/master/zons/**", "/master/locs/**"), ADMR, CENT_ADMR),
        new Rule(List.of("/master/**"), ADMR),
        new Rule(List.of("/strategy/**"), ADMR, CENT_ADMR),
        new Rule(List.of("/oms/**"), ADMR, ODR_PIC),
        new Rule(List.of("/inbound/**"), ADMR, CENT_ADMR, IB_PIC),
        new Rule(List.of("/inventory/**"), ADMR, CENT_ADMR, INV_PIC),
        new Rule(List.of("/outbound/**"), ADMR, CENT_ADMR, OUTB_PIC));

    /** 관리 화면이 「저장까지 / 조회만」을 가르는 자리 */
    public static boolean canWrite(Role role, String apiPrfx) { ... }
}
```

`permitAll` · `GET /** authenticated` · `/master/usrs/**` · `denyAll`은 성격이 달라 `SecurityConfig`에 손으로 남긴다 — 꺼내면 오히려 순서가 안 보인다.

**위험은 순서다.** 한 칸만 틀어져도 권한이 조용히 넓어진다. `SecurityRulesTest` 15개가 그 순서를 이미 검사하므로, **리팩터링 전후로 그 15개가 그대로 통과하는 것**을 안전망으로 삼는다. 이 단계를 먼저 끝내고 메뉴 작업을 시작한다.

### `MnuAccessFilter`

`SecurityConfig`의 매처마다 조건을 끼워 넣지 않는다. 그러면 지금 한눈에 읽히는 순서표가 조건식으로 지저분해진다. 두 단계를 물리적으로 분리하면 「코드가 상한, DB가 실제」가 코드 배치에도 보인다.

- **메뉴가 관장하지 않는 경로는 통과시킨다.** 차단하는 쪽으로 만들면 시드에 접두 하나만 빠져도 화면이 통째로 죽는데, 그건 ①이 이미 보고 있으므로 안전한 통과다.
- **접두가 겹치면 가장 긴 것이 이긴다.** 겹침은 정상이다 — 관리 화면 둘이 `/master/mnus`와 `/master/mnus/roles`로 포개져 있다.

### 불변식 — 주인 없는 엔드포인트가 없다

> **모든 비GET 엔드포인트는 적어도 한 메뉴의 `api_prfx` 아래 있다** (예외: `/auth/**`).
> **겹치면 가장 긴 접두를 가진 메뉴가 그 엔드포인트의 주인이다.**

이걸 테스트로 못 박는다. 그러면 위의 「통과시킨다」가 헐거운 기본값이 아니라 **일어날 수 없는 경우**가 된다 — 새 API가 어느 메뉴에도 안 속하면 빌드가 깨진다.

「정확히 하나」가 아니라 「적어도 하나 + 가장 긴 것이 주인」인 이유는 접두가 **포개지는 것이 정상**이기 때문이다. `POST /master/mnus/roles`는 `/master/mnus`(메뉴 관리)와 `/master/mnus/roles`(권한별 메뉴 관리) 둘 다에 걸리지만, 긴 쪽이 주인이라 판정은 하나로 정해진다.

실측해 보니 컨트롤러가 이미 화면별로 갈려 있어(`/inventory/adjs` · `/holds` · `/moves` · `/spmt` · `/stocktakes`, `/outbound/allocations` · `/picking-tasks` · `/picking` · `/replenishment` · `/shipping`) 이 불변식은 **거의 이미 참**이다.

**어긋나는 곳이 하나 있다.** 수동할당이 `POST /outbound/waves/{wavId}/allocations/manual`인데, 할당 화면의 기능이면서 주소는 웨이브 편성 아래다. 그대로 두면 할당 메뉴를 꺼도 이 요청이 웨이브 접두로 통과하고, 반대로 웨이브 편성을 끄면 수동할당이 막힌다. **`POST /outbound/allocations/manual`로 옮긴다**(프론트 `AllocCandidateModal` 호출부도 함께).

### 캐시

`mnu` · `mnu_role`을 애플리케이션 메모리에 통째로 들고, **기동 시 적재 + 관리 화면 저장 시 갱신**한다. TTL은 두지 않는다.

> **전제: 인스턴스 하나.** Render 무료 플랜이라 지금은 참이다. **인스턴스를 늘리면 이 전제가 깨진다** — 저장을 처리하지 않은 인스턴스가 옛 권한으로 돈다. 그때는 짧은 TTL이나 알림이 필요하다.

### 잠김 방지 셋

1. **`ADMR`은 ②를 건너뛴다.** 테이블이 비어도 관리자는 들어와서 켤 수 있다.
2. 기동 시 `mnu`가 비어 있으면 **경고 로그**를 남긴다.
3. 관리 화면 둘도 `mnu` 행을 갖지만(불변식이 요구한다) `mnu_role` 행은 하나도 없다. ①이 `/master/**` → `ADMR`로 이미 막고 `ADMR`은 ②를 건너뛰므로, **관리 화면은 어떤 설정으로도 닫히지 않는다.**

## 6. API

`mdm.mnu` 패키지에 둔다. 두 앱이 공유하는 마스터라 `mdm`이 맞고, **`common.security`에는 넣지 않는다** — `common`은 DB를 보지 않는다는 규칙이 있다.

```
mdm/mnu/controller/  MnuController
mdm/mnu/dto/         MnuSaveRequest · MnuRoleGridResponse · MnuRoleSaveRequest
mdm/mnu/entity/      Mnu · MnuRole
mdm/mnu/repository/  MnuRepository · MnuRoleRepository
mdm/mnu/service/     MnuService · MnuAccessSource(캐시)
```

| 메서드 | 경로 | 하는 일 |
|---|---|---|
| `GET` | `/master/mnus?dvsn=WEB` | 메뉴 목록 (메뉴 관리 화면) |
| `POST` | `/master/mnus/bulk` | 메뉴 C/U/D 일괄 (다른 마스터와 같은 형태) |
| `GET` | `/master/mnus/roles?dvsn=WEB` | 역할 × 메뉴 격자 + 칸마다 `저장까지`/`조회만` |
| `PUT` | `/master/mnus/roles?dvsn=WEB` | 그 탭의 매핑을 **통째로 교체** |

매핑 저장이 C/U/D가 아닌 이유 — 행 그리드가 아니라 체크박스 격자다. 바뀐 칸만 보내면 「지운 것」을 따로 표현해야 하는데, 탭 하나가 44행 × 6역할이라 통째로 보내는 편이 단순하고 여러 번 눌러도 결과가 같다.

`/master/mnus/**`는 `/master/**` → `ADMR` 규칙에 이미 걸리므로 **`WRITE_RULES`에 줄을 더하지 않는다.** 규칙표를 늘리지 않는 것이 이 설계의 전제라, 접두를 지키는 것으로 충분한 곳에 줄을 추가하지 않는다.

### 검증 경계

`MnuSaveRequest`가 자기 필드만으로 하는 검사(필수·형식·`dvsn` 값)와 엔티티 생성·반영을 맡고, DB를 봐야 하는 검사(코드 중복 · `scrn_pth` 중복 · 참조)는 `MnuService`가 한다. 다른 마스터와 같은 틀이다.

## 7. 로그인 응답

```java
public record MeResponse(String loginId, String usrNm, List<String> roles,
                         String csrfToken, List<MnuResponse> menus) { }
```

`MnuResponse`는 사이드바를 그리는 데 필요한 것 전부 — `mnuCd` · `mnuNm` · `dvsn` · `grpNm` · `srtSeq` · `iconNm` · `scrnPth` · `kywd`.

**`AuthUser`에는 절대 넣지 않는다.** `AuthUser`는 세션에 직렬화돼 들어가므로, 거기 담으면 로그인 시점의 메뉴가 굳어 관리자가 매핑을 바꿔도 그 사람은 옛 메뉴로 돈다 — **JWT를 버린 이유와 똑같은 문제**가 재현된다. `/auth/me`가 불릴 때마다 새로 읽는다.

`ADMR`이면 DB의 `mnu_role`을 보지 않고 `mnu` 전량을 돌려준다.

### 매핑이 바뀌어도 세션은 끊지 않는다

역할이 바뀌면 세션을 끊는 규칙(`UsrService`)은 그대로 두되, 메뉴 매핑 변경은 끊지 않는다.

- 매핑은 **역할 단위**라 끊으면 그 역할을 가진 **전원**이 튕긴다. 피킹 중인 현장 작업자까지 로그인 화면으로 간다.
- 옛 메뉴가 잠깐 보여도 눌러보면 ②가 막는다. 위험이 아니라 지연이다.
- 다음 `/auth/me`(새로고침 · 재로그인)에 반영된다. 관리 화면 저장 후 「사용자에게는 다음 접속부터 보입니다」를 안내한다.

## 8. 프론트

### 사이드바 · PDA — 하드코딩을 버린다

`Sidebar.jsx`의 `MENU` 44줄과 `MobileHome.jsx`의 `GROUPS` 8줄이 **통째로 사라진다.** 남는 것은 아이콘 이름표와 렌더링 로직뿐이다.

```js
// 지금
.filter(g => !g.roles || hasRole(g.roles))

// 앞으로 — /auth/me가 준 것만 그린다
menus.filter(m => m.dvsn === 'WEB')
```

- `AuthContext`가 `menus`를 들고 그룹·순서로 묶어 준다.
- `hasRole`은 남긴다 — 메뉴가 아닌 자리(화면 안의 표시 분기)에서 여전히 쓴다.
- `MobileHome`의 「현장 작업 권한이 없습니다」 화면은 그대로 살아난다. 이제 `roles` 대신 **PDA 메뉴가 0개일 때** 뜬다.
- **첫 로그인 후 사이드바가 잠깐 빈다.** 스켈레톤으로 덮는다. 로그인 자체가 이미 백엔드를 필요로 하므로 실질적인 차이는 이 한순간뿐이다.

### 아이콘 이름표

```js
// src/layout/menuIcons.js
export const MENU_ICONS = { Repeat, MapPin, LayoutGrid, Box, ... };
```

메뉴가 늘어도 **아이콘 종류가 새로 필요할 때만** 바뀌는 일반 사전이다. 메뉴마다 코드를 고치는 것과 성격이 다르다. 이름표에 없는 아이콘은 기본 아이콘으로 그리고 콘솔에 경고한다.

### 화면 둘

| 화면 | 경로 | 하는 일 |
|---|---|---|
| **메뉴 관리** | `/master/menu` | 메뉴 등록·수정·삭제, 그룹·순서, 아이콘, 화면 경로, API 접두, 키워드 |
| **권한별 메뉴 관리** | `/master/menu-auth` | 역할 × 메뉴 체크박스 격자 |

둘 다 마스터 그룹, `ADMR` 전용.

**메뉴 관리** — 다른 마스터와 같은 C/U/D 그리드. 두 가지만 다르다.

- **`scrn_pth`는 드롭다운이다.** 프론트가 자기 라우터에 등록된 경로 목록을 알고 있으므로 그중에서 고르게 한다. 직접 타이핑을 막으면 오타로 죽은 링크가 나올 길이 없다. **라우트에는 있는데 메뉴가 없는 화면**도 같은 목록에서 보인다.
- **`icon_nm`도 드롭다운이다.** 이름표의 키 목록에서 고르고 미리보기를 옆에 띄운다.

**권한별 메뉴 관리**

```
메뉴                     CENT   ODR   IB   INV   OUTB   INQ
─ 재고 ────────────────────────────────────────────────────
  현재고 조회             ☑      ☐    ☐    ☑     ☐     ☑
  재고조정                ☑      ☐    ☐    ☑     ☐     ☑👁
  고정 로케이션 관리       ☑      ☐    ☐    ☑     ☐     ☐
─ 창고 ────────────────────────────────────────────────────
  존 관리                 ☑      ☐    ☐    ☐     ☐     ☐
  로케이션 관리            ☑      ☐    ☐    ☑👁   ☐     ☐

범례   ☑ 저장까지 됨     ☑👁 열리지만 저장은 안 됨(업무 구역 상한)
```

- 탭 둘(데스크톱 · PDA). `ADMR` 열은 **없다**.
- **그룹 헤더에 일괄 체크.** 44행 × 6열을 하나씩 누르게 하면 아무도 안 쓴다.
- **👁는 경고가 아니라 정보다.** 조회 전용으로 여는 것은 정당한 설정이라 저장을 막지 않고, 관리자가 무엇을 준 것인지만 보여준다.
- 행 추가·삭제 없음. 메뉴 목록은 「메뉴 관리」가 주인이고 여기서는 **체크만** 한다.

## 9. 시딩과 적용

1. `docs/schema.sql`에 두 테이블을 추가한다(스키마의 주인).
2. `docs/migration-add-mnu.sql` — 라이브 DB용 증분. 전체를 `DO $tag$ … $tag$` 한 블록으로 쓰고 존재 확인을 걸어 재실행 가능하게 만든다(`BEGIN;`을 쓰면 실패 시 `25P02`가 남는다).
3. 시드는 **지금 코드에서 그대로 뽑는다** — `mnu` 52행은 `Sidebar.jsx`·`MobileHome.jsx`의 현재 배열에서, `mnu_role`은 현재 하드코딩된 `roles`에서. 그룹 단위 `roles`는 항목마다 펼쳐 넣는다.
4. **적용 직후 동작이 지금과 완전히 같아야 한다.** 이것이 시딩이 맞았는지 판정하는 기준이다.
5. `docs/seed-dev.sql`에도 같은 내용을 넣어 새 DB가 바로 돈다.

**배포 순서** — 스키마 변경이 있으므로 DBeaver로 마이그레이션을 먼저 돌리고, 백엔드를 올린 뒤, 프론트를 잇는다. 프론트가 먼저 올라가면 `menus` 없는 응답을 받아 사이드바가 빈다.

## 10. 테스트

| 대상 | 확인하는 것 |
|---|---|
| `SecurityRulesTest` 15개 | **그대로 통과.** ②의 권한 조회원을 `@MockBean`으로 「전부 켜짐」으로 두면 지금 그대로 상한을 검증한다. 뜻만 「규칙표」에서 「상한」으로 바뀌므로 문구를 손본다 |
| `MnuAccessFilterTest` (신규) | 메뉴가 꺼진 역할은 상한을 통과해도 403 · **GET은 메뉴가 꺼져 있어도 통과** · `ADMR`은 꺼져 있어도 통과 · 메뉴가 관장하지 않는 경로는 통과 |
| `EndpointMenuCoverageTest` (신규) | **불변식.** `RequestMappingHandlerMapping`을 훑어 모든 비GET 엔드포인트가 **적어도 한** 메뉴의 `api_prfx`에 속하는지 검사한다(예외 `/auth/**`). 0개면 실패. 새 컨트롤러가 접두를 어기면 여기서 걸린다 |
| `MnuServiceTest` (신규) | 코드·`scrn_pth` 중복 거부 · `dvsn` 값 검증 · 매핑 전량 교체가 여러 번 눌러도 같은 결과 · `ADMR` 행은 저장되지 않음 |
| `SecurityRulesCanWriteTest` (신규) | `canWrite`가 체인과 같은 답을 낸다 — 「조회만 👁」이 거짓말하지 않는 근거 |
| 시드 정합성 | 시드 적용 후 각 역할이 보는 메뉴 집합이 **개편 전 하드코딩 값과 일치** |

## 11. 미룬 것 · 남긴 위험

**버튼(동작) 권한** — 지금 하지 않는다. `mnu_role`에 「동작」 축을 붙이면 되는 길은 열려 있다.

> 미루는 이유는 **등록 안 된 새 API의 기본값**이다. 열어두면 가드가 헐거워 있으나 마나고, 닫아두면 API를 추가할 때마다 등록을 잊는 순간 화면이 죽는다. 화면 단위는 접두라서 새 API가 자동으로 그 화면에 속하지만 동작 단위는 그 자동이 없다. 게다가 화면 43개짜리 권한을 아직 한 번도 굴려보지 않았다 — 굴려보면 버튼 단위가 정말 필요한 자리가 몇 군데인지 나오고, 짐작으로는 취소·삭제·조정 정도라 **권한이 아니라 승인**으로 푸는 것이 맞다.

**멀티센터** — 센터 축을 지금 넣지 않는다. 센터가 `mnu`에 붙는지(센터마다 쓰는 화면이 다름), `mnu_role`에 붙는지(센터마다 매핑이 다름), `usr_role`에 붙는지(사람이 센터마다 다른 역할)가 다 다른 테이블이고 아직 정하지 않았다. 테이블로 간 것 자체가 이미 그 문을 열어둔 것이다.

**이 작업에 딸려 오는 변경 하나**

`POST /outbound/waves/{wavId}/allocations/manual` → **`POST /outbound/allocations/manual`**. 5장의 불변식을 세우려면 필요하다. 백엔드 컨트롤러와 프론트 `AllocCandidateModal` 호출부를 함께 바꾸고, 배포도 두 레포를 붙여서 낸다.

**남긴 위험 셋**

1. **캐시가 인스턴스 하나를 전제한다.** 늘리면 옛 권한으로 도는 인스턴스가 생긴다.
2. **인가가 DB에 의존하게 된다.** `mnu` 시드가 잘못되면 화면이 닫힌다. 상한과 `ADMR` 우회가 완충이지만, 완충이지 방지는 아니다.
3. **`scrn_pth`가 라우트와 갈라질 수 있다.** 드롭다운으로 막지만, 프론트에서 라우트를 지우고 메뉴 행을 안 지우면 죽은 링크가 남는다. 메뉴 관리 화면이 「라우트에 없는 경로」를 표시해 잡는다. `api_prfx` 쪽은 5장의 불변식 테스트가 백엔드에서 잡으므로 같은 위험이 없다.
