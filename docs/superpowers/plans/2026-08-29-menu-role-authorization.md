# 메뉴·역할 권한 관리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 「어느 역할이 어느 화면을 쓰는가」를 코드 세 곳에서 DB 두 테이블로 옮기고, 관리자가 배포 없이 화면 단위 권한을 조정할 수 있게 한다.

**Architecture:** 인가가 두 단계가 된다 — ① `SecurityConfig`(코드)가 업무 구역 경계를 상한으로 지키고, ② `MnuAccessFilter`(DB)가 화면 단위 실제 열림을 정한다. 둘 다 통과해야 열린다. 메뉴 카탈로그(라벨·그룹·순서·아이콘·경로)도 DB로 내려가 사이드바가 서버 데이터만으로 그려지고, 프론트에는 아이콘 이름표만 남는다.

**Tech Stack:** Java 17 · Spring Boot 3.5 · Spring Data JPA · QueryDSL · Spring Security(세션) · PostgreSQL / React 19 · Vite · AG Grid · Tailwind

**Spec:** `docs/superpowers/specs/2026-08-29-menu-role-authorization-design.md`

## Global Constraints

- **레포 둘.** 백엔드 `C:\wms-backend` → logistics-back, 프론트 `C:\wms-front` → logistics-front. 둘 다 `main` 직커밋이고 **푸시가 곧 배포**다. 푸시 전 반드시 `git fetch`로 원격이 앞서 있는지 확인한다.
- **컬럼·필드 이름은 `docs/naming-dictionary.md` 조합**이다. 이 작업에서 넷을 추가한다 — 화면 `SCRN` · 아이콘 `ICON` · 키워드 `KYWD` · API `API`. 사전에 넣기 전에는 쓰지 않는다.
- **스키마의 주인은 `docs/schema.sql`.** Hibernate는 `ddl-auto=none`이라 테이블을 만들지 않는다. 엔티티를 스키마에 맞춘다(반대 아님).
- **마이그레이션은 `BEGIN;` 없이 전체를 `DO $tag$ … $tag$` 블록 하나로** 쓰고 존재 확인을 걸어 재실행 가능하게 만든다. DBeaver로 손으로 돌린다.
- **FK를 만들지 않는다.** 참조 무결성은 애플리케이션 책임. `CHECK`·`UNIQUE`는 쓴다.
- **`us_yn` 같은 사용여부 컬럼을 만들지 않는다.** 전 테이블에서 제거됐고 마스터는 물리삭제로 운용한다.
- **`common` 패키지는 어느 앱도 import하지 않는다.** `common.security`는 DB를 보지 않는다 — 메뉴 조회는 `mdm.mnu`에 두고 `common`은 인터페이스만 안다.
- **테스트는 DB를 올리지 않는다.** `@WebMvcTest` 슬라이스 + Mockito. `@SpringBootTest`는 Supabase 접속을 요구한다.
- **주석은 최소한으로.** 왜 그렇게 했는지가 코드에서 안 보이는 자리에만 한 줄.
- **검증 결과 확인은 종료코드로.** `./mvnw test | tail`처럼 파이프로 읽으면 `tail`의 코드를 보게 되어 실패를 통과로 오독한다.

---

## File Structure

**백엔드 (`C:\wms-backend`)**

| 파일 | 책임 |
|---|---|
| `common/security/SecurityRules.java` (신규) | 쓰기 규칙표를 순서 있는 데이터로 보유. `canWrite(role, path)` 제공 |
| `common/security/MnuAccessSource.java` (신규) | 인터페이스. `common`이 `mdm`을 모른 채 ②를 물어보는 창구 |
| `common/security/MnuAccessFilter.java` (신규) | ② 검사. 비GET만, `ADMR` 건너뜀 |
| `common/config/SecurityConfig.java` (수정) | `WRITE_RULES`를 돌며 체인 구성 + 필터 등록 |
| `mdm/mnu/entity/Mnu.java` (신규) | 메뉴 카탈로그 엔티티 |
| `mdm/mnu/entity/MnuRole.java` (신규) | 매핑 엔티티 (복합키) |
| `mdm/mnu/repository/*` (신규) | Spring Data 2개 |
| `mdm/mnu/service/MnuService.java` (신규) | 메뉴 C/U/D + 매핑 교체 + 캐시 갱신 |
| `mdm/mnu/service/MnuAccessCache.java` (신규) | `MnuAccessSource` 구현. 메모리 캐시 |
| `mdm/mnu/controller/MnuController.java` (신규) | API 4개 |
| `mdm/mnu/dto/*` (신규) | 요청·응답 |
| `mdm/usr/service/AuthService.java` (수정) | `/auth/me`에 `menus` 실음 |
| `docs/seed-mnu.sql` (신규) | **시드의 주인.** 형식 고정 — 테스트가 파싱한다 |

**프론트 (`C:\wms-front`)**

| 파일 | 책임 |
|---|---|
| `src/layout/menuIcons.js` (신규) | 아이콘 이름 → lucide 컴포넌트 사전 |
| `src/layout/Sidebar.jsx` (수정) | `MENU` 배열 44줄 삭제, 서버 메뉴로 렌더 |
| `src/pages/mobile/MobileHome.jsx` (수정) | `GROUPS` 배열 삭제, 서버 메뉴(PDA)로 렌더 |
| `src/auth/AuthContext.jsx` (수정) | `menus` 보유·캐시 |
| `src/api/mnuApi.js` (신규) | 메뉴 API 모듈 |
| `src/pages/master/MnuMaster.jsx` (신규) | 메뉴 관리 |
| `src/pages/master/MnuAuthMaster.jsx` (신규) | 권한별 메뉴 관리 |
| `src/App.jsx` (수정) | 라우트 2개 + 라우트 목록 export |

---

## Task 1: `SecurityRules` 추출

규칙표를 읽을 수 있는 데이터로 꺼낸다. **동작은 하나도 바뀌지 않는다** — 기존 `SecurityRulesTest` 15개가 그대로 통과하는 것이 이 태스크의 합격 기준이다.

**Files:**
- Create: `src/main/java/com/project/common/security/SecurityRules.java`
- Modify: `src/main/java/com/project/common/config/SecurityConfig.java`
- Test: `src/test/java/com/project/common/security/SecurityRulesCanWriteTest.java` (신규)

**Interfaces:**
- Produces: `SecurityRules.WRITE_RULES` (`List<SecurityRules.Rule>`, package-private), `public static boolean canWrite(Role role, String path)`, `public record Rule(List<String> patterns, Set<Role> roles)`

- [ ] **Step 1: `SecurityRules`를 쓴다**

```java
package com.project.common.security;

import com.project.mdm.usr.entity.Role;

import java.util.List;
import java.util.Set;

/**
 * 인가 규칙표의 본체. {@code SecurityConfig}가 이 순서대로 체인을 등록하고,
 * 메뉴 권한 화면이 {@link #canWrite}로 같은 표를 읽는다 — 두 벌이 되지 않게 한 곳에 둔다.
 */
public final class SecurityRules {

    /** 순서가 곧 규칙이다 — 먼저 걸리는 것이 이긴다 */
    static final List<Rule> WRITE_RULES = List.of(
            new Rule(List.of("/master/fxng-locs/**"), Set.of(Role.ADMR, Role.CENT_ADMR, Role.INV_PIC)),
            new Rule(List.of("/master/zons/**", "/master/locs/**"), Set.of(Role.ADMR, Role.CENT_ADMR)),
            new Rule(List.of("/master/**"), Set.of(Role.ADMR)),
            new Rule(List.of("/strategy/**"), Set.of(Role.ADMR, Role.CENT_ADMR)),
            new Rule(List.of("/oms/**"), Set.of(Role.ADMR, Role.ODR_PIC)),
            new Rule(List.of("/inbound/**"), Set.of(Role.ADMR, Role.CENT_ADMR, Role.IB_PIC)),
            new Rule(List.of("/inventory/**"), Set.of(Role.ADMR, Role.CENT_ADMR, Role.INV_PIC)),
            new Rule(List.of("/outbound/**"), Set.of(Role.ADMR, Role.CENT_ADMR, Role.OUTB_PIC)));

    private SecurityRules() {
    }

    public record Rule(List<String> patterns, Set<Role> roles) {
    }

    /**
     * 이 역할이 이 경로에 쓰기를 할 수 있나. 메뉴 권한 화면의 「저장까지 / 조회만」 판정이 여기다.
     * 규칙표에 없는 경로는 {@code denyAll}이라 false다.
     */
    public static boolean canWrite(Role role, String path) {
        if (path == null) {
            return false;
        }
        // /master/usrs는 체인에서 GET 규칙보다 위에 손으로 두는 특례라 여기에도 따로 적는다
        if (matches("/master/usrs/**", path)) {
            return role == Role.ADMR;
        }
        for (Rule rule : WRITE_RULES) {
            for (String pattern : rule.patterns()) {
                if (matches(pattern, path)) {
                    return rule.roles().contains(role);
                }
            }
        }
        return false;
    }

    /** {@code /a/**}는 {@code /a}와 {@code /a/...}에 걸린다. 세그먼트 경계를 지켜 /a가 /ab에 걸리지 않게 한다 */
    private static boolean matches(String pattern, String path) {
        if (!pattern.endsWith("/**")) {
            return pattern.equals(path);
        }
        String base = pattern.substring(0, pattern.length() - 3);
        return path.equals(base) || path.startsWith(base + "/");
    }
}
```

- [ ] **Step 2: `SecurityConfig`가 `WRITE_RULES`를 돌게 바꾼다**

`authorizeHttpRequests` 안에서 `/master/fxng-locs/**`부터 `/outbound/**`까지 **여덟 줄을 지우고** 아래로 바꾼다. 그 위(`dispatcherTypeMatchers` · `POST /auth/login` · `/health` · `/master/usrs/**` · `GET /**` · `/auth/**`)와 아래(`anyRequest().denyAll()`)는 **손대지 않는다.**

```java
                    .requestMatchers("/auth/**").authenticated();
            // 쓰기 규칙은 SecurityRules가 갖는다 — 메뉴 권한 화면이 같은 표를 읽어야 해서 데이터로 꺼냈다.
            // 등록 순서가 곧 규칙이라 목록 순서를 그대로 따른다
            for (SecurityRules.Rule rule : SecurityRules.WRITE_RULES) {
                String[] roleNames = rule.roles().stream().map(Enum::name).toArray(String[]::new);
                auth.requestMatchers(rule.patterns().toArray(String[]::new)).hasAnyRole(roleNames);
            }
            auth.anyRequest().denyAll();
```

> 람다 체인이 중간에 끊기므로 `auth -> { ... }` 블록 형태로 바뀐다. 기존 체인 호출들도 `auth.` 접두를 붙인 문장으로 편다.

- [ ] **Step 3: 기존 테스트가 그대로 통과하는지 본다 — 이 태스크의 핵심**

```bash
./mvnw test -Dtest=SecurityRulesTest
echo "EXIT=$?"
```

Expected: `EXIT=0`, 15개 전부 통과. **하나라도 깨지면 순서가 틀어진 것이니 되돌리고 다시 맞춘다.**

- [ ] **Step 4: `canWrite`가 체인과 같은 답을 내는지 검사하는 테스트를 쓴다**

`src/test/java/com/project/common/security/SecurityRulesCanWriteTest.java`:

```java
package com.project.common.security;

import com.project.mdm.usr.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** canWrite가 SecurityConfig 체인과 같은 답을 내는지 — 「조회만 👁」 표시가 거짓말하지 않는 근거 */
class SecurityRulesCanWriteTest {

    @Test
    @DisplayName("먼저 걸리는 규칙이 이긴다 — 로케이션은 /master/** 보다 위의 줄에 걸린다")
    void longerPrefixWinsByOrder() {
        assertTrue(SecurityRules.canWrite(Role.CENT_ADMR, "/master/locs/bulk"));
        assertFalse(SecurityRules.canWrite(Role.CENT_ADMR, "/master/prods/bulk"));
    }

    @Test
    @DisplayName("고정로케이션만 재고담당에게 열려 있다")
    void invPicOnlyOnFxngLoc() {
        assertTrue(SecurityRules.canWrite(Role.INV_PIC, "/master/fxng-locs/bulk"));
        assertFalse(SecurityRules.canWrite(Role.INV_PIC, "/master/locs/bulk"));
    }

    @Test
    @DisplayName("사용자 마스터는 관리자만 — 체인에서 GET 규칙보다 위에 있는 특례")
    void usrMasterIsAdmrOnly() {
        assertTrue(SecurityRules.canWrite(Role.ADMR, "/master/usrs/bulk"));
        assertFalse(SecurityRules.canWrite(Role.CENT_ADMR, "/master/usrs/bulk"));
    }

    @Test
    @DisplayName("규칙표에 없는 접두는 관리자라도 false — denyAll과 같은 답")
    void unlistedPrefixIsFalse() {
        assertFalse(SecurityRules.canWrite(Role.ADMR, "/nowhere"));
    }

    @Test
    @DisplayName("접두는 세그먼트 경계를 지킨다 — /master/loc이 /master/locs에 걸리면 안 된다")
    void prefixRespectsSegmentBoundary() {
        assertFalse(SecurityRules.canWrite(Role.CENT_ADMR, "/master/locsomething/bulk"));
    }

    @Test
    @DisplayName("조회 전용 화면은 경로가 없다 — null은 쓰기 불가")
    void nullPathIsFalse() {
        assertFalse(SecurityRules.canWrite(Role.ADMR, null));
    }
}
```

- [ ] **Step 5: 테스트를 돌린다**

```bash
./mvnw test -Dtest=SecurityRulesCanWriteTest
echo "EXIT=$?"
```

Expected: `EXIT=0`, 6개 통과.

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/project/common/security/SecurityRules.java \
        src/main/java/com/project/common/config/SecurityConfig.java \
        src/test/java/com/project/common/security/SecurityRulesCanWriteTest.java
git commit -m "인가 쓰기 규칙표를 SecurityRules로 꺼냄 — 메뉴 권한 화면이 같은 표를 읽어야 한다"
```

---

## Task 2: 수동할당 엔드포인트를 할당 이름공간으로 옮긴다

「한 엔드포인트는 한 메뉴에 속한다」 불변식을 세우려면 필요하다. **두 레포를 함께 고치고 함께 배포한다.**

**Files:**
- Modify: `src/main/java/com/project/wmsback/outbound/controller/OutbWaveController.java` (수동할당 매핑 제거)
- Modify: `src/main/java/com/project/wmsback/outbound/controller/OutbAllocController.java` (매핑 추가)
- Modify (wms-front): `src/api/outbAllocApi.js`

- [ ] **Step 1: 지금 매핑과 호출부를 찾는다**

```bash
grep -rn "allocations/manual" src/main/java/ ../wms-front/src/ 2>/dev/null
grep -rn "allocations/manual" /c/wms-front/src/
```

- [ ] **Step 2: 백엔드 매핑을 옮긴다**

`OutbWaveController`에서 `@PostMapping("/outbound/waves/{wavId}/allocations/manual")` 메서드를 잘라내 `OutbAllocController`로 옮기고 경로를 바꾼다. `wavId`는 경로 변수에서 **요청 본문 필드로** 내린다(경로가 웨이브 아래를 벗어나므로).

```java
    /**
     * 수동할당. 원래 /outbound/waves/{wavId} 아래 있었으나 할당 화면의 기능이라 이 이름공간으로 옮겼다
     * (2026-08-29) — 메뉴 권한이 「한 엔드포인트는 한 메뉴」를 전제한다.
     */
    @PostMapping("/manual")
    public void allocateManual(@RequestBody OutbAllocManualRequest request) {
        outbAllocService.allocateManual(request);
    }
```

`OutbAllocManualRequest`에 `wavId` 필드를 더한다(없으면 신설).

- [ ] **Step 3: 백엔드 테스트를 돌린다**

```bash
./mvnw test
echo "EXIT=$?"
```

Expected: `EXIT=0`. 실패하면 기존 테스트가 옛 경로를 쓰는 것이니 함께 고친다.

- [ ] **Step 4: 프론트 호출부를 바꾼다**

`/c/wms-front/src/api/outbAllocApi.js`에서 해당 함수의 경로와 인자를 바꾼다.

```js
    /** 수동할당. 2026-08-29에 경로가 /outbound/waves/{wavId}/... 에서 여기로 옮겨졌다 */
    allocateManual(body) {
        return api.post('/outbound/allocations/manual', body);
    },
```

호출부(`src/components/outbound/AllocCandidateModal.jsx`)가 `wavId`를 본문에 넣도록 고친다.

- [ ] **Step 5: 프론트 빌드**

```bash
cd /c/wms-front && npm run build
echo "EXIT=$?"
```

Expected: `EXIT=0`.

- [ ] **Step 6: 두 레포 각각 커밋**

```bash
cd /c/wms-backend && git add -A && \
  git commit -m "수동할당을 /outbound/allocations/manual로 — 할당 기능이 웨이브 접두 아래 있었다"
cd /c/wms-front && git add -A && \
  git commit -m "수동할당 호출 경로를 백엔드 이동에 맞춤"
```

> **이 둘은 붙여서 배포한다.** 한쪽만 올라가면 수동할당이 404다.

---

## Task 3: 사전 · 스키마 · 시드

**Files:**
- Modify: `docs/naming-dictionary.md`
- Modify: `docs/schema.sql`
- Create: `docs/seed-mnu.sql`
- Create: `docs/migration-add-mnu.sql`
- Modify: `docs/seed-dev.sql`

- [ ] **Step 1: 사전에 단어 넷을 넣는다**

`docs/naming-dictionary.md`의 **두 표 모두**에 넣는다(가나다순 표 · 약어 역인덱스). 머리말의 개수와 추가 이력도 함께 올린다.

가나다순 표(한글 순서에 맞는 자리에):

```
| 아이콘 | `ICON` | Icon |
| 키워드 | `KYWD` | Keyword |
| 화면 | `SCRN` | Screen |
| API | `API` | Application Programming Interface |
```

역인덱스 표(약어 알파벳 순서에 맞는 자리에):

```
| `API` | API | Application Programming Interface |
| `ICON` | 아이콘 | Icon |
| `KYWD` | 키워드 | Keyword |
| `SCRN` | 화면 | Screen |
```

머리말 문장 끝에 이어 붙인다: `· 2026-08-29 메뉴 권한 설계에서 화면 SCRN · 아이콘 ICON · 키워드 KYWD · API API 4개 추가`. 개수 `247`을 `251`로 고친다.

- [ ] **Step 2: 실제 개수를 세어 머리말과 맞는지 확인한다**

```bash
grep -cE '^\| `[A-Z_]+` \| ' docs/naming-dictionary.md
```

Expected: `251`. 다르면 머리말 숫자를 실측값으로 맞춘다(사전 자체가 "두 표를 실측한 값"이라고 적어 두었다).

- [ ] **Step 3: `docs/schema.sql`에 테이블 둘을 넣는다**

`usr_role` 정의 바로 다음에 넣는다(인증·권한이 모여 있게).

```sql
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
    CONSTRAINT uq_mnu_api_prfx UNIQUE (api_prfx),
    CONSTRAINT ck_mnu_dvsn CHECK (dvsn IN ('WEB', 'PDA'))
);

COMMENT ON TABLE  mnu IS '메뉴 카탈로그 (사이드바·PDA 홈의 주인)';
COMMENT ON COLUMN mnu.dvsn IS 'WEB 데스크톱 / PDA 현장 단말';
COMMENT ON COLUMN mnu.icon_nm IS 'lucide 아이콘 이름. 프론트 menuIcons.js가 컴포넌트로 바꾼다';
COMMENT ON COLUMN mnu.scrn_pth IS '프론트 라우트. App.jsx에 같은 경로가 있어야 한다';
COMMENT ON COLUMN mnu.api_prfx IS '이 화면의 쓰기 API 이름공간. NULL이면 조회 전용 화면이라 메뉴 권한이 관여하지 않는다';

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
```

- [ ] **Step 4: `docs/seed-mnu.sql`을 쓴다 — 형식이 고정이다**

`Sidebar.jsx`의 `MENU`와 `MobileHome.jsx`의 `GROUPS`를 그대로 옮긴다. **한 줄에 한 행**, 컬럼 순서는 `CREATE TABLE`과 같게. 테스트가 이 형식을 파싱한다.

```sql
-- 메뉴 카탈로그 시드. 이 파일이 시드의 주인이다 —
-- schema.sql 적용 경로 · migration-add-mnu.sql · MnuSeedCoverageTest 셋이 모두 이 파일을 쓴다.
-- 형식 고정: VALUES 뒤 한 줄에 한 행, 컬럼 순서는 CREATE TABLE과 같게, 주석은 행 끝 -- 만.

INSERT INTO mnu (mnu_cd, mnu_nm, dvsn, grp_nm, srt_seq, icon_nm, scrn_pth, api_prfx, kywd) VALUES
('DASHBOARD', '대시보드', 'WEB', '모니터링', 10, 'LayoutDashboard', '/', NULL, 'dashboard 홈 메인'),
('OMS_IB_ODR', '입고주문', 'WEB', 'OMS', 10, 'FileInput', '/oms/inbound-order', '/oms/ib-orders', '발주 po purchase order 등록'),
-- … MENU 44행 + PDA 8행. 아래는 반드시 채운다:
--   * api_prfx는 그 화면이 부르는 비GET 컨트롤러 접두. 조회 전용이면 NULL
--   * scrn_pth는 App.jsx에 실제로 있는 라우트
('MNU_MST', '메뉴 관리', 'WEB', '마스터', 90, 'List', '/master/menu', '/master/mnus', 'menu 메뉴 등록 순서'),
('MNU_AUTH', '권한별 메뉴 관리', 'WEB', '마스터', 91, 'ShieldCheck', '/master/menu-auth', '/master/mnus/roles', 'auth 권한 역할 메뉴'),
('PDA_PICKING', '피킹', 'PDA', '출고', 20, 'PackageOpen', '/m/picking', '/outbound/picking', 'pikng 집품');

INSERT INTO mnu_role (mnu_cd, role) VALUES
('OMS_IB_ODR', 'ODR_PIC'),
('OMS_IB_ODR', 'INQ');
-- … 현재 하드코딩된 roles를 그대로. 그룹 단위 roles는 항목마다 펼쳐 넣고 ADMR은 넣지 않는다
```

> **채우는 방법** — `Sidebar.jsx`의 그룹 `roles`가 항목의 기본값이고, 항목에 `roles`가 따로 있으면 그것이 이긴다. `ADMR`은 전부 뺀다. `MNU_MST`·`MNU_AUTH`는 `mnu_role` 행이 **하나도 없다**(관리자 전용).

- [ ] **Step 5: 마이그레이션을 쓴다**

`docs/migration-add-mnu.sql` — 전체를 `DO $mnu$ … $mnu$` 한 블록으로, 존재 확인을 걸어 재실행 가능하게.

```sql
-- 라이브 DB에 메뉴 권한 테이블을 추가한다. 재실행 안전.
-- DBeaver로 돌린다 — BEGIN;을 쓰면 실패 시 연결에 죽은 트랜잭션이 남아 이후 쿼리가 25P02를 뱉는다.
DO $mnu$
BEGIN
    IF to_regclass('public.mnu') IS NULL THEN
        CREATE TABLE mnu ( /* schema.sql과 같은 정의 */ );
    END IF;

    IF to_regclass('public.mnu_role') IS NULL THEN
        CREATE TABLE mnu_role ( /* schema.sql과 같은 정의 */ );
    END IF;

    -- 시드는 비어 있을 때만 넣는다 — 재실행해도 관리자가 편집한 값을 덮지 않는다
    IF NOT EXISTS (SELECT 1 FROM mnu) THEN
        INSERT INTO mnu (...) VALUES ...;   -- seed-mnu.sql 내용 그대로
        INSERT INTO mnu_role (...) VALUES ...;
    END IF;
END
$mnu$;
```

- [ ] **Step 6: `docs/seed-dev.sql`이 `seed-mnu.sql` 내용을 포함하게 한다**

새 DB가 바로 돌아야 한다. 기존 seed 파일 끝에 같은 두 `INSERT`를 넣는다.

- [ ] **Step 7: 커밋**

```bash
git add docs/naming-dictionary.md docs/schema.sql docs/seed-mnu.sql \
        docs/migration-add-mnu.sql docs/seed-dev.sql
git commit -m "메뉴 권한 테이블과 시드 — 사전에 화면·아이콘·키워드·API 4단어 추가"
```

---

## Task 4: 엔티티 · 리포지토리 · 불변식 테스트

**Files:**
- Create: `src/main/java/com/project/mdm/mnu/entity/Mnu.java`
- Create: `src/main/java/com/project/mdm/mnu/entity/MnuRole.java`
- Create: `src/main/java/com/project/mdm/mnu/entity/MnuRoleId.java`
- Create: `src/main/java/com/project/mdm/mnu/entity/MnuDvsn.java`
- Create: `src/main/java/com/project/mdm/mnu/repository/MnuRepository.java`
- Create: `src/main/java/com/project/mdm/mnu/repository/MnuRoleRepository.java`
- Test: `src/test/java/com/project/mdm/mnu/MnuSeedCoverageTest.java`

**Interfaces:**
- Produces: `Mnu` (getters: `getMnuCd` `getMnuNm` `getDvsn` `getGrpNm` `getSrtSeq` `getIconNm` `getScrnPth` `getApiPrfx` `getKywd`; `Mnu.builder()`; `update(String mnuNm, MnuDvsn dvsn, String grpNm, int srtSeq, String iconNm, String scrnPth, String apiPrfx, String kywd)`), `MnuRole(String mnuCd, Role role)`, `enum MnuDvsn { WEB, PDA }`, `MnuRepository.findAllByOrderByGrpNmAscSrtSeqAsc()`, `MnuRoleRepository.findAllByMnuCdIn(Collection<String>)`, `MnuRoleRepository.deleteByMnuCdIn(Collection<String>)`

- [ ] **Step 1: `MnuDvsn`과 엔티티 셋을 쓴다**

```java
package com.project.mdm.mnu.entity;

/** 메뉴가 어느 단말의 것인가. mnu.ck_mnu_dvsn과 값을 맞춘다 */
public enum MnuDvsn {
    WEB, PDA
}
```

`Mnu`는 `BaseEntity`를 상속하고 `@Id`가 `mnuCd`(String)다. `Zon` 엔티티의 형태를 그대로 따르되 PK가 생성값이 아니라 사용자 입력 코드인 점만 다르다.

`MnuRole`은 복합키다. `Usr`의 `@ElementCollection`과 달리 **독립 엔티티**로 둔다 — 역할로 거꾸로 조회하고 통째로 지웠다 다시 넣기 때문이다.

```java
package com.project.mdm.mnu.entity;

import java.io.Serializable;
import java.util.Objects;

/** mnu_role 복합키. @IdClass용이라 기본 생성자와 equals/hashCode가 필요하다 */
public class MnuRoleId implements Serializable {

    private String mnuCd;
    private String role;

    protected MnuRoleId() {
    }

    public MnuRoleId(String mnuCd, String role) {
        this.mnuCd = mnuCd;
        this.role = role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MnuRoleId that)) return false;
        return Objects.equals(mnuCd, that.mnuCd) && Objects.equals(role, that.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mnuCd, role);
    }
}
```

- [ ] **Step 2: 리포지토리 둘을 쓴다**

```java
public interface MnuRepository extends JpaRepository<Mnu, String> {
    List<Mnu> findAllByOrderByGrpNmAscSrtSeqAsc();
    boolean existsByScrnPth(String scrnPth);
    boolean existsByApiPrfx(String apiPrfx);
}
```

```java
public interface MnuRoleRepository extends JpaRepository<MnuRole, MnuRoleId> {
    List<MnuRole> findAllByMnuCdIn(Collection<String> mnuCds);
    void deleteByMnuCdIn(Collection<String> mnuCds);
}
```

- [ ] **Step 3: 불변식 테스트를 쓴다 (실패하는 상태로)**

```java
package com.project.mdm.mnu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「모든 비GET 엔드포인트는 적어도 한 메뉴의 api_prfx 아래 있다」를 빌드 시에 지킨다.
 * <p>
 * 메뉴는 DB에 있고 테스트는 DB를 올리지 않으므로, 시드 파일을 읽어 대조한다 —
 * 새 컨트롤러를 만들고 시드에 메뉴를 안 넣으면 여기서 걸린다.
 */
class MnuSeedCoverageTest {

    private static final Path SEED = Path.of("docs", "seed-mnu.sql");
    /** api_prfx는 INSERT INTO mnu 행의 8번째 값이다 */
    private static final Pattern ROW = Pattern.compile(
            "^\\('[^']*',\\s*'[^']*',\\s*'[^']*',\\s*'[^']*',\\s*\\d+,\\s*'[^']*',\\s*'[^']*',\\s*(NULL|'([^']*)')");
    /** 메뉴가 관장하지 않는 것이 정상인 접두 */
    private static final List<String> EXEMPT = List.of("/auth");

    @Test
    @DisplayName("시드의 api_prfx가 모든 비GET 엔드포인트를 덮는다")
    void seedCoversEveryWriteEndpoint() throws IOException {
        Set<String> prefixes = readApiPrefixes();
        List<String> uncovered = writeEndpointPaths().stream()
                .filter(p -> EXEMPT.stream().noneMatch(e -> under(e, p)))
                .filter(p -> prefixes.stream().noneMatch(x -> under(x, p)))
                .toList();

        assertTrue(uncovered.isEmpty(),
                "주인 없는 비GET 엔드포인트가 있다. docs/seed-mnu.sql에 메뉴를 넣거나 접두를 고쳐라: " + uncovered);
    }

    private static boolean under(String prefix, String path) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private Set<String> readApiPrefixes() throws IOException {
        Set<String> found = new LinkedHashSet<>();
        for (String line : Files.readAllLines(SEED, StandardCharsets.UTF_8)) {
            Matcher m = ROW.matcher(line.strip());
            if (m.find() && m.group(2) != null) {
                found.add(m.group(2));
            }
        }
        assertTrue(found.size() > 20, "시드를 못 읽었다. seed-mnu.sql 형식이 바뀌었는지 본다: " + found.size());
        return found;
    }

    /** 소스에서 직접 훑는다 — 스프링 컨텍스트를 올리면 DataSource를 요구한다 */
    private List<String> writeEndpointPaths() throws IOException {
        // 구현: src/main/java 아래 *Controller.java를 읽어
        // 클래스의 @RequestMapping("...") 과 메서드의 @Post/@Put/@Delete/@PatchMapping("...")을 합친다.
        // 메서드 애노테이션 값이 "/"로 시작하면 절대경로라 클래스 접두를 붙이지 않는다.
        // 경로변수 {..} 구간은 그대로 두되 접두 판정에는 영향이 없다.
        throw new UnsupportedOperationException("Step 4에서 구현한다");
    }
}
```

- [ ] **Step 4: `writeEndpointPaths()`를 구현한다**

`src/main/java` 아래를 걸어 `*Controller.java`를 읽고, 클래스 레벨 `@RequestMapping("x")`와 메서드 레벨 `@PostMapping`/`@PutMapping`/`@DeleteMapping`/`@PatchMapping`의 값을 조합한다. 값이 `/`로 시작하면 절대경로로 취급하고, 없으면 클래스 접두만 쓴다.

- [ ] **Step 5: 테스트를 돌린다**

```bash
./mvnw test -Dtest=MnuSeedCoverageTest
echo "EXIT=$?"
```

Expected: `EXIT=0`. 실패하면 **실패 메시지가 알려주는 엔드포인트를 `docs/seed-mnu.sql`에 넣거나 접두를 고친다.** 이 왕복이 시드를 완성시키는 정상 과정이다.

- [ ] **Step 6: 커밋**

```bash
./mvnw test && git add -A && \
  git commit -m "메뉴 엔티티와 시드 커버리지 테스트 — 주인 없는 엔드포인트가 있으면 빌드가 깨진다"
```

---

## Task 5: `MnuService` + 메뉴 관리 API

**Files:**
- Create: `src/main/java/com/project/mdm/mnu/dto/MnuResponse.java`
- Create: `src/main/java/com/project/mdm/mnu/dto/MnuSaveRequest.java`
- Create: `src/main/java/com/project/mdm/mnu/service/MnuService.java`
- Create: `src/main/java/com/project/mdm/mnu/controller/MnuController.java`
- Test: `src/test/java/com/project/mdm/mnu/service/MnuServiceTest.java`

**Interfaces:**
- Consumes: Task 4의 `Mnu` · `MnuRepository` · `MnuRoleRepository` · `MnuDvsn`
- Produces: `MnuService.list(MnuDvsn dvsn)` → `List<MnuResponse>`, `MnuService.saveAll(List<MnuSaveRequest>)`, `MnuResponse.from(Mnu)`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`ZonServiceTest`의 형태를 따른다(Mockito, DB 없음).

```java
    @Test
    @DisplayName("화면 경로가 겹치면 거부한다 — 사이드바에 같은 화면이 둘 뜬다")
    void rejectsDuplicateScrnPth() {
        when(mnuRepository.existsByScrnPth("/stock/spmt")).thenReturn(true);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("C", "NEW", "새 화면", "/stock/spmt", "/inventory/x"))));

        assertTrue(e.getMessage().contains("화면 경로"));
    }

    @Test
    @DisplayName("API 접두가 겹치면 거부한다 — 한 접두는 한 메뉴여야 판정이 하나로 정해진다")
    void rejectsDuplicateApiPrfx() {
        when(mnuRepository.existsByApiPrfx("/inventory/adjs")).thenReturn(true);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("C", "NEW", "새 화면", "/stock/new", "/inventory/adjs"))));

        assertTrue(e.getMessage().contains("API 접두"));
    }

    @Test
    @DisplayName("메뉴를 지우면 그 메뉴의 권한 행도 함께 지운다 — FK가 없어 DB가 안 치운다")
    void deleteAlsoRemovesRoles() {
        when(mnuRepository.findById("OLD")).thenReturn(Optional.of(mnu("OLD")));

        service.saveAll(List.of(row("D", "OLD", null, null, null)));

        verify(mnuRoleRepository).deleteByMnuCdIn(List.of("OLD"));
    }
```

- [ ] **Step 2: 테스트가 실패하는지 본다**

```bash
./mvnw test -Dtest=MnuServiceTest
echo "EXIT=$?"
```

Expected: 컴파일 실패 또는 FAIL.

- [ ] **Step 3: DTO와 서비스를 쓴다**

`MnuSaveRequest`는 `ZonSaveRequest` 형태 그대로 — `@JsonProperty("_status")`, `toEntity()`, `updateEntity(Mnu)`, 자기 필드 검사만.

`MnuService`는 `ZonService` 형태 그대로 — `saveAll`이 `C`/`U`/`D`를 갈라 처리하고 DB를 봐야 하는 검사를 여기서 한다.

```java
    private void create(MnuSaveRequest row) {
        Mnu mnu = row.toEntity();
        if (mnuRepository.existsById(mnu.getMnuCd())) {
            throw new IllegalArgumentException("이미 존재하는 메뉴 코드입니다: " + mnu.getMnuCd());
        }
        if (mnuRepository.existsByScrnPth(mnu.getScrnPth())) {
            throw new IllegalArgumentException("이미 쓰이는 화면 경로입니다: " + mnu.getScrnPth());
        }
        if (mnu.getApiPrfx() != null && mnuRepository.existsByApiPrfx(mnu.getApiPrfx())) {
            throw new IllegalArgumentException("이미 쓰이는 API 접두입니다: " + mnu.getApiPrfx());
        }
        mnuRepository.save(mnu);
    }

    private void delete(MnuSaveRequest row) {
        Mnu mnu = find(row.getMnuCd());
        // FK가 없어 DB가 안 치운다 — 남으면 없는 메뉴를 가리키는 권한 행이 된다
        mnuRoleRepository.deleteByMnuCdIn(List.of(mnu.getMnuCd()));
        mnuRepository.delete(mnu);
    }
```

- [ ] **Step 4: 컨트롤러를 쓴다**

```java
@RestController
@RequestMapping("/master/mnus")
@RequiredArgsConstructor
public class MnuController {

    private final MnuService mnuService;

    @GetMapping
    public List<MnuResponse> list(@RequestParam(required = false) MnuDvsn dvsn) {
        return mnuService.list(dvsn);
    }

    @PostMapping("/bulk")
    public void saveAll(@RequestBody List<MnuSaveRequest> rows) {
        mnuService.saveAll(rows);
    }
}
```

- [ ] **Step 5: 테스트를 돌린다**

```bash
./mvnw test -Dtest=MnuServiceTest
echo "EXIT=$?"
```

Expected: `EXIT=0`.

- [ ] **Step 6: 커밋**

```bash
git add -A && git commit -m "메뉴 관리 API — 화면 경로·API 접두 중복을 서비스가 막는다"
```

---

## Task 6: 권한 매핑 API

**Files:**
- Create: `src/main/java/com/project/mdm/mnu/dto/MnuRoleGridResponse.java`
- Create: `src/main/java/com/project/mdm/mnu/dto/MnuRoleSaveRequest.java`
- Modify: `src/main/java/com/project/mdm/mnu/service/MnuService.java`
- Modify: `src/main/java/com/project/mdm/mnu/controller/MnuController.java`
- Test: `src/test/java/com/project/mdm/mnu/service/MnuRoleServiceTest.java`

**Interfaces:**
- Consumes: `SecurityRules.canWrite(Role, String)` (Task 1), `MnuService` (Task 5)
- Produces: `MnuService.roleGrid(MnuDvsn)` → `List<MnuRoleGridResponse>`, `MnuService.replaceRoles(MnuDvsn, List<MnuRoleSaveRequest>)`
- `MnuRoleGridResponse`: `mnuCd` · `mnuNm` · `grpNm` · `srtSeq` · `roles`(`List<String>` 켜진 역할) · `readOnlyRoles`(`List<String>` 켜져 있으나 쓰기가 안 되는 역할 — 화면의 👁)

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
    @Test
    @DisplayName("쓰기가 안 되는 역할은 readOnlyRoles로 표시한다 — 막지 않고 알려만 준다")
    void marksReadOnlyRoles() {
        when(mnuRepository.findAllByOrderByGrpNmAscSrtSeqAsc())
                .thenReturn(List.of(mnu("MST_LOC", "/master/locs")));
        when(mnuRoleRepository.findAllByMnuCdIn(List.of("MST_LOC")))
                .thenReturn(List.of(new MnuRole("MST_LOC", Role.INV_PIC)));

        MnuRoleGridResponse row = service.roleGrid(MnuDvsn.WEB).get(0);

        assertEquals(List.of("INV_PIC"), row.getRoles());
        // /master/locs는 ADMR·CENT_ADMR만 쓴다 — INV_PIC은 열리지만 저장은 못 한다
        assertEquals(List.of("INV_PIC"), row.getReadOnlyRoles());
    }

    @Test
    @DisplayName("조회 전용 화면(api_prfx 없음)은 readOnlyRoles가 비어 있다")
    void readOnlyScreenHasNoMarks() {
        when(mnuRepository.findAllByOrderByGrpNmAscSrtSeqAsc())
                .thenReturn(List.of(mnu("STK_STATUS", null)));
        when(mnuRoleRepository.findAllByMnuCdIn(List.of("STK_STATUS")))
                .thenReturn(List.of(new MnuRole("STK_STATUS", Role.INQ)));

        assertTrue(service.roleGrid(MnuDvsn.WEB).get(0).getReadOnlyRoles().isEmpty());
    }

    @Test
    @DisplayName("매핑 저장은 그 탭을 통째로 교체한다 — 두 번 눌러도 결과가 같다")
    void replaceIsIdempotent() {
        when(mnuRepository.findAllByOrderByGrpNmAscSrtSeqAsc())
                .thenReturn(List.of(mnu("A", "/a"), mnu("B", "/b")));

        service.replaceRoles(MnuDvsn.WEB, List.of(save("A", List.of("INV_PIC"))));

        verify(mnuRoleRepository).deleteByMnuCdIn(List.of("A", "B"));
        verify(mnuRoleRepository).saveAll(argThat(it ->
                StreamSupport.stream(it.spliterator(), false).count() == 1));
    }

    @Test
    @DisplayName("ADMR은 저장하지 않는다 — 매핑 대상이 아니고 DB CHECK도 막는다")
    void ignoresAdmr() {
        when(mnuRepository.findAllByOrderByGrpNmAscSrtSeqAsc()).thenReturn(List.of(mnu("A", "/a")));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.replaceRoles(MnuDvsn.WEB, List.of(save("A", List.of("ADMR")))));

        assertTrue(e.getMessage().contains("시스템관리자"));
    }
```

- [ ] **Step 2: 실패를 확인한다**

```bash
./mvnw test -Dtest=MnuRoleServiceTest
echo "EXIT=$?"
```

Expected: 컴파일 실패 또는 FAIL.

- [ ] **Step 3: 서비스에 두 메서드를 더한다**

```java
    public List<MnuRoleGridResponse> roleGrid(MnuDvsn dvsn) {
        List<Mnu> menus = mnuRepository.findAllByOrderByGrpNmAscSrtSeqAsc().stream()
                .filter(m -> dvsn == null || m.getDvsn() == dvsn)
                .toList();
        Map<String, List<Role>> byMnu = mnuRoleRepository
                .findAllByMnuCdIn(menus.stream().map(Mnu::getMnuCd).toList()).stream()
                .collect(groupingBy(MnuRole::getMnuCd, mapping(MnuRole::getRole, toList())));

        return menus.stream()
                .map(m -> MnuRoleGridResponse.of(m, byMnu.getOrDefault(m.getMnuCd(), List.of())))
                .toList();
    }

    @Transactional
    public void replaceRoles(MnuDvsn dvsn, List<MnuRoleSaveRequest> rows) {
        List<String> scope = mnuRepository.findAllByOrderByGrpNmAscSrtSeqAsc().stream()
                .filter(m -> dvsn == null || m.getDvsn() == dvsn)
                .map(Mnu::getMnuCd)
                .toList();

        List<MnuRole> next = new ArrayList<>();
        for (MnuRoleSaveRequest row : rows) {
            if (!scope.contains(row.getMnuCd())) {
                throw new IllegalArgumentException("이 구분에 없는 메뉴입니다: " + row.getMnuCd());
            }
            for (String role : row.getRoles()) {
                if (Role.ADMR.name().equals(role)) {
                    throw new IllegalArgumentException("시스템관리자는 메뉴 권한 대상이 아닙니다.");
                }
                next.add(new MnuRole(row.getMnuCd(), Role.valueOf(role)));
            }
        }
        // 통째로 교체한다 — 체크박스 격자라 「지운 칸」을 따로 표현하는 것보다 단순하고 재실행이 안전하다
        mnuRoleRepository.deleteByMnuCdIn(scope);
        mnuRoleRepository.flush();
        mnuRoleRepository.saveAll(next);
        mnuAccessCache.reload();
    }
```

`MnuRoleGridResponse.of`가 `SecurityRules.canWrite`로 `readOnlyRoles`를 계산한다.

```java
    public static MnuRoleGridResponse of(Mnu mnu, List<Role> roles) {
        List<String> readOnly = mnu.getApiPrfx() == null ? List.of()
                : roles.stream()
                        .filter(r -> !SecurityRules.canWrite(r, mnu.getApiPrfx()))
                        .map(Enum::name)
                        .toList();
        return new MnuRoleGridResponse(mnu, roles.stream().map(Enum::name).toList(), readOnly);
    }
```

> `mnuAccessCache.reload()`는 Task 7에서 만든다. 그 전까지는 이 줄을 빼두고 Task 7에서 넣는다.

- [ ] **Step 4: 컨트롤러에 두 줄을 더한다**

```java
    @GetMapping("/roles")
    public List<MnuRoleGridResponse> roleGrid(@RequestParam(required = false) MnuDvsn dvsn) {
        return mnuService.roleGrid(dvsn);
    }

    @PutMapping("/roles")
    public void replaceRoles(@RequestParam(required = false) MnuDvsn dvsn,
                             @RequestBody List<MnuRoleSaveRequest> rows) {
        mnuService.replaceRoles(dvsn, rows);
    }
```

- [ ] **Step 5: 테스트를 돌린다**

```bash
./mvnw test -Dtest=MnuRoleServiceTest
echo "EXIT=$?"
```

Expected: `EXIT=0`.

- [ ] **Step 6: 커밋**

```bash
git add -A && git commit -m "권한 매핑 API — 격자 조회와 탭 단위 전량 교체"
```

---

## Task 7: 캐시 + `MnuAccessFilter`

여기서 ②가 실제로 켜진다. **가장 위험한 태스크다** — 시드가 틀리면 화면이 닫힌다.

**Files:**
- Create: `src/main/java/com/project/common/security/MnuAccessSource.java`
- Create: `src/main/java/com/project/common/security/MnuAccessFilter.java`
- Create: `src/main/java/com/project/mdm/mnu/service/MnuAccessCache.java`
- Modify: `src/main/java/com/project/common/config/SecurityConfig.java`
- Test: `src/test/java/com/project/common/security/MnuAccessFilterTest.java`
- Test: `src/test/java/com/project/common/security/MnuPrefixMatcherTest.java`

**Interfaces:**
- Consumes: `SecurityRules` (Task 1), `MnuRepository`·`MnuRoleRepository` (Task 4)
- Produces: `interface MnuAccessSource { boolean allows(List<String> roles, String path); }`, `MnuAccessCache.reload()`

- [ ] **Step 1: `MnuAccessSource`를 쓴다**

`common`이 `mdm`을 import하지 않아야 하므로 인터페이스만 `common`에 둔다.

```java
package com.project.common.security;

import java.util.List;

/**
 * 메뉴 권한을 묻는 창구. 구현은 {@code mdm.mnu}에 있다 —
 * {@code common}은 DB를 보지 않는다는 규칙 때문에 인터페이스만 여기 둔다.
 */
public interface MnuAccessSource {

    /** 이 역할들 중 하나라도 이 경로를 관장하는 메뉴를 갖고 있나. 관장하는 메뉴가 없으면 true(통과) */
    boolean allows(List<String> roles, String path);
}
```

- [ ] **Step 2: 접두 매칭 테스트를 쓰고 실패를 본다**

```java
    @Test
    @DisplayName("가장 긴 접두가 이긴다 — 겹침은 정상이다")
    void longestPrefixWins() {
        MnuAccessCache cache = cacheOf(
                Map.of("/master/mnus", List.of(Role.CENT_ADMR),
                       "/master/mnus/roles", List.of()));

        assertTrue(cache.allows(List.of("CENT_ADMR"), "/master/mnus/bulk"));
        // 더 긴 접두의 메뉴는 아무 역할에도 안 켜져 있다
        assertFalse(cache.allows(List.of("CENT_ADMR"), "/master/mnus/roles"));
    }

    @Test
    @DisplayName("접두는 세그먼트 경계를 지킨다 — /inventory/stock이 /inventory/stocktakes를 먹지 않는다")
    void respectsSegmentBoundary() {
        MnuAccessCache cache = cacheOf(Map.of("/inventory/stock", List.of()));

        assertTrue(cache.allows(List.of("INV_PIC"), "/inventory/stocktakes/1/confirm"));
    }

    @Test
    @DisplayName("관장하는 메뉴가 없는 경로는 통과한다 — 상한이 이미 봤다")
    void unmanagedPathPasses() {
        MnuAccessCache cache = cacheOf(Map.of("/inventory/adjs", List.of()));

        assertTrue(cache.allows(List.of("INQ"), "/auth/pwd"));
    }
```

```bash
./mvnw test -Dtest=MnuPrefixMatcherTest
echo "EXIT=$?"
```

Expected: 컴파일 실패.

- [ ] **Step 3: `MnuAccessCache`를 쓴다**

```java
package com.project.mdm.mnu.service;

/**
 * 메뉴 권한 캐시. 요청마다 DB를 보지 않으려고 통째로 들고 있는다.
 * <p>
 * <b>인스턴스 하나를 전제한다</b>(Render 무료 플랜). 인스턴스를 늘리면 저장을 처리하지 않은
 * 쪽이 옛 권한으로 도므로, 그때는 짧은 TTL이나 알림이 필요하다.
 */
@Component
@RequiredArgsConstructor
public class MnuAccessCache implements MnuAccessSource {

    private final MnuRepository mnuRepository;
    private final MnuRoleRepository mnuRoleRepository;

    /** 접두 → 그 메뉴가 켜진 역할 이름들. 긴 접두 우선으로 정렬해 둔다 */
    private volatile List<Entry> entries = List.of();

    private record Entry(String prefix, Set<String> roles) {
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reload() {
        List<Mnu> menus = mnuRepository.findAll();
        if (menus.isEmpty()) {
            log.warn("[MNU] 메뉴 카탈로그가 비어 있다 — 시드를 적용했는지 확인할 것. "
                    + "지금은 시스템관리자만 정상 동작한다");
        }
        Map<String, List<Role>> byMnu = /* Task 6의 groupingBy와 같은 방식 */;
        this.entries = menus.stream()
                .filter(m -> m.getApiPrfx() != null)
                .map(m -> new Entry(m.getApiPrfx(),
                        byMnu.getOrDefault(m.getMnuCd(), List.<Role>of()).stream()
                                .map(Enum::name).collect(toSet())))
                .sorted(comparing((Entry e) -> e.prefix().length()).reversed())
                .toList();
        warnUncoveredEndpoints();
    }

    @Override
    public boolean allows(List<String> roles, String path) {
        for (Entry entry : entries) {          // 긴 접두부터 — 첫 매칭이 그 경로의 주인이다
            if (under(entry.prefix(), path)) {
                return roles.stream().anyMatch(entry.roles()::contains);
            }
        }
        return true;                           // 관장하는 메뉴가 없다 — 상한이 이미 봤다
    }

    private static boolean under(String prefix, String path) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}
```

`warnUncoveredEndpoints()`는 `RequestMappingHandlerMapping`을 훑어 주인 없는 비GET 경로를 `log.warn`으로 남긴다(라이브 DB가 시드와 갈라진 경우를 잡는다).

- [ ] **Step 4: 접두 매칭 테스트가 통과하는지 본다**

```bash
./mvnw test -Dtest=MnuPrefixMatcherTest
echo "EXIT=$?"
```

Expected: `EXIT=0`.

- [ ] **Step 5: 필터 테스트를 쓴다**

```java
    @Test
    @DisplayName("메뉴가 꺼진 역할은 상한을 통과해도 막힌다")
    void blockedWhenMenuOff() throws Exception {
        given(mnuAccessSource.allows(anyList(), eq("/inventory/adjs"))).willReturn(false);

        mvc.perform(post("/inventory/adjs").with(user("t").roles("INV_PIC")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET은 메뉴가 꺼져 있어도 통과한다 — 화면 여럿이 같은 조회 API를 쓴다")
    void getIsNeverBlocked() throws Exception {
        given(mnuAccessSource.allows(anyList(), any())).willReturn(false);

        mvc.perform(get("/inventory/stock").with(user("t").roles("INV_PIC")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMR은 메뉴가 꺼져 있어도 통과한다 — 잠김 방지")
    void admrBypasses() throws Exception {
        given(mnuAccessSource.allows(anyList(), any())).willReturn(false);

        mvc.perform(post("/master/prods/bulk").with(user("t").roles("ADMR")).with(csrf()))
                .andExpect(status().isOk());
    }
```

- [ ] **Step 6: `MnuAccessFilter`를 쓰고 `SecurityConfig`에 등록한다**

```java
/** ② 메뉴 권한. ①(SecurityConfig 상한)을 통과한 요청만 여기 온다 */
@RequiredArgsConstructor
public class MnuAccessFilter extends OncePerRequestFilter {

    private final MnuAccessSource source;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (blocked(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"이 화면의 권한이 없습니다.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean blocked(HttpServletRequest request) {
        if (HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        return AuthUser.current()
                .filter(user -> !user.roles().contains(Role.ADMR.name()))
                .map(user -> !source.allows(user.roles(), request.getRequestURI()))
                .orElse(false);
    }
}
```

`SecurityConfig`에 `.addFilterAfter(new MnuAccessFilter(mnuAccessSource), AuthorizationFilter.class)`로 등록한다. `MnuAccessSource`는 생성자 주입.

- [ ] **Step 7: 필터 테스트와 기존 보안 테스트를 함께 돌린다**

```bash
./mvnw test -Dtest='MnuAccessFilterTest+SecurityRulesTest'
echo "EXIT=$?"
```

Expected: `EXIT=0`. `SecurityRulesTest`에는 `@MockBean MnuAccessSource`를 넣고 `allows`가 항상 `true`를 돌려주게 해 **상한만** 검증하게 한다.

- [ ] **Step 8: Task 6에서 빼뒀던 `mnuAccessCache.reload()`를 `replaceRoles`와 `saveAll` 끝에 넣는다**

- [ ] **Step 9: 전체 테스트**

```bash
./mvnw test
echo "EXIT=$?"
```

Expected: `EXIT=0`.

- [ ] **Step 10: 커밋**

```bash
git add -A && git commit -m "메뉴 권한 검사 필터 — 상한을 통과해도 메뉴가 꺼져 있으면 막는다"
```

---

## Task 8: `/auth/me`에 메뉴를 싣는다

**Files:**
- Modify: `src/main/java/com/project/mdm/usr/dto/AuthDtos.java`
- Modify: `src/main/java/com/project/mdm/usr/controller/AuthController.java`
- Modify: `src/main/java/com/project/mdm/usr/service/AuthService.java`
- Test: `src/test/java/com/project/mdm/usr/service/AuthServiceMenuTest.java`

**Interfaces:**
- Consumes: `MnuService` (Task 5), `MnuRoleRepository` (Task 4)
- Produces: `MnuResponse` 목록이 `LoginResponse`·`MeResponse`의 마지막 필드 `menus`로 실린다

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
    @Test
    @DisplayName("ADMR은 DB를 보지 않고 전 메뉴를 받는다")
    void admrGetsEveryMenu() {
        when(mnuRepository.findAllByOrderByGrpNmAscSrtSeqAsc()).thenReturn(List.of(mnu("A"), mnu("B")));

        assertEquals(2, service.menusOf(List.of("ADMR")).size());
        verify(mnuRoleRepository, never()).findAllByMnuCdIn(any());
    }

    @Test
    @DisplayName("담당은 자기 역할에 켜진 메뉴만 받는다")
    void picGetsOnlyGrantedMenus() {
        when(mnuRepository.findAllByOrderByGrpNmAscSrtSeqAsc()).thenReturn(List.of(mnu("A"), mnu("B")));
        when(mnuRoleRepository.findAllByMnuCdIn(List.of("A", "B")))
                .thenReturn(List.of(new MnuRole("A", Role.INV_PIC)));

        assertEquals(List.of("A"),
                service.menusOf(List.of("INV_PIC")).stream().map(MnuResponse::getMnuCd).toList());
    }
```

- [ ] **Step 2: 실패를 확인한다**

```bash
./mvnw test -Dtest=AuthServiceMenuTest
echo "EXIT=$?"
```

- [ ] **Step 3: DTO 두 개에 필드를 더한다**

```java
    public record LoginResponse(String loginId, String usrNm, List<String> roles,
                                String csrfToken, List<MnuResponse> menus) {
    }

    /**
     * {@code menus}는 매번 DB에서 새로 읽는다 — {@code AuthUser}(세션)에 담으면 로그인 시점의
     * 메뉴가 굳어 관리자가 매핑을 바꿔도 옛 메뉴로 돈다. JWT를 버린 이유와 같은 문제다.
     */
    public record MeResponse(String loginId, String usrNm, List<String> roles,
                             String csrfToken, List<MnuResponse> menus) {
    }
```

- [ ] **Step 4: `AuthService.menusOf(List<String> roles)`를 만들고 컨트롤러 두 곳에서 부른다**

- [ ] **Step 5: 테스트를 돌린다**

```bash
./mvnw test
echo "EXIT=$?"
```

Expected: `EXIT=0`.

- [ ] **Step 6: 커밋**

```bash
git add -A && git commit -m "로그인 응답에 메뉴를 싣는다 — 세션에는 담지 않는다"
```

---

## Task 9: 프론트 — 사이드바·PDA를 서버 메뉴로 전환

**작업 디렉터리: `C:\wms-front`**

**Files:**
- Create: `src/layout/menuIcons.js`
- Modify: `src/auth/AuthContext.jsx`
- Modify: `src/layout/Sidebar.jsx`
- Modify: `src/pages/mobile/MobileHome.jsx`

**Interfaces:**
- Consumes: `/auth/me`의 `menus` (Task 8) — 각 항목 `{ mnuCd, mnuNm, dvsn, grpNm, srtSeq, iconNm, scrnPth, kywd }`
- Produces: `useAuth()`가 `menus`를 더 돌려준다

- [ ] **Step 1: 아이콘 이름표를 만든다**

지금 `Sidebar.jsx`와 `MobileHome.jsx`가 import하는 lucide 아이콘 전부를 한 사전에 모은다.

```js
// 아이콘 이름 → lucide 컴포넌트. 메뉴가 DB로 갔지만 아이콘은 컴포넌트라 담을 수 없어 여기 남는다.
// 메뉴가 늘어도 「새 아이콘 종류」가 필요할 때만 바뀌는 일반 사전이다.
import { ArrowLeftRight, Barcode, Box, /* … 지금 쓰는 것 전부 */ } from 'lucide-react';

export const MENU_ICONS = { ArrowLeftRight, Barcode, Box, /* … */ };

/** 이름표에 없으면 기본 아이콘. 시드 오타로 화면이 깨지지 않게 한다 */
export function menuIcon(name) {
    const found = MENU_ICONS[name];
    if (!found && import.meta.env.DEV) {
        console.warn(`[menu] 이름표에 없는 아이콘: ${name}`);
    }
    return found ?? MENU_ICONS.Box;
}
```

- [ ] **Step 2: `AuthContext`가 `menus`를 들게 한다**

`apply`가 캐시하는 값에 `menus`를 더한다. **캐시가 있으면 첫 페인트에 사이드바가 바로 그려져** 빈 사이드바가 보이지 않는다.

```js
    const apply = useCallback((me) => {
        setCsrfToken(me.csrfToken);
        const shown = { loginId: me.loginId, usrNm: me.usrNm, roles: me.roles, menus: me.menus ?? [] };
        localStorage.setItem(USER_KEY, JSON.stringify(shown));
        setUser(shown);
        return shown;
    }, []);
```

`value`에 `menus: user?.menus ?? []`를 더한다.

- [ ] **Step 3: `Sidebar.jsx`에서 `MENU` 배열을 지우고 서버 메뉴로 그린다**

`const MENU = [...]` 44줄과 lucide import 목록을 지운다. `visible`을 이렇게 바꾼다.

```js
    const { user, logout, menus } = useAuth();

    // 서버가 준 메뉴만 그린다 — 역할 판정은 백엔드가 이미 했다
    const visible = useMemo(() => {
        const byGroup = new Map();
        menus.filter(m => m.dvsn === 'WEB')
            .slice()
            .sort((a, b) => a.srtSeq - b.srtSeq)
            .forEach(m => {
                if (!byGroup.has(m.grpNm)) byGroup.set(m.grpNm, []);
                byGroup.get(m.grpNm).push({
                    to: m.scrnPth, label: m.mnuNm, icon: menuIcon(m.iconNm), keywords: m.kywd ?? '',
                });
            });
        return [...byGroup.entries()].map(([title, items]) => ({ title, items }));
    }, [menus]);
```

`hasRole` import는 남긴다(다른 자리에서 쓴다). 검색·펼침·스크롤 로직은 **손대지 않는다** — `visible`의 모양이 같아서 그대로 돈다.

> **그룹 순서** — `grpNm`만으로는 그룹 사이 순서를 알 수 없다. 각 그룹의 **최소 `srtSeq`** 순으로 그룹을 정렬한다. 시드에서 그룹별 `srt_seq` 대역을 겹치지 않게 준다(모니터링 10번대, OMS 100번대, 입고 200번대 …).

- [ ] **Step 4: `MobileHome.jsx`도 같게 바꾼다**

`GROUPS` 배열을 지우고 `menus.filter(m => m.dvsn === 'PDA')`로 그린다. 「현장 작업 권한이 없습니다」 화면은 **PDA 메뉴가 0개일 때** 뜨게 조건만 바꾼다.

- [ ] **Step 5: 빌드와 린트**

```bash
cd /c/wms-front && npm run build && npx eslint src/layout/Sidebar.jsx src/pages/mobile/MobileHome.jsx src/auth/AuthContext.jsx src/layout/menuIcons.js
echo "EXIT=$?"
```

Expected: `EXIT=0`.

- [ ] **Step 6: 커밋**

```bash
git add -A && git commit -m "사이드바·PDA를 서버 메뉴로 전환 — 하드코딩 배열 52줄 제거"
```

---

## Task 10: 프론트 — 메뉴 관리 화면

**Files:**
- Create: `src/api/mnuApi.js`
- Create: `src/pages/master/MnuMaster.jsx`
- Modify: `src/App.jsx`

**Interfaces:**
- Consumes: `GET /master/mnus`, `POST /master/mnus/bulk` (Task 5)
- Produces: `src/App.jsx`가 `export const ROUTE_PATHS`로 등록된 라우트 목록을 내보낸다 — 화면 경로 드롭다운이 쓴다

- [ ] **Step 1: API 모듈**

```js
// 메뉴 마스터 API — wms-backend의 com.project.mdm.mnu 연동. 시스템관리자만 열린다
import api from '@/utils/axios';

export const mnuApi = {
    list(dvsn) {
        return api.get('/master/mnus', { params: dvsn ? { dvsn } : {} });
    },
    saveAll(rows) {
        return api.post('/master/mnus/bulk', rows);
    },
    roleGrid(dvsn) {
        return api.get('/master/mnus/roles', { params: dvsn ? { dvsn } : {} });
    },
    replaceRoles(dvsn, rows) {
        return api.put('/master/mnus/roles', rows, { params: dvsn ? { dvsn } : {} });
    },
};
```

- [ ] **Step 2: `App.jsx`가 라우트 목록을 내보내게 한다**

```js
/** 메뉴 관리 화면의 화면 경로 드롭다운이 쓴다 — 직접 입력을 막아 죽은 링크가 나올 길을 없앤다 */
export const ROUTE_PATHS = [
    '/', '/oms/inbound-order', '/oms/inbound-orders', /* … 등록된 라우트 전부 */
];
```

- [ ] **Step 3: `MnuMaster.jsx`를 쓴다**

`UsrMaster.jsx`의 골격을 그대로 따른다 — `useMasterGrid` 훅, `SearchBar`, `RowStatusCell`, `ConfirmModal`, `SaveCountSummary`. 컬럼만 다르다.

```jsx
    const columnDefs = [
        { headerName: 'No.', width: 60, editable: false,
          valueGetter: (p) => p.node.rowIndex + 1, cellClass: 'text-slate-400' },
        { field: 'mnuCd', headerName: '메뉴 코드', width: 150,
          headerClass: 'header-required', editable: newRowOnly },
        { field: 'mnuNm', headerName: '메뉴명', width: 170,
          headerClass: 'header-required', editable: notDeleted },
        { field: 'dvsn', headerName: '구분', width: 90, editable: notDeleted,
          cellEditor: 'agSelectCellEditor', cellEditorParams: { values: ['WEB', 'PDA'] } },
        { field: 'grpNm', headerName: '그룹', width: 110, editable: notDeleted },
        { field: 'srtSeq', headerName: '순서', width: 80, editable: notDeleted, ...num },
        { field: 'iconNm', headerName: '아이콘', width: 150, editable: notDeleted,
          cellEditor: 'agSelectCellEditor',
          cellEditorParams: { values: Object.keys(MENU_ICONS) } },
        { field: 'scrnPth', headerName: '화면 경로', width: 200, editable: notDeleted,
          cellEditor: 'agSelectCellEditor', cellEditorParams: { values: ROUTE_PATHS } },
        { field: 'apiPrfx', headerName: 'API 접두', width: 190, editable: notDeleted,
          headerTooltip: '이 화면의 저장 API 앞부분. 비우면 조회 전용 화면이라 권한이 관여하지 않는다' },
        { field: 'kywd', headerName: '검색 키워드', flex: 1, editable: notDeleted },
    ];
```

**「주인 없는 엔드포인트」 패널** — 목록 위에 접히는 영역을 두고, 응답에 실려 온 `uncoveredEndpoints`가 있으면 노란 배너로 띄운다.

```jsx
    {uncovered.length > 0 && (
        <div className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm">
            <b className="text-amber-800">주인 없는 저장 API {uncovered.length}건</b>
            <p className="text-amber-700 mt-1">
                아래 주소는 어느 메뉴에도 속하지 않아 메뉴 권한이 관여하지 못합니다.
                메뉴를 추가하거나 API 접두를 고쳐 주세요.
            </p>
            <ul className="mt-2 font-mono text-xs text-amber-900">
                {uncovered.map(p => <li key={p}>{p}</li>)}
            </ul>
        </div>
    )}
```

> 백엔드 `GET /master/mnus` 응답에 `uncoveredEndpoints`를 함께 실어야 한다. Task 5의 `MnuController.list`가 `MnuService.uncoveredEndpoints()`를 호출하도록 이 단계에서 더한다.

- [ ] **Step 4: 라우트를 등록한다**

```jsx
{/* 메뉴 관리 — 시스템관리자만. 사이드바 자체를 정의하는 화면이라 마스터 그룹에 둔다 */}
<Route path="/master/menu" element={<MnuMaster/>}/>
```

- [ ] **Step 5: 빌드**

```bash
cd /c/wms-front && npm run build && npx eslint src/pages/master/MnuMaster.jsx src/api/mnuApi.js
echo "EXIT=$?"
```

Expected: `EXIT=0`.

- [ ] **Step 6: 커밋**

```bash
git add -A && git commit -m "메뉴 관리 화면 — 경로·아이콘은 드롭다운, 주인 없는 API는 배너로"
```

---

## Task 11: 프론트 — 권한별 메뉴 관리 화면

**Files:**
- Create: `src/pages/master/MnuAuthMaster.jsx`
- Modify: `src/App.jsx`

**Interfaces:**
- Consumes: `GET /master/mnus/roles`, `PUT /master/mnus/roles` (Task 6) — 각 행 `{ mnuCd, mnuNm, grpNm, srtSeq, roles, readOnlyRoles }`

- [ ] **Step 1: 화면을 쓴다**

`useMasterGrid`를 쓰지 않는다 — 행 C/U/D 그리드가 아니라 체크박스 격자다. `AgGridReact`를 직접 쓴다.

```jsx
const ROLE_CODES = ['CENT_ADMR', 'ODR_PIC', 'IB_PIC', 'INV_PIC', 'OUTB_PIC', 'INQ'];

export default function MnuAuthMaster() {
    const [dvsn, setDvsn] = useState('WEB');
    const [rowData, setRowData] = useState([]);
    const [dirty, setDirty] = useState(false);

    const load = useCallback(async () => {
        const rows = await mnuApi.roleGrid(dvsn);
        // 역할 목록을 열별 boolean으로 편다 — 격자로 보려면 이 모양이어야 한다
        setRowData(rows.map(r => ({
            ...r,
            ...Object.fromEntries(ROLE_CODES.map(c => [c, r.roles.includes(c)])),
        })));
        setDirty(false);
    }, [dvsn]);

    useEffect(() => { load(); }, [load]);

    const columnDefs = [
        { field: 'grpNm', headerName: '그룹', width: 110, rowGroup: false, editable: false,
          cellClass: 'text-slate-400' },
        { field: 'mnuNm', headerName: '메뉴', width: 200, editable: false },
        ...ROLE_CODES.map(code => ({
            field: code, headerName: ROLE_LABELS[code], width: 120,
            editable: true, cellRenderer: RoleCheckCell,
            cellRendererParams: { code },
        })),
    ];

    const save = async () => {
        await mnuApi.replaceRoles(dvsn, rowData.map(r => ({
            mnuCd: r.mnuCd,
            roles: ROLE_CODES.filter(c => r[c]),
        })));
        toast.success('저장했습니다. 사용자에게는 다음 접속부터 보입니다.');
        load();
    };
```

**`RoleCheckCell`** — 체크 상태와 👁를 함께 그린다.

```jsx
/** 켜져 있지만 저장은 못 하는 칸에 눈 아이콘을 붙인다 — 막지 않고 무엇을 준 것인지만 알린다 */
function RoleCheckCell({ value, data, code }) {
    const readOnly = value && data.readOnlyRoles?.includes(code);
    return (
        <span className="flex items-center gap-1">
            <input type="checkbox" checked={!!value} readOnly className="pointer-events-none"/>
            {readOnly && <Eye size={13} className="text-amber-500"
                              title="열리지만 저장은 안 됩니다 (업무 구역 상한)"/>}
        </span>
    );
}
```

**그룹 헤더 일괄 체크** — 그룹명이 바뀌는 자리에 구분 행을 넣고, 그 행의 역할 칸을 누르면 그 그룹 전체가 토글되게 한다.

- [ ] **Step 2: 범례와 안내를 넣는다**

화면 하단에 고정으로 둔다.

```
☑ 저장까지 됨    ☑👁 열리지만 저장은 안 됨 (업무 구역 상한)
시스템관리자는 항상 모든 메뉴를 봅니다 — 그래서 열이 없습니다.
```

- [ ] **Step 3: 라우트 등록**

```jsx
<Route path="/master/menu-auth" element={<MnuAuthMaster/>}/>
```

- [ ] **Step 4: 빌드**

```bash
cd /c/wms-front && npm run build && npx eslint src/pages/master/MnuAuthMaster.jsx
echo "EXIT=$?"
```

Expected: `EXIT=0`.

- [ ] **Step 5: 커밋**

```bash
git add -A && git commit -m "권한별 메뉴 관리 화면 — 역할 격자와 조회전용 표시"
```

---

## Task 12: 문서 갱신과 배포

**Files:**
- Modify: `docs/design.md`
- Modify: `docs/screen-list.html`
- Modify: `CLAUDE.md`

- [ ] **Step 1: `docs/design.md` 「인증과 역할」에 절을 더한다**

담을 것 — 인가가 두 단계가 된 이유, ②가 GET을 건너뛰는 이유, `ADMR`이 매핑 대상이 아닌 이유(잠김 방지), 캐시가 인스턴스 하나를 전제한다는 것, 「한 엔드포인트는 한 메뉴」 불변식과 그것을 두 겹으로 지키는 방법.

- [ ] **Step 2: `docs/screen-list.html`을 고친다**

사이드바 절의 *"메뉴는 역할로 걸러진다(`MENU` 항목의 `roles` + `hasRole`)"* 문장을 **DB 기준**으로 바꾸고, 마스터 그룹에 「메뉴 관리」·「권한별 메뉴 관리」 두 행을 더한다. 화면 수(40 → 42)와 라우트 수(42 → 44)를 함께 고친다.

- [ ] **Step 3: `CLAUDE.md`의 보안 문단을 고친다**

지금 *"접근 규칙은 `SecurityConfig` 한 곳의 URL 접두 표이고"*라고 단정한 부분에 **②가 생겼다**는 것을 적는다. 새 컨트롤러를 만들 때 **시드에 메뉴를 넣지 않으면 빌드가 깨진다**는 것도 함께 적는다 — 이 레포에서 일하는 사람이 가장 먼저 부딪힐 규칙이다.

- [ ] **Step 4: 전체 테스트**

```bash
cd /c/wms-backend && ./mvnw test
echo "EXIT=$?"
cd /c/wms-front && npm run build
echo "EXIT=$?"
```

Expected: 둘 다 `EXIT=0`.

- [ ] **Step 5: 커밋**

```bash
cd /c/wms-backend && git add -A && git commit -m "메뉴 권한 문서 갱신 — 인가가 두 단계가 됐다"
```

- [ ] **Step 6: 배포 — 순서를 지킨다**

```
① DBeaver로 docs/migration-add-mnu.sql 실행       ← 먼저. 테이블과 시드가 있어야 한다
② 백엔드 push (Render)                            ← menus를 주기 시작한다
③ 프론트 push (Cloudflare Pages)                  ← 서버 메뉴로 그리기 시작한다
```

②와 ③ 사이에는 프론트가 옛 코드라 자기 배열로 그린다 — 잠깐 어긋나지만 깨지지는 않는다. ③이 먼저 가면 `menus` 없는 응답을 받아 **사이드바가 빈다.**

각 푸시 전에 `git fetch`로 원격이 앞서 있는지 확인하고, 앞서 있으면 병합 후 **양쪽 변경이 다 살아 있는지 눈으로 확인**한다.

- [ ] **Step 7: 배포 후 확인**

- 관리자로 로그인 → 사이드바가 지금과 **똑같이** 보이는가 (시딩 성공 기준)
- 재고담당으로 로그인 → 재고 그룹 + 고정 로케이션만 보이는가
- 권한별 메뉴 관리에서 재고담당의 「재고조정」을 끄고 저장 → 재고담당으로 다시 로그인 → 메뉴가 사라지고, 주소로 직접 들어가 저장하면 403인가
- 조회 계정으로 `/m` → 「현장 작업 권한이 없습니다」가 뜨는가
- 백엔드 로그에 `[MNU]` 경고가 없는가

---

## Self-Review

**스펙 커버리지**

| 스펙 장 | 태스크 |
|---|---|
| 3 사전 단어 넷 | Task 3 |
| 4 데이터 모델 | Task 3(SQL) · Task 4(엔티티) |
| 5 두 단계 인가 · `SecurityRules` | Task 1 |
| 5 `MnuAccessFilter` · 캐시 · 잠김 방지 | Task 7 |
| 5 불변식(빌드 시) | Task 4 |
| 5 불변식(실행 중) | Task 7(경고 로그) · Task 10(배너) |
| 5 수동할당 엔드포인트 이동 | Task 2 |
| 6 API 넷 · 검증 경계 | Task 5 · Task 6 |
| 7 로그인 응답 · 세션 미차단 | Task 8 |
| 8 사이드바·PDA 전환 · 아이콘 이름표 | Task 9 |
| 8 화면 둘 | Task 10 · Task 11 |
| 9 시딩과 배포 순서 | Task 3 · Task 12 |
| 10 테스트 | 각 태스크에 분산 |

**빠진 것을 채운 자리** — 스펙 8장의 「메뉴 관리 화면이 라우트에 없는 경로를 표시해 잡는다」가 태스크에 없어서, Task 10 Step 2에 `App.jsx`의 `ROUTE_PATHS` export와 드롭다운을 넣었다. 스펙 5장의 실행 중 진단(주인 없는 엔드포인트)도 Task 10 Step 3의 배너와 백엔드 응답 필드로 채웠다.

**이름 일관성** — `MnuAccessSource.allows(List<String> roles, String path)`가 Task 7에서 정의되고 Task 7의 필터에서만 쓰인다. `MnuService.roleGrid`/`replaceRoles`는 Task 6에서 정의되어 Task 10·11의 API 모듈이 부른다. `menuIcon(name)`은 Task 9에서 정의되어 Task 10의 아이콘 드롭다운이 `MENU_ICONS`를 함께 쓴다.
