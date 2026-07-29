# 채번관리(Number Generation) 시스템 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `nbr_rule`/`nbr_seq` 테이블 기반 공통 채번 모듈(`com.project.wmsback.master`)을 만들고, 기존 6개 시퀀스 기반 채번(`prod_cd`/`vndr_cd`/`oms_ib_no`/`ib_no`/`outb_no`/`outb_wave_no`)을 여기로 이관한다.

**Architecture:** `master` 패키지에 `Zon`/`CodeDetail`과 같은 모양의 엔티티 2개(`NbrRule` 자연키 코드 테이블, `NbrSeq` 복합키 카운터)를 추가하고, QueryDSL `PESSIMISTIC_WRITE` 락 + `INSERT ... ON CONFLICT DO NOTHING` upsert로 동시 발급을 처리한다. 패턴 파싱/렌더링은 순수 로직 클래스(`NbrPattern`)로 분리해 저장 시 검증과 발급 시 렌더링에서 재사용한다. 기존 6개 호출부는 `nbrService.issue(ruleCd)`(고정 규칙) 또는 `nbrService.issue(ruleCd, LocalDate)`(호출자가 정하는 날짜 기준 규칙)로 교체한다.

**Tech Stack:** Spring Boot, Spring Data JPA + QueryDSL(JPAQueryFactory), PostgreSQL(Supabase), Lombok, JUnit 5 + Mockito(spring-boot-starter-test에 포함, 추가 의존성 없음).

## Global Constraints

- 새 컬럼/필드명은 `docs/naming-dictionary.md` 사전 단어만 조합한다. 이번 작업으로 `규칙(RULE)`/`패턴(PTRN)` 2단어를 추가로 등재한다.
- FK를 걸지 않는다 (`docs/schema.sql` 전역 정책 — FK 0건).
- 마이그레이션 SQL은 `BEGIN;`/`COMMIT;`을 쓰지 않고 전체를 `DO $tag$ … $tag$` 블록 하나로 작성하며, 재실행 가능해야 한다(존재 확인 필수).
- `spring.jpa.hibernate.ddl-auto=none` — 스키마는 항상 `docs/schema.sql`(+마이그레이션)을 먼저 DB에 적용한 뒤 엔티티를 맞춘다.
- `omsback`은 `wmsback`을 import할 수 있지만 반대는 불가 — 채번 모듈은 `wmsback.master`에 두고 `omsback`이 가져다 쓴다.
- 컨트롤러 경로는 `/api` 접두 없이 `/{도메인}/{리소스 복수형}`, 다단어는 kebab-case, 그리드 CRUD는 `GET` 목록 + `POST .../bulk`(행마다 `_status`: C/U/D) 컨벤션을 따른다(`ZonController` 등과 동일).
- 참조 없음 오류는 `IllegalArgumentException`(→400), 상태 충돌은 `IllegalStateException`(→409)으로 던진다 — `GlobalExceptionHandler`가 이미 전역 처리한다.
- 이 저장소에는 DB 연동 테스트 인프라(H2/Testcontainers 등)가 없다 — 리포지토리 계층(QueryDSL 락, 네이티브 upsert)과 마이그레이션 SQL, 컨트롤러는 자동화 테스트 대상이 아니고 수동 검증(`./mvnw spring-boot:run` + 로컬 Postgres)으로 확인한다. 순수 로직(`NbrPattern`)과 Mockito로 리포지토리를 모킹할 수 있는 서비스 계층만 JUnit 테스트를 작성한다.
- 설계 근거: `docs/superpowers/specs/2026-07-29-nbr-design.md` (필요시 각 태스크에서 참조)

---

## Task 1: 표준 단어 사전에 `규칙`/`패턴` 등재

**Files:**
- Modify: `docs/naming-dictionary.md`

**Interfaces:** 없음 (문서 변경만)

- [ ] **Step 1: 가나다순 표에 `규칙` 추가**

`docs/naming-dictionary.md`에서 다음 두 줄 사이(58~59번째 줄 부근, `규격` 다음·`그룹` 앞):

```
| 규격 | `STND` | Standard |
| 그룹 | `GRP` | Group |
```

`규칙` 행을 끼워 넣는다:

```
| 규격 | `STND` | Standard |
| 규칙 | `RULE` | Rule |
| 그룹 | `GRP` | Group |
```

- [ ] **Step 2: 가나다순 표에 `패턴` 추가**

같은 표에서 `팝업` 다음·`팩스` 앞(232~233번째 줄 부근):

```
| 팝업 | `PPUP` | Popup |
| 팩스 | `FAX` | Fax |
```

`패턴` 행을 끼워 넣는다:

```
| 팝업 | `PPUP` | Popup |
| 패턴 | `PTRN` | Pattern |
| 팩스 | `FAX` | Fax |
```

- [ ] **Step 3: 약어 역인덱스에 `PTRN`/`RULE` 추가**

`PTH` 다음·`PWD` 앞(410~411번째 줄 부근):

```
| `PTH` | 경로 | Path |
| `PWD` | 비밀번호 | Password |
```

```
| `PTH` | 경로 | Path |
| `PTRN` | 패턴 | Pattern |
| `PWD` | 비밀번호 | Password |
```

`RTNGS` 다음·`SEQ` 앞(428~429번째 줄 부근):

```
| `RTNGS` | 반품 | Returning Goods |
| `SEQ` | 순서 | Sequence |
```

```
| `RTNGS` | 반품 | Returning Goods |
| `RULE` | 규칙 | Rule |
| `SEQ` | 순서 | Sequence |
```

- [ ] **Step 4: 단어 수 갱신**

파일 7번째 줄:

```
216개 단어이며 한글·약어 모두 중복이 없다.
```

를

```
218개 단어이며 한글·약어 모두 중복이 없다.
```

로 바꾼다.

- [ ] **Step 5: 확인**

```bash
grep -n "규칙\|패턴\|PTRN\|RULE" docs/naming-dictionary.md
```

`규칙`/`RULE`/`패턴`/`PTRN`이 가나다순 표 1곳 + 역인덱스 1곳, 총 2번씩 나오는지 확인한다.

- [ ] **Step 6: 커밋**

```bash
git add docs/naming-dictionary.md
git commit -m "docs: 표준 단어 사전에 규칙(RULE)/패턴(PTRN) 추가"
```

---

## Task 2: `docs/schema.sql`에 `nbr_rule`/`nbr_seq` 반영, 기존 6개 시퀀스 제거

**Files:**
- Modify: `docs/schema.sql`

**Interfaces:**
- Produces: `nbr_rule(rule_cd PK, rule_nm, ptrn, dync_ky_typ, us_yn, ...감사컬럼)`, `nbr_seq(rule_cd+dync_ky PK, seq, ...감사컬럼)` — 이후 모든 태스크가 이 스키마를 전제로 엔티티를 작성한다.

- [ ] **Step 1: `nbr_rule`/`nbr_seq` 테이블 정의 추가**

`code_detail` 테이블의 `COMMENT ON COLUMN code_detail.srt_seq ...` 줄과, 그 다음의 `-- 공통코드 시드 (...)` 주석 사이에 삽입한다:

```sql
-- 채번 규칙. code_group과 같은 자연키 코드 테이블 — 항상 코드로 조회한다.
CREATE TABLE nbr_rule (
    rule_cd     VARCHAR(30)     NOT NULL,
    rule_nm     VARCHAR(100)    NOT NULL,
    ptrn        VARCHAR(200)    NOT NULL,
    dync_ky_typ VARCHAR(10)     NOT NULL,
    us_yn       CHAR(1)         DEFAULT 'Y' NOT NULL,
    created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by  VARCHAR(30)     DEFAULT 'admin' NOT NULL,
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(30),
    CONSTRAINT pk_nbr_rule PRIMARY KEY (rule_cd),
    CONSTRAINT ck_nbr_rule_dync_ky_typ CHECK (dync_ky_typ IN ('NONE', 'DATE')),
    CONSTRAINT ck_nbr_rule_us_yn CHECK (us_yn IN ('Y', 'N'))
);

COMMENT ON TABLE  nbr_rule IS '채번 규칙. 패턴 문자열 하나로 형식을 정의 (예: PROD-{SEQ:4}, IB-{yyyyMMdd}-{SEQ:3})';
COMMENT ON COLUMN nbr_rule.rule_cd     IS '채번 규칙 코드 (업무 식별자, 예: PROD_CD). 코드성 테이블이라 자연키를 PK로 쓴다 (code_group과 동일 패턴)';
COMMENT ON COLUMN nbr_rule.ptrn        IS '채번 패턴. 토큰: {SEQ:n}(n=1~9 zero-pad, 정확히 1개 필수) + 날짜 토큰({yyyyMMdd}/{yyyy}/{MM}/{dd}). 그 외 문자는 리터럴';
COMMENT ON COLUMN nbr_rule.dync_ky_typ IS '동적키 유형. NONE=카운터 전역 공유(dync_ky 고정값 -) / DATE=호출자가 넘긴 날짜 기준으로 카운터 분리(일 단위 리셋)';
COMMENT ON COLUMN nbr_rule.us_yn       IS '사용 여부. N이면 발급 요청 시 거부 (과거 발급분은 영향 없음)';

-- 채번 카운터. rule_cd+dync_ky 조합별 현재 발급값. 관리자가 만들지 않고 최초 발급 시 자동 생성된다.
CREATE TABLE nbr_seq (
    rule_cd     VARCHAR(30)     NOT NULL,
    dync_ky     VARCHAR(30)     NOT NULL,
    seq         BIGINT          DEFAULT 0 NOT NULL,
    created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by  VARCHAR(30)     DEFAULT 'admin' NOT NULL,
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(30),
    CONSTRAINT pk_nbr_seq PRIMARY KEY (rule_cd, dync_ky)
);

COMMENT ON TABLE  nbr_seq IS '채번 카운터. rule_cd+dync_ky별 현재 발급값. FK 없음 — rule_cd는 nbr_rule.rule_cd를 느슨하게 참조';
COMMENT ON COLUMN nbr_seq.dync_ky IS '동적키 값. dync_ky_typ=NONE이면 고정값 "-", DATE면 yyyyMMdd';
COMMENT ON COLUMN nbr_seq.seq     IS '현재 발급값. 발급마다 +1, updated_at이 곧 최종 발급 시각';
```

- [ ] **Step 2: `nbr_rule` 시드 6행 추가**

기존 `code_detail` 시드 INSERT 블록(`COMMIT;` 직전) 끝에 이어서 추가한다:

```sql
INSERT INTO nbr_rule (rule_cd, rule_nm, ptrn, dync_ky_typ) VALUES
    ('PROD_CD',     '상품 코드',        'PROD-{SEQ:4}',           'NONE'),
    ('VNDR_CD',     '벤더 코드',        'VD-{SEQ:4}',             'NONE'),
    ('OMS_IB_NO',   '입고주문 번호',    'PO-{yyyyMMdd}-{SEQ:3}',  'DATE'),
    ('IB_NO',       '입고 번호',        'IB-{yyyyMMdd}-{SEQ:3}',  'DATE'),
    ('OUTB_NO',     '출고 번호',        'OB-{yyyyMMdd}-{SEQ:3}',  'DATE'),
    ('OUTB_WAV_NO', '출고 웨이브 번호', 'WV-{yyyyMMdd}-{SEQ:3}',  'DATE');
```

- [ ] **Step 3: 옛 시퀀스 6개와 관련 주석 삭제**

다음 6개 블록(각 `CREATE SEQUENCE ...` 줄과 바로 위 설명 주석 한 줄)을 통째로 삭제한다:

```sql
-- 상품 코드 채번 시퀀스 (PROD-0001 형식의 업무 코드 전용, PK identity와 분리 운용).
-- MAX(prod_cd)+1 방식은 동시 INSERT 시 같은 값을 읽어 중복 채번되므로 시퀀스로 발급한다.
-- 이미 seed 데이터가 있다면 START WITH를 (기존 최대 채번값 + 1)로 맞출 것.
CREATE SEQUENCE prod_cd_seq START WITH 1 INCREMENT BY 1;
```

```sql
-- 벤더 코드 채번 시퀀스 (VD-0001 형식)
CREATE SEQUENCE vndr_cd_seq START WITH 1 INCREMENT BY 1;
```

```sql
-- 입고주문 번호 채번 시퀀스 (PO-YYYYMMDD-NNN 형식. 전역 유일성만 보장, 일자 리셋 없음)
CREATE SEQUENCE oms_ib_no_seq START WITH 1 INCREMENT BY 1;
```

```sql
-- 입고번호 채번 시퀀스 (IB-YYYYMMDD-NNN 형식. 시퀀스는 전역이라 일자별로 리셋되지 않는다 — 유일성만 보장)
CREATE SEQUENCE ib_no_seq START WITH 1 INCREMENT BY 1;
```

```sql
-- 웨이브 번호 채번 시퀀스 (WV-YYYYMMDD-NNN 형식. 전역 유일성만 보장, 일자 리셋 없음)
CREATE SEQUENCE outb_wave_no_seq START WITH 1 INCREMENT BY 1;
```

```sql
-- 출고번호 채번 시퀀스 (OB-YYYYMMDD-NNN 형식. 전역 유일성만 보장, 일자 리셋 없음)
CREATE SEQUENCE outb_no_seq START WITH 1 INCREMENT BY 1;
```

- [ ] **Step 4: 확인**

```bash
grep -n "CREATE SEQUENCE\|CREATE TABLE nbr" docs/schema.sql
```

`CREATE SEQUENCE`가 한 건도 없고, `nbr_rule`/`nbr_seq` 두 테이블이 있는지 확인한다.

- [ ] **Step 5: 커밋**

```bash
git add docs/schema.sql
git commit -m "docs: schema.sql에 nbr_rule/nbr_seq 반영, 옛 채번 시퀀스 6개 제거"
```

---

## Task 3: `NbrPattern` — 패턴 검증/렌더링 (TDD)

**Files:**
- Create: `src/main/java/com/project/wmsback/master/entity/DyncKyTyp.java` (이 태스크에서 먼저 만든다 — `NbrPattern`이 참조)
- Create: `src/main/java/com/project/wmsback/master/service/NbrPattern.java`
- Test: `src/test/java/com/project/wmsback/master/service/NbrPatternTest.java`

**Interfaces:**
- Produces:
  - `DyncKyTyp` enum `{ NONE, DATE }` (`com.project.wmsback.master.entity`)
  - `NbrPattern.validate(String ptrn, DyncKyTyp dyncKyTyp)` — 검증 실패 시 `IllegalArgumentException`
  - `NbrPattern.render(String ptrn, long seq, LocalDate de)` — 검증을 통과한 패턴만 넘긴다는 전제(내부에서 재검증하지 않음)

- [ ] **Step 1: `DyncKyTyp` enum 작성**

```java
package com.project.wmsback.master.entity;

/** 채번 규칙의 동적키 유형. NONE=카운터 전역 공유 / DATE=호출자가 넘긴 날짜 기준으로 카운터 분리 */
public enum DyncKyTyp {
    NONE,
    DATE
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

```java
package com.project.wmsback.master.service;

import com.project.wmsback.master.entity.DyncKyTyp;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NbrPatternTest {

    @Test
    void SEQ_토큰이_없으면_검증_실패() {
        assertThrows(IllegalArgumentException.class,
                () -> NbrPattern.validate("PROD-0001", DyncKyTyp.NONE));
    }

    @Test
    void SEQ_토큰이_2개면_검증_실패() {
        assertThrows(IllegalArgumentException.class,
                () -> NbrPattern.validate("PROD-{SEQ:4}-{SEQ:2}", DyncKyTyp.NONE));
    }

    @Test
    void 알수없는_토큰이면_검증_실패() {
        assertThrows(IllegalArgumentException.class,
                () -> NbrPattern.validate("PROD-{DEPT}-{SEQ:4}", DyncKyTyp.NONE));
    }

    @Test
    void DATE_타입인데_날짜_토큰이_없으면_검증_실패() {
        assertThrows(IllegalArgumentException.class,
                () -> NbrPattern.validate("IB-{SEQ:3}", DyncKyTyp.DATE));
    }

    @Test
    void NONE_타입은_날짜_토큰_없이도_통과() {
        NbrPattern.validate("PROD-{SEQ:4}", DyncKyTyp.NONE);
    }

    @Test
    void DATE_타입은_날짜_토큰_있으면_통과() {
        NbrPattern.validate("IB-{yyyyMMdd}-{SEQ:3}", DyncKyTyp.DATE);
    }

    @Test
    void render이_SEQ를_자릿수만큼_zero_pad() {
        String result = NbrPattern.render("PROD-{SEQ:4}", 7, LocalDate.of(2026, 7, 29));
        assertEquals("PROD-0007", result);
    }

    @Test
    void render이_날짜_토큰을_전달받은_날짜로_치환() {
        String result = NbrPattern.render("IB-{yyyyMMdd}-{SEQ:3}", 12, LocalDate.of(2026, 8, 25));
        assertEquals("IB-20260825-012", result);
    }

    @Test
    void render이_seq가_자릿수를_넘으면_그대로_늘어남() {
        String result = NbrPattern.render("PROD-{SEQ:4}", 12345, LocalDate.of(2026, 7, 29));
        assertEquals("PROD-12345", result);
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./mvnw test -Dtest=NbrPatternTest`
Expected: FAIL (컴파일 에러 — `NbrPattern` 클래스가 없음)

- [ ] **Step 4: `NbrPattern` 구현**

```java
package com.project.wmsback.master.service;

import com.project.wmsback.master.entity.DyncKyTyp;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 채번 패턴 문자열 파싱. 지원 토큰: {SEQ:n}(n=1~9, 패턴당 정확히 1개) + 날짜 토큰.
 * 날짜 토큰 이름({yyyyMMdd}/{yyyy}/{MM}/{dd})은 그 자체로 java.time.format.DateTimeFormatter
 * 패턴 문자열이라 별도 매핑 없이 그대로 재사용한다.
 * render()는 validate()를 통과한 패턴에만 쓴다 — 검증되지 않은 알 수 없는 토큰이 오면
 * 날짜 포맷 시도로 넘어가 DateTimeFormatter가 던지는 예외가 그대로 새어나간다.
 */
final class NbrPattern {

    private static final Pattern TOKEN = Pattern.compile("\\{([^}]*)}");
    private static final Pattern SEQ_TOKEN = Pattern.compile("SEQ:([1-9])");
    private static final Set<String> DATE_TOKENS = Set.of("yyyyMMdd", "yyyy", "MM", "dd");

    private NbrPattern() {
    }

    static void validate(String ptrn, DyncKyTyp dyncKyTyp) {
        Matcher matcher = TOKEN.matcher(ptrn);
        int seqCount = 0;
        boolean hasDateToken = false;
        while (matcher.find()) {
            String token = matcher.group(1);
            if (SEQ_TOKEN.matcher(token).matches()) {
                seqCount++;
            } else if (DATE_TOKENS.contains(token)) {
                hasDateToken = true;
            } else {
                throw new IllegalArgumentException("지원하지 않는 채번 패턴 토큰입니다: {" + token + "}");
            }
        }
        if (seqCount != 1) {
            throw new IllegalArgumentException("채번 패턴은 {SEQ:n} 토큰을 정확히 1개 포함해야 합니다: " + ptrn);
        }
        if (dyncKyTyp == DyncKyTyp.DATE && !hasDateToken) {
            throw new IllegalArgumentException(
                    "동적키유형이 DATE이면 날짜 토큰({yyyyMMdd} 등)이 패턴에 1개 이상 있어야 합니다: " + ptrn);
        }
    }

    static String render(String ptrn, long seq, LocalDate de) {
        Matcher matcher = TOKEN.matcher(ptrn);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            Matcher seqMatcher = SEQ_TOKEN.matcher(token);
            String replacement = seqMatcher.matches()
                    ? String.format("%0" + seqMatcher.group(1) + "d", seq)
                    : de.format(DateTimeFormatter.ofPattern(token));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./mvnw test -Dtest=NbrPatternTest`
Expected: PASS (9개 전부)

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/project/wmsback/master/entity/DyncKyTyp.java \
        src/main/java/com/project/wmsback/master/service/NbrPattern.java \
        src/test/java/com/project/wmsback/master/service/NbrPatternTest.java
git commit -m "feat: 채번 패턴 검증/렌더링(NbrPattern) 추가"
```

---

## Task 4: `NbrRule` 엔티티

**Files:**
- Create: `src/main/java/com/project/wmsback/master/entity/NbrRule.java`
- Test: `src/test/java/com/project/wmsback/master/entity/NbrRuleTest.java`

**Interfaces:**
- Consumes: `DyncKyTyp` (Task 3)
- Produces: `NbrRule` — `getRuleCd()/getRuleNm()/getPtrn()/getDyncKyTyp()/getUsYn()`, `isUsable()`, `update(ruleNm, ptrn, usYn)`, `NbrRule.builder()`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.project.wmsback.master.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbrRuleTest {

    @Test
    void usYn을_지정하지_않으면_기본값_Y() {
        NbrRule rule = NbrRule.builder()
                .ruleCd("PROD_CD").ruleNm("상품 코드").ptrn("PROD-{SEQ:4}").dyncKyTyp(DyncKyTyp.NONE)
                .build();
        assertEquals("Y", rule.getUsYn());
        assertTrue(rule.isUsable());
    }

    @Test
    void usYn_N이면_isUsable_false() {
        NbrRule rule = NbrRule.builder()
                .ruleCd("PROD_CD").ruleNm("상품 코드").ptrn("PROD-{SEQ:4}").dyncKyTyp(DyncKyTyp.NONE)
                .usYn("N")
                .build();
        assertFalse(rule.isUsable());
    }

    @Test
    void update은_ruleNm_ptrn_usYn만_바꾸고_ruleCd와_dyncKyTyp은_유지() {
        NbrRule rule = NbrRule.builder()
                .ruleCd("PROD_CD").ruleNm("상품 코드").ptrn("PROD-{SEQ:4}").dyncKyTyp(DyncKyTyp.NONE)
                .build();

        rule.update("상품 코드(수정)", "PROD-{SEQ:5}", "N");

        assertEquals("PROD_CD", rule.getRuleCd());
        assertEquals(DyncKyTyp.NONE, rule.getDyncKyTyp());
        assertEquals("상품 코드(수정)", rule.getRuleNm());
        assertEquals("PROD-{SEQ:5}", rule.getPtrn());
        assertEquals("N", rule.getUsYn());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./mvnw test -Dtest=NbrRuleTest`
Expected: FAIL (컴파일 에러 — `NbrRule` 클래스가 없음)

- [ ] **Step 3: `NbrRule` 구현**

```java
package com.project.wmsback.master.entity;

import com.project.wmsback.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채번 규칙. code_group과 같은 자연키 코드 테이블 — ruleCd 자체가 PK다.
 * dyncKyTyp은 등록 후 변경 불가 (nbr_seq와의 정합성이 깨지므로 update()가 파라미터로 받지 않는다).
 */
@Entity
@Table(name = "nbr_rule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NbrRule extends BaseEntity {

    @Id
    @Column(name = "rule_cd", length = 30)
    private String ruleCd;

    @Column(name = "rule_nm", nullable = false, length = 100)
    private String ruleNm;

    @Column(name = "ptrn", nullable = false, length = 200)
    private String ptrn;

    @Enumerated(EnumType.STRING)
    @Column(name = "dync_ky_typ", nullable = false, length = 10)
    private DyncKyTyp dyncKyTyp;

    /** 사용 여부. 'N'이면 발급 요청 거부 (과거 발급분은 영향 없음) */
    @Column(name = "us_yn", nullable = false, length = 1)
    private String usYn;

    @Builder
    private NbrRule(String ruleCd, String ruleNm, String ptrn, DyncKyTyp dyncKyTyp, String usYn) {
        this.ruleCd = ruleCd;
        this.ruleNm = ruleNm;
        this.ptrn = ptrn;
        this.dyncKyTyp = dyncKyTyp;
        this.usYn = usYn != null ? usYn : "Y";
    }

    public void update(String ruleNm, String ptrn, String usYn) {
        this.ruleNm = ruleNm;
        this.ptrn = ptrn;
        this.usYn = usYn;
    }

    public boolean isUsable() {
        return "Y".equals(usYn);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./mvnw test -Dtest=NbrRuleTest`
Expected: PASS (3개 전부)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/project/wmsback/master/entity/NbrRule.java \
        src/test/java/com/project/wmsback/master/entity/NbrRuleTest.java
git commit -m "feat: NbrRule 엔티티 추가"
```

---

## Task 5: `NbrSeq` 엔티티 (복합키)

**Files:**
- Create: `src/main/java/com/project/wmsback/master/entity/NbrSeqId.java`
- Create: `src/main/java/com/project/wmsback/master/entity/NbrSeq.java`
- Test: `src/test/java/com/project/wmsback/master/entity/NbrSeqTest.java`

**Interfaces:**
- Produces: `NbrSeq` — `getRuleCd()/getDyncKy()/getSeq()`, `increment()`, `NbrSeq.builder()`(테스트 전용 — 운영 코드는 이 엔티티를 네이티브 upsert로만 만든다. Task 7 참고)

- [ ] **Step 1: `NbrSeqId` 복합키 클래스 작성 (`CodeDetailId`와 동일한 모양)**

```java
package com.project.wmsback.master.entity;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** NbrSeq 복합키 (rule_cd + dync_ky) */
@NoArgsConstructor
@EqualsAndHashCode
public class NbrSeqId implements Serializable {

    private String ruleCd;
    private String dyncKy;
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

```java
package com.project.wmsback.master.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NbrSeqTest {

    @Test
    void increment은_seq를_1_증가시킨다() {
        NbrSeq row = NbrSeq.builder().ruleCd("PROD_CD").dyncKy("-").seq(3L).build();

        row.increment();

        assertEquals(4L, row.getSeq());
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./mvnw test -Dtest=NbrSeqTest`
Expected: FAIL (컴파일 에러 — `NbrSeq` 클래스가 없음)

- [ ] **Step 4: `NbrSeq` 구현**

```java
package com.project.wmsback.master.entity;

import com.project.wmsback.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채번 카운터. rule_cd+dync_ky별 현재 발급값.
 * 실제 신규 행은 애플리케이션이 이 엔티티를 직접 save()하지 않고, 네이티브
 * INSERT ... ON CONFLICT DO NOTHING 업서트로 만든다 (NbrSeqRepository.insertIfAbsent, Task 7).
 * 여기 @Builder는 테스트에서 인스턴스를 만들 때만 쓴다.
 */
@Entity
@Table(name = "nbr_seq")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(NbrSeqId.class)
public class NbrSeq extends BaseEntity {

    @Id
    @Column(name = "rule_cd", length = 30)
    private String ruleCd;

    @Id
    @Column(name = "dync_ky", length = 30)
    private String dyncKy;

    @Column(name = "seq", nullable = false)
    private Long seq;

    @Builder
    private NbrSeq(String ruleCd, String dyncKy, Long seq) {
        this.ruleCd = ruleCd;
        this.dyncKy = dyncKy;
        this.seq = seq;
    }

    public void increment() {
        this.seq += 1;
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./mvnw test -Dtest=NbrSeqTest`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/project/wmsback/master/entity/NbrSeqId.java \
        src/main/java/com/project/wmsback/master/entity/NbrSeq.java \
        src/test/java/com/project/wmsback/master/entity/NbrSeqTest.java
git commit -m "feat: NbrSeq 엔티티 추가 (복합키 rule_cd+dync_ky)"
```

---

## Task 6: `NbrRuleRepository` (+Custom/Impl)

**Files:**
- Create: `src/main/java/com/project/wmsback/master/dto/NbrRuleSearchCond.java`
- Create: `src/main/java/com/project/wmsback/master/repository/NbrRuleRepository.java`
- Create: `src/main/java/com/project/wmsback/master/repository/NbrRuleRepositoryCustom.java`
- Create: `src/main/java/com/project/wmsback/master/repository/NbrRuleRepositoryImpl.java`

**Interfaces:**
- Consumes: `NbrRule`(Task 4)
- Produces: `NbrRuleRepository extends JpaRepository<NbrRule, String>` (기본 `findById`/`existsById`/`save`/`delete`/`flush` 그대로 사용), `NbrRuleRepositoryCustom.search(NbrRuleSearchCond)`

이 태스크는 QueryDSL Q클래스(`QNbrRule`)가 필요하므로, 시작 전에 `./mvnw compile`을 한 번 돌려 Task 4에서 만든 `NbrRule` 엔티티의 Q클래스를 생성해둔다.

- [ ] **Step 1: `./mvnw compile`로 `QNbrRule` 생성 확인**

Run: `./mvnw compile`
Expected: BUILD SUCCESS, `target/generated-sources/annotations/com/project/wmsback/master/entity/QNbrRule.java` 생성됨

- [ ] **Step 2: `NbrRuleSearchCond` DTO**

```java
package com.project.wmsback.master.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 채번 규칙 목록 검색 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class NbrRuleSearchCond {

    private String ruleCd;
    private String ruleNm;
    private String usYn;
}
```

- [ ] **Step 3: `NbrRuleRepositoryCustom`/`Impl`**

```java
package com.project.wmsback.master.repository;

import com.project.wmsback.master.dto.NbrRuleSearchCond;
import com.project.wmsback.master.entity.NbrRule;

import java.util.List;

public interface NbrRuleRepositoryCustom {

    List<NbrRule> search(NbrRuleSearchCond cond);
}
```

```java
package com.project.wmsback.master.repository;

import com.project.wmsback.master.dto.NbrRuleSearchCond;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.wmsback.master.entity.QNbrRule.nbrRule;
import com.project.wmsback.master.entity.NbrRule;

@RequiredArgsConstructor
public class NbrRuleRepositoryImpl implements NbrRuleRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<NbrRule> search(NbrRuleSearchCond cond) {
        return queryFactory
                .selectFrom(nbrRule)
                .where(
                        ruleCdContains(cond.getRuleCd()),
                        ruleNmContains(cond.getRuleNm()),
                        usYnEq(cond.getUsYn())
                )
                .orderBy(nbrRule.ruleCd.asc())
                .fetch();
    }

    private BooleanExpression ruleCdContains(String ruleCd) {
        return StringUtils.hasText(ruleCd) ? nbrRule.ruleCd.containsIgnoreCase(ruleCd) : null;
    }

    private BooleanExpression ruleNmContains(String ruleNm) {
        return StringUtils.hasText(ruleNm) ? nbrRule.ruleNm.containsIgnoreCase(ruleNm) : null;
    }

    private BooleanExpression usYnEq(String usYn) {
        return StringUtils.hasText(usYn) ? nbrRule.usYn.eq(usYn) : null;
    }
}
```

(import 정렬은 IDE 저장 시 자동 정리되는 걸 전제로 순서를 크게 신경 쓰지 않았다 — 실제 작성 시 `NbrRule` import를 다른 import들과 함께 상단에 정리할 것.)

- [ ] **Step 4: `NbrRuleRepository`**

```java
package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.NbrRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NbrRuleRepository extends JpaRepository<NbrRule, String>, NbrRuleRepositoryCustom {
}
```

- [ ] **Step 5: 컴파일 확인**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/project/wmsback/master/dto/NbrRuleSearchCond.java \
        src/main/java/com/project/wmsback/master/repository/NbrRuleRepository.java \
        src/main/java/com/project/wmsback/master/repository/NbrRuleRepositoryCustom.java \
        src/main/java/com/project/wmsback/master/repository/NbrRuleRepositoryImpl.java
git commit -m "feat: NbrRuleRepository 추가 (QueryDSL 검색)"
```

---

## Task 7: `NbrSeqRepository` (+Custom/Impl, 락 + upsert)

**Files:**
- Create: `src/main/java/com/project/wmsback/master/repository/NbrSeqRepositoryCustom.java`
- Create: `src/main/java/com/project/wmsback/master/repository/NbrSeqRepositoryImpl.java`
- Create: `src/main/java/com/project/wmsback/master/repository/NbrSeqRepository.java`

**Interfaces:**
- Consumes: `NbrSeq`, `NbrSeqId`(Task 5)
- Produces:
  - `NbrSeqRepositoryCustom.findByIdForUpdate(String ruleCd, String dyncKy): Optional<NbrSeq>` — `ProdRepositoryImpl.findByIdForUpdate`와 동일하게 `PESSIMISTIC_WRITE`
  - `NbrSeqRepository.insertIfAbsent(String ruleCd, String dyncKy): void` — 네이티브 `INSERT ... ON CONFLICT DO NOTHING`
  - `NbrSeqRepository.findByRuleCdOrderByDyncKy(String ruleCd): List<NbrSeq>`

- [ ] **Step 1: `NbrSeqRepositoryCustom`/`Impl`**

```java
package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.NbrSeq;
import com.querydsl.jpa.impl.JPAQueryFactory;

import java.util.Optional;

public interface NbrSeqRepositoryCustom {

    Optional<NbrSeq> findByIdForUpdate(String ruleCd, String dyncKy);
}
```

```java
package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.NbrSeq;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

import static com.project.wmsback.master.entity.QNbrSeq.nbrSeq;

@RequiredArgsConstructor
public class NbrSeqRepositoryImpl implements NbrSeqRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<NbrSeq> findByIdForUpdate(String ruleCd, String dyncKy) {
        return Optional.ofNullable(
                queryFactory
                        .selectFrom(nbrSeq)
                        .where(nbrSeq.ruleCd.eq(ruleCd), nbrSeq.dyncKy.eq(dyncKy))
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                        .fetchOne());
    }
}
```

- [ ] **Step 2: `NbrSeqRepository`**

```java
package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.NbrSeq;
import com.project.wmsback.master.entity.NbrSeqId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NbrSeqRepository extends JpaRepository<NbrSeq, NbrSeqId>, NbrSeqRepositoryCustom {

    /** 규칙 관리 화면의 "현재 카운터 조회" 읽기전용 목록 */
    List<NbrSeq> findByRuleCdOrderByDyncKy(String ruleCd);

    /**
     * 최초 발급 시 카운터 행이 없으면 만든다. ON CONFLICT DO NOTHING을 쓰는 이유:
     * 동시에 첫 발급이 몰려 PK 충돌이 나면 PostgreSQL은 그 순간 트랜잭션을 abort 상태로
     * 만들어(25P02) 같은 트랜잭션의 후속 조회까지 실패한다 — DataIntegrityViolationException을
     * catch하는 방식은 여기서 쓸 수 없다. ON CONFLICT DO NOTHING은 예외 자체를 던지지 않는다.
     * seq/created_at/created_by는 DB 기본값(0 / CURRENT_TIMESTAMP / 'admin')에 맡긴다.
     */
    @Modifying
    @Query(value = "INSERT INTO nbr_seq (rule_cd, dync_ky) VALUES (:ruleCd, :dyncKy) "
            + "ON CONFLICT (rule_cd, dync_ky) DO NOTHING", nativeQuery = true)
    void insertIfAbsent(@Param("ruleCd") String ruleCd, @Param("dyncKy") String dyncKy);
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/project/wmsback/master/repository/NbrSeqRepositoryCustom.java \
        src/main/java/com/project/wmsback/master/repository/NbrSeqRepositoryImpl.java \
        src/main/java/com/project/wmsback/master/repository/NbrSeqRepository.java
git commit -m "feat: NbrSeqRepository 추가 (PESSIMISTIC_WRITE 락 + upsert)"
```

---

## Task 8: `NbrService` — 발급/미리보기 (TDD)

**Files:**
- Create: `src/main/java/com/project/wmsback/master/service/NbrService.java`
- Test: `src/test/java/com/project/wmsback/master/service/NbrServiceTest.java`

**Interfaces:**
- Consumes: `NbrRuleRepository`(Task 6), `NbrSeqRepository`(Task 7), `NbrPattern`(Task 3), `NbrRule`/`NbrSeq`/`DyncKyTyp`(Task 4/5)
- Produces:
  - `NbrService.issue(String ruleCd): String` — `NONE` 규칙 전용
  - `NbrService.issue(String ruleCd, LocalDate de): String` — `DATE` 규칙 전용
  - `NbrService.preview(String ptrn, DyncKyTyp dyncKyTyp): String` — DB 미접근

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.project.wmsback.master.service;

import com.project.wmsback.master.entity.DyncKyTyp;
import com.project.wmsback.master.entity.NbrRule;
import com.project.wmsback.master.entity.NbrSeq;
import com.project.wmsback.master.repository.NbrRuleRepository;
import com.project.wmsback.master.repository.NbrSeqRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NbrServiceTest {

    @Mock
    private NbrRuleRepository nbrRuleRepository;
    @Mock
    private NbrSeqRepository nbrSeqRepository;

    @InjectMocks
    private NbrService nbrService;

    @Test
    void 존재하지_않는_규칙이면_IllegalArgumentException() {
        when(nbrRuleRepository.findById("NO_SUCH")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> nbrService.issue("NO_SUCH"));
    }

    @Test
    void 비활성_규칙이면_IllegalStateException() {
        NbrRule rule = NbrRule.builder()
                .ruleCd("PROD_CD").ruleNm("상품 코드").ptrn("PROD-{SEQ:4}").dyncKyTyp(DyncKyTyp.NONE)
                .usYn("N")
                .build();
        when(nbrRuleRepository.findById("PROD_CD")).thenReturn(Optional.of(rule));

        assertThrows(IllegalStateException.class, () -> nbrService.issue("PROD_CD"));
    }

    @Test
    void NONE_규칙에_날짜_오버로드를_쓰면_IllegalStateException() {
        NbrRule rule = NbrRule.builder()
                .ruleCd("PROD_CD").ruleNm("상품 코드").ptrn("PROD-{SEQ:4}").dyncKyTyp(DyncKyTyp.NONE)
                .build();
        when(nbrRuleRepository.findById("PROD_CD")).thenReturn(Optional.of(rule));

        assertThrows(IllegalStateException.class,
                () -> nbrService.issue("PROD_CD", LocalDate.of(2026, 7, 29)));
    }

    @Test
    void DATE_규칙에_인자_없는_issue를_쓰면_IllegalStateException() {
        NbrRule rule = NbrRule.builder()
                .ruleCd("IB_NO").ruleNm("입고 번호").ptrn("IB-{yyyyMMdd}-{SEQ:3}").dyncKyTyp(DyncKyTyp.DATE)
                .build();
        when(nbrRuleRepository.findById("IB_NO")).thenReturn(Optional.of(rule));

        assertThrows(IllegalStateException.class, () -> nbrService.issue("IB_NO"));
    }

    @Test
    void NONE_규칙_기존_카운터가_있으면_그대로_증가시켜_렌더링() {
        NbrRule rule = NbrRule.builder()
                .ruleCd("PROD_CD").ruleNm("상품 코드").ptrn("PROD-{SEQ:4}").dyncKyTyp(DyncKyTyp.NONE)
                .build();
        NbrSeq seqRow = NbrSeq.builder().ruleCd("PROD_CD").dyncKy("-").seq(6L).build();
        when(nbrRuleRepository.findById("PROD_CD")).thenReturn(Optional.of(rule));
        when(nbrSeqRepository.findByIdForUpdate("PROD_CD", "-")).thenReturn(Optional.of(seqRow));

        String number = nbrService.issue("PROD_CD");

        assertEquals("PROD-0007", number);
        verify(nbrSeqRepository, never()).insertIfAbsent(anyString(), anyString());
    }

    @Test
    void 카운터가_없으면_생성_후_재조회해_증가시킨다() {
        NbrRule rule = NbrRule.builder()
                .ruleCd("PROD_CD").ruleNm("상품 코드").ptrn("PROD-{SEQ:4}").dyncKyTyp(DyncKyTyp.NONE)
                .build();
        NbrSeq createdRow = NbrSeq.builder().ruleCd("PROD_CD").dyncKy("-").seq(0L).build();
        when(nbrRuleRepository.findById("PROD_CD")).thenReturn(Optional.of(rule));
        when(nbrSeqRepository.findByIdForUpdate("PROD_CD", "-"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(createdRow));

        String number = nbrService.issue("PROD_CD");

        assertEquals("PROD-0001", number);
        verify(nbrSeqRepository).insertIfAbsent("PROD_CD", "-");
    }

    @Test
    void DATE_규칙은_전달받은_날짜를_동적키와_렌더링에_같이_쓴다() {
        NbrRule rule = NbrRule.builder()
                .ruleCd("IB_NO").ruleNm("입고 번호").ptrn("IB-{yyyyMMdd}-{SEQ:3}").dyncKyTyp(DyncKyTyp.DATE)
                .build();
        NbrSeq seqRow = NbrSeq.builder().ruleCd("IB_NO").dyncKy("20260825").seq(11L).build();
        when(nbrRuleRepository.findById("IB_NO")).thenReturn(Optional.of(rule));
        when(nbrSeqRepository.findByIdForUpdate("IB_NO", "20260825")).thenReturn(Optional.of(seqRow));

        String number = nbrService.issue("IB_NO", LocalDate.of(2026, 8, 25));

        assertEquals("IB-20260825-012", number);
    }

    @Test
    void preview는_DB를_건드리지_않고_seq_1로_렌더링() {
        String number = nbrService.preview("PROD-{SEQ:4}", DyncKyTyp.NONE);

        assertEquals("PROD-0001", number);
        verifyNoInteractions(nbrRuleRepository, nbrSeqRepository);
    }

    @Test
    void preview도_패턴_검증을_통과해야_한다() {
        assertThrows(IllegalArgumentException.class,
                () -> nbrService.preview("PROD-0001", DyncKyTyp.NONE));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./mvnw test -Dtest=NbrServiceTest`
Expected: FAIL (컴파일 에러 — `NbrService` 클래스가 없음)

- [ ] **Step 3: `NbrService` 구현**

```java
package com.project.wmsback.master.service;

import com.project.wmsback.master.entity.DyncKyTyp;
import com.project.wmsback.master.entity.NbrRule;
import com.project.wmsback.master.entity.NbrSeq;
import com.project.wmsback.master.repository.NbrRuleRepository;
import com.project.wmsback.master.repository.NbrSeqRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NbrService {

    private static final String NONE_DYNC_KY = "-";

    private final NbrRuleRepository nbrRuleRepository;
    private final NbrSeqRepository nbrSeqRepository;

    /** NONE 규칙 전용 발급 */
    @Transactional
    public String issue(String ruleCd) {
        NbrRule rule = findUsableRule(ruleCd);
        if (rule.getDyncKyTyp() != DyncKyTyp.NONE) {
            throw new IllegalStateException(
                    "DATE 규칙은 issue(ruleCd, LocalDate)를 써야 합니다: " + ruleCd);
        }
        return issueWithKey(rule, NONE_DYNC_KY, LocalDate.now());
    }

    /**
     * DATE 규칙 전용 발급. de가 동적키(리셋 단위)이자 패턴의 날짜 토큰 렌더링 기준이다.
     * 서버가 오늘 날짜로 강제하지 않는다 — 예정일·주문일처럼 호출자가 이미 들고 있는
     * 업무 일자를 그대로 쓴다 (신뢰된 서버 내부 호출이라 위변조 우려가 없다).
     */
    @Transactional
    public String issue(String ruleCd, LocalDate de) {
        NbrRule rule = findUsableRule(ruleCd);
        if (rule.getDyncKyTyp() != DyncKyTyp.DATE) {
            throw new IllegalStateException(
                    "NONE 규칙은 issue(ruleCd)를 써야 합니다: " + ruleCd);
        }
        String dyncKy = de.format(DateTimeFormatter.BASIC_ISO_DATE);
        return issueWithKey(rule, dyncKy, de);
    }

    /** DB 접근 없이 오늘 날짜 + seq=1로 렌더링만 — 규칙 저장 전 화면 미리보기용 */
    public String preview(String ptrn, DyncKyTyp dyncKyTyp) {
        NbrPattern.validate(ptrn, dyncKyTyp);
        return NbrPattern.render(ptrn, 1, LocalDate.now());
    }

    private NbrRule findUsableRule(String ruleCd) {
        NbrRule rule = nbrRuleRepository.findById(ruleCd)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채번 규칙입니다: " + ruleCd));
        if (!rule.isUsable()) {
            throw new IllegalStateException("비활성화된 채번 규칙입니다: " + ruleCd);
        }
        return rule;
    }

    private String issueWithKey(NbrRule rule, String dyncKy, LocalDate de) {
        NbrSeq row = nbrSeqRepository.findByIdForUpdate(rule.getRuleCd(), dyncKy)
                .orElseGet(() -> {
                    nbrSeqRepository.insertIfAbsent(rule.getRuleCd(), dyncKy);
                    return nbrSeqRepository.findByIdForUpdate(rule.getRuleCd(), dyncKy)
                            .orElseThrow(() -> new IllegalStateException(
                                    "채번 카운터 초기화에 실패했습니다: " + rule.getRuleCd()));
                });
        row.increment();
        return NbrPattern.render(rule.getPtrn(), row.getSeq(), de);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./mvnw test -Dtest=NbrServiceTest`
Expected: PASS (10개 전부)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/project/wmsback/master/service/NbrService.java \
        src/test/java/com/project/wmsback/master/service/NbrServiceTest.java
git commit -m "feat: NbrService 추가 (발급/미리보기)"
```

---

## Task 9: `NbrRuleService` — CRUD (TDD)

**Files:**
- Create: `src/main/java/com/project/wmsback/master/dto/NbrRuleResponse.java`
- Create: `src/main/java/com/project/wmsback/master/dto/NbrRuleSaveRequest.java`
- Create: `src/main/java/com/project/wmsback/master/dto/NbrSeqResponse.java`
- Create: `src/main/java/com/project/wmsback/master/service/NbrRuleService.java`
- Test: `src/test/java/com/project/wmsback/master/service/NbrRuleServiceTest.java`

**Interfaces:**
- Consumes: `NbrRuleRepository`(Task 6), `NbrSeqRepository`(Task 7), `NbrPattern`(Task 3), `NbrRule`/`NbrSeq`/`DyncKyTyp`
- Produces:
  - `NbrRuleService.list(NbrRuleSearchCond): List<NbrRuleResponse>`
  - `NbrRuleService.saveAll(List<NbrRuleSaveRequest>): void`
  - `NbrRuleService.seqs(String ruleCd): List<NbrSeqResponse>`

- [ ] **Step 1: 응답/요청 DTO**

```java
package com.project.wmsback.master.dto;

import com.project.wmsback.master.entity.DyncKyTyp;
import com.project.wmsback.master.entity.NbrRule;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class NbrRuleResponse {

    private final String ruleCd;
    private final String ruleNm;
    private final String ptrn;
    private final DyncKyTyp dyncKyTyp;
    private final String usYn;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final String updatedBy;
    private final LocalDateTime updatedAt;

    private NbrRuleResponse(NbrRule rule) {
        this.ruleCd = rule.getRuleCd();
        this.ruleNm = rule.getRuleNm();
        this.ptrn = rule.getPtrn();
        this.dyncKyTyp = rule.getDyncKyTyp();
        this.usYn = rule.getUsYn();
        this.createdBy = rule.getCreatedBy();
        this.createdAt = rule.getCreatedAt();
        this.updatedBy = rule.getUpdatedBy();
        this.updatedAt = rule.getUpdatedAt();
    }

    public static NbrRuleResponse from(NbrRule rule) {
        return new NbrRuleResponse(rule);
    }
}
```

```java
package com.project.wmsback.master.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.wmsback.master.entity.DyncKyTyp;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * dyncKyTyp은 신규 등록 시에만 쓰인다 — 수정 행에 값이 와도 서비스가 무시하지 않고
 * 기존 값과 다르면 거부한다(등록 후 변경 불가).
 */
@Getter
@Setter
@NoArgsConstructor
public class NbrRuleSaveRequest {

    @JsonProperty("_status")
    private String status;

    private String ruleCd;
    private String ruleNm;
    private String ptrn;
    private DyncKyTyp dyncKyTyp;
    private String usYn;
}
```

```java
package com.project.wmsback.master.dto;

import com.project.wmsback.master.entity.NbrSeq;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class NbrSeqResponse {

    private final String ruleCd;
    private final String dyncKy;
    private final Long seq;
    private final LocalDateTime updatedAt;

    private NbrSeqResponse(NbrSeq row) {
        this.ruleCd = row.getRuleCd();
        this.dyncKy = row.getDyncKy();
        this.seq = row.getSeq();
        this.updatedAt = row.getUpdatedAt();
    }

    public static NbrSeqResponse from(NbrSeq row) {
        return new NbrSeqResponse(row);
    }
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

```java
package com.project.wmsback.master.service;

import com.project.wmsback.master.dto.NbrRuleSaveRequest;
import com.project.wmsback.master.entity.DyncKyTyp;
import com.project.wmsback.master.entity.NbrRule;
import com.project.wmsback.master.repository.NbrRuleRepository;
import com.project.wmsback.master.repository.NbrSeqRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NbrRuleServiceTest {

    @Mock
    private NbrRuleRepository nbrRuleRepository;
    @Mock
    private NbrSeqRepository nbrSeqRepository;

    @InjectMocks
    private NbrRuleService nbrRuleService;

    private NbrRuleSaveRequest createRow(String status, String ruleCd, String ptrn, DyncKyTyp typ) {
        NbrRuleSaveRequest row = new NbrRuleSaveRequest();
        row.setStatus(status);
        row.setRuleCd(ruleCd);
        row.setRuleNm("테스트 규칙");
        row.setPtrn(ptrn);
        row.setDyncKyTyp(typ);
        row.setUsYn("Y");
        return row;
    }

    @Test
    void 신규_등록시_이미_있는_ruleCd면_예외() {
        when(nbrRuleRepository.existsById("PROD_CD")).thenReturn(true);
        NbrRuleSaveRequest row = createRow("C", "PROD_CD", "PROD-{SEQ:4}", DyncKyTyp.NONE);

        assertThrows(IllegalArgumentException.class, () -> nbrRuleService.saveAll(List.of(row)));
        verify(nbrRuleRepository, never()).save(any());
    }

    @Test
    void 신규_등록시_패턴이_유효하지_않으면_예외() {
        when(nbrRuleRepository.existsById("PROD_CD")).thenReturn(false);
        NbrRuleSaveRequest row = createRow("C", "PROD_CD", "PROD-0001", DyncKyTyp.NONE);

        assertThrows(IllegalArgumentException.class, () -> nbrRuleService.saveAll(List.of(row)));
        verify(nbrRuleRepository, never()).save(any());
    }

    @Test
    void 신규_등록_정상() {
        when(nbrRuleRepository.existsById("PROD_CD")).thenReturn(false);
        NbrRuleSaveRequest row = createRow("C", "PROD_CD", "PROD-{SEQ:4}", DyncKyTyp.NONE);

        nbrRuleService.saveAll(List.of(row));

        verify(nbrRuleRepository).save(any(NbrRule.class));
        verify(nbrRuleRepository).flush();
    }

    @Test
    void 수정시_dyncKyTyp을_바꾸려_하면_예외() {
        NbrRule existing = NbrRule.builder()
                .ruleCd("IB_NO").ruleNm("입고 번호").ptrn("IB-{yyyyMMdd}-{SEQ:3}").dyncKyTyp(DyncKyTyp.DATE)
                .build();
        when(nbrRuleRepository.findById("IB_NO")).thenReturn(Optional.of(existing));
        NbrRuleSaveRequest row = createRow("U", "IB_NO", "IB-{yyyyMMdd}-{SEQ:3}", DyncKyTyp.NONE);

        assertThrows(IllegalArgumentException.class, () -> nbrRuleService.saveAll(List.of(row)));
    }

    @Test
    void 수정_정상() {
        NbrRule existing = NbrRule.builder()
                .ruleCd("IB_NO").ruleNm("입고 번호").ptrn("IB-{yyyyMMdd}-{SEQ:3}").dyncKyTyp(DyncKyTyp.DATE)
                .build();
        when(nbrRuleRepository.findById("IB_NO")).thenReturn(Optional.of(existing));
        NbrRuleSaveRequest row = createRow("U", "IB_NO", "IB-{yyyyMMdd}-{SEQ:4}", DyncKyTyp.DATE);

        nbrRuleService.saveAll(List.of(row));

        assertEquals("IB-{yyyyMMdd}-{SEQ:4}", existing.getPtrn());
    }

    @Test
    void 알수없는_status면_예외() {
        NbrRuleSaveRequest row = createRow("X", "PROD_CD", "PROD-{SEQ:4}", DyncKyTyp.NONE);

        assertThrows(IllegalArgumentException.class, () -> nbrRuleService.saveAll(List.of(row)));
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./mvnw test -Dtest=NbrRuleServiceTest`
Expected: FAIL (컴파일 에러 — `NbrRuleService` 클래스가 없음)

- [ ] **Step 4: `NbrRuleService` 구현**

```java
package com.project.wmsback.master.service;

import com.project.wmsback.master.dto.NbrRuleResponse;
import com.project.wmsback.master.dto.NbrRuleSaveRequest;
import com.project.wmsback.master.dto.NbrRuleSearchCond;
import com.project.wmsback.master.dto.NbrSeqResponse;
import com.project.wmsback.master.entity.NbrRule;
import com.project.wmsback.master.repository.NbrRuleRepository;
import com.project.wmsback.master.repository.NbrSeqRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NbrRuleService {

    private final NbrRuleRepository nbrRuleRepository;
    private final NbrSeqRepository nbrSeqRepository;

    public List<NbrRuleResponse> list(NbrRuleSearchCond cond) {
        return nbrRuleRepository.search(cond).stream()
                .map(NbrRuleResponse::from)
                .toList();
    }

    public List<NbrSeqResponse> seqs(String ruleCd) {
        if (!nbrRuleRepository.existsById(ruleCd)) {
            throw new IllegalArgumentException("존재하지 않는 채번 규칙입니다: " + ruleCd);
        }
        return nbrSeqRepository.findByRuleCdOrderByDyncKy(ruleCd).stream()
                .map(NbrSeqResponse::from)
                .toList();
    }

    /** 신규(C)/수정(U)/삭제(D) 행 일괄 저장. 한 건이라도 실패하면 전체 롤백. */
    @Transactional
    public void saveAll(List<NbrRuleSaveRequest> rows) {
        for (NbrRuleSaveRequest row : rows) {
            switch (row.getStatus()) {
                case "C" -> create(row);
                case "U" -> update(row);
                case "D" -> delete(row);
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        nbrRuleRepository.flush();
    }

    private void create(NbrRuleSaveRequest row) {
        if (row.getRuleCd() == null || row.getRuleCd().isBlank()) {
            throw new IllegalArgumentException("규칙 코드는 필수입니다.");
        }
        if (row.getRuleNm() == null || row.getRuleNm().isBlank()) {
            throw new IllegalArgumentException("규칙명은 필수입니다: " + row.getRuleCd());
        }
        if (row.getDyncKyTyp() == null) {
            throw new IllegalArgumentException("동적키유형은 필수입니다: " + row.getRuleCd());
        }
        if (nbrRuleRepository.existsById(row.getRuleCd())) {
            throw new IllegalArgumentException("이미 존재하는 채번 규칙 코드입니다: " + row.getRuleCd());
        }
        NbrPattern.validate(row.getPtrn(), row.getDyncKyTyp());
        // rule_cd가 비생성(assigned) PK라 save()가 내부적으로 merge()를 타 SELECT가 한 번 더 나간다.
        // 관리 화면에서 사람이 저장하는 빈도라 무시 가능한 비용이다.
        nbrRuleRepository.save(NbrRule.builder()
                .ruleCd(row.getRuleCd())
                .ruleNm(row.getRuleNm())
                .ptrn(row.getPtrn())
                .dyncKyTyp(row.getDyncKyTyp())
                .usYn(row.getUsYn())
                .build());
    }

    private void update(NbrRuleSaveRequest row) {
        NbrRule rule = nbrRuleRepository.findById(row.getRuleCd())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채번 규칙입니다: " + row.getRuleCd()));
        if (row.getDyncKyTyp() != null && row.getDyncKyTyp() != rule.getDyncKyTyp()) {
            throw new IllegalArgumentException(
                    "동적키유형은 변경할 수 없습니다. 새 규칙으로 등록하세요: " + row.getRuleCd());
        }
        if (row.getRuleNm() == null || row.getRuleNm().isBlank()) {
            throw new IllegalArgumentException("규칙명은 필수입니다: " + row.getRuleCd());
        }
        NbrPattern.validate(row.getPtrn(), rule.getDyncKyTyp());
        rule.update(row.getRuleNm(), row.getPtrn(), row.getUsYn());
    }

    private void delete(NbrRuleSaveRequest row) {
        NbrRule rule = nbrRuleRepository.findById(row.getRuleCd())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채번 규칙입니다: " + row.getRuleCd()));
        nbrRuleRepository.delete(rule);
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./mvnw test -Dtest=NbrRuleServiceTest`
Expected: PASS (6개 전부)

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/project/wmsback/master/dto/NbrRuleResponse.java \
        src/main/java/com/project/wmsback/master/dto/NbrRuleSaveRequest.java \
        src/main/java/com/project/wmsback/master/dto/NbrSeqResponse.java \
        src/main/java/com/project/wmsback/master/service/NbrRuleService.java \
        src/test/java/com/project/wmsback/master/service/NbrRuleServiceTest.java
git commit -m "feat: NbrRuleService 추가 (그리드 CRUD)"
```

---

## Task 10: `NbrRuleController`

**Files:**
- Create: `src/main/java/com/project/wmsback/master/dto/NbrIssueResponse.java`
- Create: `src/main/java/com/project/wmsback/master/dto/NbrPreviewRequest.java`
- Create: `src/main/java/com/project/wmsback/master/dto/NbrPreviewResponse.java`
- Create: `src/main/java/com/project/wmsback/master/controller/NbrRuleController.java`

**Interfaces:**
- Consumes: `NbrRuleService`(Task 9), `NbrService`(Task 8)
- Produces: HTTP 엔드포인트 (아래 Step 2 표)

이 저장소에는 컨트롤러 테스트가 어디에도 없다(다른 마스터 컨트롤러도 전부 테스트 없이 얇은 위임만) — 이 태스크도 자동화 테스트 없이 작성하고, Task 16에서 실제 기동 후 수동 검증한다.

- [ ] **Step 1: 나머지 DTO**

```java
package com.project.wmsback.master.dto;

import lombok.Getter;

@Getter
public class NbrIssueResponse {

    private final String number;

    public NbrIssueResponse(String number) {
        this.number = number;
    }
}
```

```java
package com.project.wmsback.master.dto;

import com.project.wmsback.master.entity.DyncKyTyp;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NbrPreviewRequest {

    private String ptrn;
    private DyncKyTyp dyncKyTyp;
}
```

```java
package com.project.wmsback.master.dto;

import lombok.Getter;

@Getter
public class NbrPreviewResponse {

    private final String number;

    public NbrPreviewResponse(String number) {
        this.number = number;
    }
}
```

- [ ] **Step 2: `NbrRuleController`**

```java
package com.project.wmsback.master.controller;

import com.project.wmsback.master.dto.NbrIssueResponse;
import com.project.wmsback.master.dto.NbrPreviewRequest;
import com.project.wmsback.master.dto.NbrPreviewResponse;
import com.project.wmsback.master.dto.NbrRuleResponse;
import com.project.wmsback.master.dto.NbrRuleSaveRequest;
import com.project.wmsback.master.dto.NbrRuleSearchCond;
import com.project.wmsback.master.dto.NbrSeqResponse;
import com.project.wmsback.master.service.NbrRuleService;
import com.project.wmsback.master.service.NbrService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/master/nbr-rules")
@RequiredArgsConstructor
public class NbrRuleController {

    private final NbrRuleService nbrRuleService;
    private final NbrService nbrService;

    @GetMapping
    public List<NbrRuleResponse> list(@ModelAttribute NbrRuleSearchCond cond) {
        return nbrRuleService.list(cond);
    }

    @PostMapping("/bulk")
    public void saveAll(@RequestBody List<NbrRuleSaveRequest> rows) {
        nbrRuleService.saveAll(rows);
    }

    @GetMapping("/{ruleCd}/seqs")
    public List<NbrSeqResponse> seqs(@PathVariable String ruleCd) {
        return nbrRuleService.seqs(ruleCd);
    }

    /** 테스트/외부 호출용. 항상 오늘 날짜 기준 — 내부 Java 호출과 달리 클라이언트가 날짜를 넘길 수 없다 */
    @PostMapping("/{ruleCd}/issue")
    public NbrIssueResponse issue(@PathVariable String ruleCd) {
        return new NbrIssueResponse(nbrService.issue(ruleCd));
    }

    @PostMapping("/preview")
    public NbrPreviewResponse preview(@RequestBody NbrPreviewRequest req) {
        return new NbrPreviewResponse(nbrService.preview(req.getPtrn(), req.getDyncKyTyp()));
    }
}
```

`issue()`가 `NbrService.issue(String)`(NONE 전용)만 호출한다는 점에 주의 — REST로는 `DATE` 규칙을 발급할 수 없다. `DATE` 규칙(`OMS_IB_NO`/`IB_NO`/`OUTB_NO`/`OUTB_WAV_NO`)은 호출자가 날짜를 들고 있는 서버 내부 Java 호출로만 발급되고, 이 엔드포인트는 `NONE` 규칙 테스트나 향후 확장을 위한 보조 창구다. `DATE` 규칙에 이 엔드포인트를 호출하면 `NbrService.issue(String)`이 `IllegalStateException`(409)을 던진다 — 의도된 동작이다.

- [ ] **Step 3: 컴파일 확인**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/project/wmsback/master/dto/NbrIssueResponse.java \
        src/main/java/com/project/wmsback/master/dto/NbrPreviewRequest.java \
        src/main/java/com/project/wmsback/master/dto/NbrPreviewResponse.java \
        src/main/java/com/project/wmsback/master/controller/NbrRuleController.java
git commit -m "feat: NbrRuleController 추가"
```

---

## Task 11: `docs/screen-list.html`에 채번규칙 관리 화면 추가

**Files:**
- Modify: `docs/screen-list.html`

**Interfaces:** 없음 (문서 변경만)

- [ ] **Step 1: "5. 기준정보 (Master) — 부가" 표에 행 추가**

`공통코드 관리` 행(`</tr>`로 끝나는 블록) 바로 다음, `</tbody>` 앞에 삽입한다:

```html
<tr>
  <td class="name">채번규칙 관리</td>
  <td>규칙코드/규칙명/패턴/동적키유형/사용여부 편집, 패턴 미리보기, 선택 규칙의 현재 카운터 읽기전용 조회</td>
  <td class="st"><span class="badge hold">보류</span></td>
  <td>API만 존재(<code>/master/nbr-rules</code>, <code>/bulk</code>, <code>/{ruleCd}/seqs</code>, <code>/{ruleCd}/issue</code>, <code>/preview</code>). 그리드 화면은 별도 프론트 레포 — <strong>사이드바 미등록</strong>. 등록 후 동적키유형 변경 불가</td>
</tr>
```

- [ ] **Step 2: 확인**

브라우저 또는 `grep -n "채번규칙" docs/screen-list.html`로 행이 들어갔는지 확인.

- [ ] **Step 3: 커밋**

```bash
git add docs/screen-list.html
git commit -m "docs: screen-list에 채번규칙 관리 화면 추가"
```

---

## Task 12: 마이그레이션 SQL 작성 (`docs/migration-nbr.sql`)

**Files:**
- Create: `docs/migration-nbr.sql`

**Interfaces:** 없음 (DB 상태 변경 — DBeaver로 수동 실행)

`docs/migration-zon.sql`과 같은 스타일(`DO $tag$` 단일 블록, 존재 확인, `RAISE NOTICE`)로 작성한다.

- [ ] **Step 1: 스크립트 작성**

```sql
-- =====================================================================
-- 채번관리(nbr_rule/nbr_seq) 도입 — 기존 6개 시퀀스 채번을 이관한다.
--   전제: docs/migration-catchup-to-schema.sql 까지 적용된 DB.
--
--   대상: prod_cd_seq, vndr_cd_seq, oms_ib_no_seq, ib_no_seq, outb_no_seq, outb_wave_no_seq
--   (lot_no는 상품+입고일자 복합 리셋이라 이번 이관 대상이 아니다 — 지금 방식 유지)
--
--   ▣ 전체가 DO 블록 하나다 (BEGIN;/COMMIT; 을 쓰지 않는다) — CLAUDE.md 규칙.
--     전 구간에 존재 확인이 걸려 있어 몇 번을 돌려도 안전하다.
--
--   FK는 걸지 않는다. nbr_seq 시딩은 옛 시퀀스의 내부 상태(last_value)를 믿지 않고
--   실제 발급된 업무 데이터(prod_cd/vndr_cd/oms_ib_no/ib_no/outb_no/wav_no)에서
--   직접 최대값을 뽑는다 — 시퀀스의 is_called 여부 같은 곁가지를 신경 쓸 필요가 없다.
--
--   날짜가 들어가는 4개(OMS_IB_NO/IB_NO/OUTB_NO/OUTB_WAV_NO)는 "오늘 발급분"만이 아니라
--   기존 번호에 실제로 박혀 있는 날짜마다 각각 시딩한다 — oms_ib_no/ib_no는 예정일,
--   outb_no는 주문일 기준이라 아직 안 끝난 미래예정 주문이나 소급 등록 건이 여러 날짜에
--   걸쳐 있을 수 있다. wav_no는 항상 생성 당일이지만 특례를 두지 않고 같은 방식으로 훑는다.
--
--   실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--     - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--     - 이 스크립트를 적용하는 배포에는 애플리케이션 코드 변경(6개 호출부를
--       nbrService.issue(...)로 교체, 옛 시퀀스 nextval 리포지토리 메서드 제거)도
--       같이 나가야 한다 — 옛 코드가 남은 채로 이 스크립트만 먼저 적용하면
--       DROP SEQUENCE 이후 옛 코드의 nextval 호출이 즉시 실패한다.
-- =====================================================================

DO $nbr$
BEGIN
    -- 1. 테이블 -------------------------------------------------------
    IF to_regclass('nbr_rule') IS NULL THEN
        CREATE TABLE nbr_rule (
            rule_cd     VARCHAR(30)     NOT NULL,
            rule_nm     VARCHAR(100)    NOT NULL,
            ptrn        VARCHAR(200)    NOT NULL,
            dync_ky_typ VARCHAR(10)     NOT NULL,
            us_yn       CHAR(1)         DEFAULT 'Y' NOT NULL,
            created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
            created_by  VARCHAR(30)     DEFAULT 'admin' NOT NULL,
            updated_at  TIMESTAMP,
            updated_by  VARCHAR(30),
            CONSTRAINT pk_nbr_rule PRIMARY KEY (rule_cd),
            CONSTRAINT ck_nbr_rule_dync_ky_typ CHECK (dync_ky_typ IN ('NONE', 'DATE')),
            CONSTRAINT ck_nbr_rule_us_yn CHECK (us_yn IN ('Y', 'N'))
        );
        COMMENT ON TABLE  nbr_rule IS '채번 규칙. 패턴 문자열 하나로 형식을 정의';
        COMMENT ON COLUMN nbr_rule.rule_cd     IS '채번 규칙 코드 (업무 식별자, 예: PROD_CD)';
        COMMENT ON COLUMN nbr_rule.ptrn        IS '채번 패턴. {SEQ:n} 정확히 1개 + 날짜 토큰({yyyyMMdd} 등)';
        COMMENT ON COLUMN nbr_rule.dync_ky_typ IS 'NONE=카운터 전역 공유 / DATE=호출자가 넘긴 날짜 기준 분리';
        COMMENT ON COLUMN nbr_rule.us_yn       IS '사용 여부. N이면 발급 요청 거부';
        RAISE NOTICE 'nbr_rule 테이블 생성';
    ELSE
        RAISE NOTICE 'nbr_rule 테이블 이미 존재 — 건너뜀';
    END IF;

    IF to_regclass('nbr_seq') IS NULL THEN
        CREATE TABLE nbr_seq (
            rule_cd     VARCHAR(30)     NOT NULL,
            dync_ky     VARCHAR(30)     NOT NULL,
            seq         BIGINT          DEFAULT 0 NOT NULL,
            created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
            created_by  VARCHAR(30)     DEFAULT 'admin' NOT NULL,
            updated_at  TIMESTAMP,
            updated_by  VARCHAR(30),
            CONSTRAINT pk_nbr_seq PRIMARY KEY (rule_cd, dync_ky)
        );
        COMMENT ON TABLE  nbr_seq IS '채번 카운터. rule_cd+dync_ky별 현재 발급값';
        COMMENT ON COLUMN nbr_seq.dync_ky IS 'NONE이면 고정값 "-", DATE면 yyyyMMdd';
        COMMENT ON COLUMN nbr_seq.seq     IS '현재 발급값. updated_at이 곧 최종 발급 시각';
        RAISE NOTICE 'nbr_seq 테이블 생성';
    ELSE
        RAISE NOTICE 'nbr_seq 테이블 이미 존재 — 건너뜀';
    END IF;

    -- 2. 규칙 6건 시드 --------------------------------------------------
    INSERT INTO nbr_rule (rule_cd, rule_nm, ptrn, dync_ky_typ) VALUES
        ('PROD_CD',     '상품 코드',        'PROD-{SEQ:4}',           'NONE'),
        ('VNDR_CD',     '벤더 코드',        'VD-{SEQ:4}',             'NONE'),
        ('OMS_IB_NO',   '입고주문 번호',    'PO-{yyyyMMdd}-{SEQ:3}',  'DATE'),
        ('IB_NO',       '입고 번호',        'IB-{yyyyMMdd}-{SEQ:3}',  'DATE'),
        ('OUTB_NO',     '출고 번호',        'OB-{yyyyMMdd}-{SEQ:3}',  'DATE'),
        ('OUTB_WAV_NO', '출고 웨이브 번호', 'WV-{yyyyMMdd}-{SEQ:3}',  'DATE')
    ON CONFLICT (rule_cd) DO NOTHING;
    RAISE NOTICE '채번 규칙 6건 반영';

    -- 3. 카운터 시딩 (번호가 끊기지 않도록 실제 발급 데이터에서 최대값을 뽑는다) ----
    INSERT INTO nbr_seq (rule_cd, dync_ky, seq)
    SELECT 'PROD_CD', '-', COALESCE(MAX(split_part(prod_cd, '-', 2)::bigint), 0) FROM prod
    ON CONFLICT (rule_cd, dync_ky) DO NOTHING;

    INSERT INTO nbr_seq (rule_cd, dync_ky, seq)
    SELECT 'VNDR_CD', '-', COALESCE(MAX(split_part(vndr_cd, '-', 2)::bigint), 0) FROM vendor
    ON CONFLICT (rule_cd, dync_ky) DO NOTHING;

    INSERT INTO nbr_seq (rule_cd, dync_ky, seq)
    SELECT 'OMS_IB_NO', split_part(oms_ib_no, '-', 2), MAX(split_part(oms_ib_no, '-', 3)::bigint)
      FROM oms_ib_order GROUP BY split_part(oms_ib_no, '-', 2)
    ON CONFLICT (rule_cd, dync_ky) DO NOTHING;

    INSERT INTO nbr_seq (rule_cd, dync_ky, seq)
    SELECT 'IB_NO', split_part(ib_no, '-', 2), MAX(split_part(ib_no, '-', 3)::bigint)
      FROM ib_order GROUP BY split_part(ib_no, '-', 2)
    ON CONFLICT (rule_cd, dync_ky) DO NOTHING;

    INSERT INTO nbr_seq (rule_cd, dync_ky, seq)
    SELECT 'OUTB_NO', split_part(outb_no, '-', 2), MAX(split_part(outb_no, '-', 3)::bigint)
      FROM outb_order GROUP BY split_part(outb_no, '-', 2)
    ON CONFLICT (rule_cd, dync_ky) DO NOTHING;

    INSERT INTO nbr_seq (rule_cd, dync_ky, seq)
    SELECT 'OUTB_WAV_NO', split_part(wav_no, '-', 2), MAX(split_part(wav_no, '-', 3)::bigint)
      FROM outb_wave GROUP BY split_part(wav_no, '-', 2)
    ON CONFLICT (rule_cd, dync_ky) DO NOTHING;

    RAISE NOTICE '채번 카운터 시딩 완료';

    -- 4. 옛 시퀀스 제거 ---------------------------------------------------
    DROP SEQUENCE IF EXISTS prod_cd_seq;
    DROP SEQUENCE IF EXISTS vndr_cd_seq;
    DROP SEQUENCE IF EXISTS oms_ib_no_seq;
    DROP SEQUENCE IF EXISTS ib_no_seq;
    DROP SEQUENCE IF EXISTS outb_no_seq;
    DROP SEQUENCE IF EXISTS outb_wave_no_seq;
    RAISE NOTICE '옛 채번 시퀀스 6개 제거 완료';

    RAISE NOTICE '채번관리 마이그레이션 완료';
END
$nbr$;

-- =====================================================================
-- 적용 후 확인
--   1) 규칙 6건
--      SELECT * FROM nbr_rule ORDER BY rule_cd;
--   2) 카운터가 실제 최대 발급값과 일치하는지 (예: IB_NO)
--      SELECT dync_ky, seq FROM nbr_seq WHERE rule_cd = 'IB_NO' ORDER BY dync_ky;
--      SELECT split_part(ib_no,'-',2) AS de, MAX(split_part(ib_no,'-',3)::bigint) AS mx
--        FROM ib_order GROUP BY 1 ORDER BY 1;
--      -- 두 결과의 dync_ky/de별 seq/mx가 같아야 한다.
--   3) 옛 시퀀스 0건
--      SELECT sequencename FROM pg_sequences
--       WHERE sequencename IN ('prod_cd_seq','vndr_cd_seq','oms_ib_no_seq',
--                               'ib_no_seq','outb_no_seq','outb_wave_no_seq');
-- =====================================================================
```

- [ ] **Step 2: 커밋**

```bash
git add docs/migration-nbr.sql
git commit -m "docs: 채번관리 마이그레이션 스크립트 추가"
```

(이 스크립트는 Task 16에서 실제 DB에 적용한다 — 애플리케이션 코드가 아직 옛 시퀀스를 참조하는 동안에는 실행하지 않는다.)

---

## Task 13: `ProdService`/`VendorService` 전환 (NONE 규칙)

**Files:**
- Modify: `src/main/java/com/project/wmsback/master/repository/ProdRepository.java`
- Modify: `src/main/java/com/project/wmsback/master/repository/VendorRepository.java`
- Modify: `src/main/java/com/project/wmsback/master/service/ProdService.java`
- Modify: `src/main/java/com/project/wmsback/master/service/VendorService.java`

**Interfaces:**
- Consumes: `NbrService.issue(String ruleCd)`(Task 8)

- [ ] **Step 1: `ProdRepository`에서 `nextProdCdSeq` 제거**

`src/main/java/com/project/wmsback/master/repository/ProdRepository.java`를 다음으로 교체(전체 파일):

```java
package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.Prod;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdRepository extends JpaRepository<Prod, Long>, ProdRepositoryCustom {
}
```

- [ ] **Step 2: `VendorRepository`에서 `nextVndrCdSeq` 제거**

`src/main/java/com/project/wmsback/master/repository/VendorRepository.java`를 다음으로 교체(전체 파일):

```java
package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<Vendor, Long>, VendorRepositoryCustom {
}
```

- [ ] **Step 3: `ProdService.create()` 전환**

`src/main/java/com/project/wmsback/master/service/ProdService.java`에서:

```java
    private void create(ProdSaveRequest row) {
        // 클라이언트가 보낸 코드는 받지 않는다 — 시퀀스로 채번 (PROD-0001 형식)
        String prodCd = String.format("PROD-%04d", prodRepository.nextProdCdSeq());
        prodRepository.save(Prod.builder()
```

를

```java
    private void create(ProdSaveRequest row) {
        // 클라이언트가 보낸 코드는 받지 않는다 — 채번 규칙 PROD_CD로 발급 (PROD-0001 형식)
        String prodCd = nbrService.issue("PROD_CD");
        prodRepository.save(Prod.builder()
```

로 바꾸고, 필드 선언부에 `NbrService` 의존성을 추가한다:

```java
    private final ProdRepository prodRepository;
```

를

```java
    private final ProdRepository prodRepository;
    private final NbrService nbrService;
```

로, import에 `com.project.wmsback.master.service.NbrService`를 추가(같은 패키지이므로 실제로는 import 불필요 — `ProdService`와 `NbrService`가 둘 다 `com.project.wmsback.master.service` 패키지다).

- [ ] **Step 4: `VendorService.create()` 전환**

`src/main/java/com/project/wmsback/master/service/VendorService.java`에서 동일하게:

```java
    private void create(VendorSaveRequest row) {
        // 클라이언트가 보낸 코드는 받지 않는다 — 시퀀스로 채번 (VD-0001 형식)
        String vndrCd = String.format("VD-%04d", vendorRepository.nextVndrCdSeq());
        vendorRepository.save(Vendor.builder()
```

를

```java
    private void create(VendorSaveRequest row) {
        // 클라이언트가 보낸 코드는 받지 않는다 — 채번 규칙 VNDR_CD로 발급 (VD-0001 형식)
        String vndrCd = nbrService.issue("VNDR_CD");
        vendorRepository.save(Vendor.builder()
```

로 바꾸고, 필드 선언부에 `private final NbrService nbrService;`를 추가한다.

- [ ] **Step 5: 컴파일 확인**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/project/wmsback/master/repository/ProdRepository.java \
        src/main/java/com/project/wmsback/master/repository/VendorRepository.java \
        src/main/java/com/project/wmsback/master/service/ProdService.java \
        src/main/java/com/project/wmsback/master/service/VendorService.java
git commit -m "refactor: 상품/벤더 코드 채번을 nbr 모듈로 전환"
```

---

## Task 14: `OutbOrderService`/`OutbWaveService` 전환 (DATE 규칙)

**Files:**
- Modify: `src/main/java/com/project/wmsback/outbound/repository/OutbOrderRepository.java`
- Modify: `src/main/java/com/project/wmsback/outbound/repository/OutbWaveRepository.java`
- Modify: `src/main/java/com/project/wmsback/outbound/service/OutbOrderService.java`
- Modify: `src/main/java/com/project/wmsback/outbound/service/OutbWaveService.java`

**Interfaces:**
- Consumes: `NbrService.issue(String ruleCd, LocalDate de)`(Task 8)

- [ ] **Step 1: `OutbOrderRepository`에서 `nextOutbNoSeq` 제거**

```java
package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.entity.OutbOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutbOrderRepository extends JpaRepository<OutbOrder, Long>, OutbOrderRepositoryCustom {

    /** 웨이브 해체 시 소속 주문 일괄 조회 */
    List<OutbOrder> findByWaveId(Long wavId);
}
```

- [ ] **Step 2: `OutbWaveRepository`에서 `nextWaveNoSeq` 제거**

```java
package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.entity.OutbWave;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutbWaveRepository extends JpaRepository<OutbWave, Long>, OutbWaveRepositoryCustom {
}
```

- [ ] **Step 3: `OutbOrderService.create()` 전환**

```java
        String outbNo = String.format("OB-%s-%03d",
                req.getOdrDe().format(DateTimeFormatter.BASIC_ISO_DATE),
                outbOrderRepository.nextOutbNoSeq());
```

를

```java
        String outbNo = nbrService.issue("OUTB_NO", req.getOdrDe());
```

로 바꾸고, `import java.time.format.DateTimeFormatter;`가 이제 이 파일에서 안 쓰이면 제거한다(다른 곳에서 쓰지 않는지 파일 전체를 확인). 필드 선언에 `private final NbrService nbrService;`를 추가하고 import(`com.project.wmsback.master.service.NbrService`)를 넣는다(이 서비스는 `wmsback.outbound` 패키지라 타 패키지 import가 필요하다).

- [ ] **Step 4: `OutbWaveService.create()` 전환**

```java
        String wavNo = String.format("WV-%s-%03d",
                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
                outbWaveRepository.nextWaveNoSeq());
```

를

```java
        String wavNo = nbrService.issue("OUTB_WAV_NO", LocalDate.now());
```

로 바꾼다. `LocalDate`/`DateTimeFormatter` import는 `LocalDate.now()` 호출이 남아 있으니 `LocalDate`는 유지, `DateTimeFormatter`는 이 파일에서 더 안 쓰면 제거한다. 필드 선언에 `private final NbrService nbrService;`와 import를 추가한다.

- [ ] **Step 5: 컴파일 확인**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/project/wmsback/outbound/repository/OutbOrderRepository.java \
        src/main/java/com/project/wmsback/outbound/repository/OutbWaveRepository.java \
        src/main/java/com/project/wmsback/outbound/service/OutbOrderService.java \
        src/main/java/com/project/wmsback/outbound/service/OutbWaveService.java
git commit -m "refactor: 출고/웨이브 번호 채번을 nbr 모듈로 전환"
```

---

## Task 15: `OmsIbOrderService` 전환 (DATE 규칙, omsback)

**Files:**
- Modify: `src/main/java/com/project/omsback/inbound/repository/OmsIbOrderRepository.java`
- Modify: `src/main/java/com/project/wmsback/inbound/repository/IbOrderRepository.java`
- Modify: `src/main/java/com/project/omsback/inbound/service/OmsIbOrderService.java`

**Interfaces:**
- Consumes: `NbrService.issue(String ruleCd, LocalDate de)`(Task 8)

- [ ] **Step 1: `OmsIbOrderRepository`에서 `nextOmsIbNoSeq` 제거**

```java
package com.project.omsback.inbound.repository;

import com.project.omsback.inbound.entity.OmsIbOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OmsIbOrderRepository extends JpaRepository<OmsIbOrder, Long>, OmsIbOrderRepositoryCustom {
}
```

- [ ] **Step 2: `IbOrderRepository`에서 `nextIbNoSeq`만 제거 (다른 메서드는 유지)**

`src/main/java/com/project/wmsback/inbound/repository/IbOrderRepository.java`에서 다음 블록만 삭제한다:

```java
    /**
     * 입고번호 채번값 발급. 시퀀스는 DB가 원자적으로 증가시키므로 동시 등록에도 중복이 없다.
     * QueryDSL은 JPA 엔티티 기반이라 "시퀀스.NEXTVAL"처럼 테이블/엔티티가 없는 스칼라 조회는
     * 표현할 대상이 없다 — 네이티브 쿼리로 남긴다.
     */
    @Query(value = "SELECT nextval('ib_no_seq')", nativeQuery = true)
    Long nextIbNoSeq();

```

`findByOmsIbOrderIdAndStatusNot`은 그대로 남긴다. `org.springframework.data.jpa.repository.Query` import가 이 파일에서 더 안 쓰이면 제거한다.

- [ ] **Step 3: `OmsIbOrderService.create()` 전환**

```java
        String omsIbNo = String.format("PO-%s-%03d",
                req.getExpctDe().format(DateTimeFormatter.BASIC_ISO_DATE),
                omsIbOrderRepository.nextOmsIbNoSeq());
```

를

```java
        String omsIbNo = nbrService.issue("OMS_IB_NO", req.getExpctDe());
```

로 바꾼다.

- [ ] **Step 4: `OmsIbOrderService.convert()` 전환**

```java
        String ibNo = String.format("IB-%s-%03d",
                order.getExpctDe().format(DateTimeFormatter.BASIC_ISO_DATE),
                ibOrderRepository.nextIbNoSeq());
```

를

```java
        String ibNo = nbrService.issue("IB_NO", order.getExpctDe());
```

로 바꾼다.

- [ ] **Step 5: 의존성/import 정리**

`OmsIbOrderService` 필드 선언에 `private final NbrService nbrService;`를 추가하고 `import com.project.wmsback.master.service.NbrService;`를 넣는다(이미 `wmsback.master`의 `Prod`/`Vendor`를 import하고 있어 wmsback→ 방향은 문제없다). `import java.time.format.DateTimeFormatter;`가 이 파일에서 더 안 쓰이면 제거한다.

- [ ] **Step 6: 컴파일 확인**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/project/omsback/inbound/repository/OmsIbOrderRepository.java \
        src/main/java/com/project/wmsback/inbound/repository/IbOrderRepository.java \
        src/main/java/com/project/omsback/inbound/service/OmsIbOrderService.java
git commit -m "refactor: 입고주문/입고번호 채번을 nbr 모듈로 전환"
```

---

## Task 16: 마이그레이션 적용 + 전체 수동 검증

**Files:** 없음(검증 전용)

**Interfaces:** 없음

- [ ] **Step 1: 전체 빌드 + 단위 테스트**

```bash
./mvnw clean test
```

Expected: BUILD SUCCESS, `NbrPatternTest`/`NbrRuleTest`/`NbrSeqTest`/`NbrServiceTest`/`NbrRuleServiceTest` 전부 PASS.

- [ ] **Step 2: `docs/migration-nbr.sql` 적용**

DBeaver에서 `docs/migration-nbr.sql`을 열고 Alt+X(Execute script)로 실행한다. Server Output 탭에서 `RAISE NOTICE` 로그를 확인한다.

- [ ] **Step 3: 마이그레이션 검증 쿼리 실행**

`docs/migration-nbr.sql` 하단의 "적용 후 확인" 섹션 3개 쿼리를 DBeaver에서 실행해 다음을 확인한다:
- `nbr_rule` 6건
- 각 `DATE` 규칙의 `nbr_seq.seq`가 실제 데이터의 최대 발급값과 일치
- 옛 시퀀스 6개가 `pg_sequences`에서 사라짐(빈 결과)

- [ ] **Step 4: 애플리케이션 기동**

```bash
./mvnw spring-boot:run
```

Expected: 정상 기동, 에러 로그 없음.

- [ ] **Step 5: 신규 채번 화면 API 수동 확인**

새 터미널에서:

```bash
curl -s http://localhost:8080/master/nbr-rules | head -c 500
```

Expected: 6개 규칙이 JSON 배열로 반환됨.

```bash
curl -s -X POST http://localhost:8080/master/nbr-rules/preview \
  -H "Content-Type: application/json" \
  -d '{"ptrn":"TEST-{SEQ:3}","dyncKyTyp":"NONE"}'
```

Expected: `{"number":"TEST-001"}`

```bash
curl -s -X POST http://localhost:8080/master/nbr-rules/PROD_CD/issue
```

Expected: `{"number":"PROD-XXXX"}` — 마이그레이션에서 시딩한 값 다음 번호(예: 기존 최대가 0012였다면 `PROD-0013`).

- [ ] **Step 6: 이관된 6개 흐름 수동 확인**

애플리케이션 화면(또는 각 도메인의 등록 API)으로 다음을 각 1건씩 실제로 만들어보고 번호 형식과 연속성을 확인한다:
- 상품 등록 → `prod_cd`가 `PROD-XXXX` 형식으로, 마이그레이션 시딩값 다음 번호로 나오는지
- 벤더 등록 → `vndr_cd`가 `VD-XXXX` 형식으로 이어지는지
- 입고주문 등록 → `oms_ib_no`가 `PO-{선택한 예정일}-NNN` 형식이고, 같은 예정일로 두 번째 주문을 만들면 NNN이 이어지는지(001부터 시작하지 않음 — 시딩 확인)
- 입고주문 변환(ASN 생성) → `ib_no`가 `IB-{예정일}-NNN` 형식인지
- 출고주문 등록 → `outb_no`가 `OB-{주문일}-NNN` 형식인지
- 웨이브 생성 → `wav_no`가 `WV-{오늘}-NNN` 형식인지

- [ ] **Step 7: 비활성 규칙 거부 확인**

DBeaver에서 `UPDATE nbr_rule SET us_yn = 'N' WHERE rule_cd = 'PROD_CD';` 실행 후:

```bash
curl -i -X POST http://localhost:8080/master/nbr-rules/PROD_CD/issue
```

Expected: HTTP 409, `{"message":"비활성화된 채번 규칙입니다: PROD_CD"}`

확인 후 원복: `UPDATE nbr_rule SET us_yn = 'Y' WHERE rule_cd = 'PROD_CD';`

- [ ] **Step 8: 애플리케이션 종료**

`Ctrl+C`로 `spring-boot:run` 프로세스를 종료한다.
