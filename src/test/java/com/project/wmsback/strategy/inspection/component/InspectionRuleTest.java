package com.project.wmsback.strategy.inspection.component;

import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.strategy.inspection.repository.InspectionQueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 검수 규칙의 동작 명세 — 규칙별 통과/위반/제외(skip)와 저장 검증(P2)을 시나리오로 남긴다.
 * 이 패키지를 처음 읽는다면 이 테스트를 위에서부터 읽는 것이 규칙 이해의 가장 빠른 길이다.
 */
class InspectionRuleTest {

    private static final LocalDate RECEIPT_DT = LocalDate.of(2026, 8, 14);

    private final Prod prod = mock(Prod.class);
    private final InspectionQueryRepository lotQuery = mock(InspectionQueryRepository.class);

    private InspectionContext ctx(LocalDate mfgDt) {
        return new InspectionContext(prod, RECEIPT_DT, mfgDt, lotQuery, false, false);
    }

    private InspectionContext rtngsCtx(LocalDate mfgDt, boolean rjctOnly) {
        return new InspectionContext(prod, RECEIPT_DT, mfgDt, lotQuery, true, rjctOnly);
    }

    @Nested
    @DisplayName("유통기한 잔여비율 (SHELF_LIFE_PCT)")
    class ShelfLifePct {

        private final InspectionRule rule = InspectionRule.SHELF_LIFE_PCT;

        @Test
        @DisplayName("잔여% = (유통기한일수 − 경과일수) ÷ 유통기한일수 × 100 — 기준 이상이면 통과한다")
        void passesWhenRemainingPctMeetsMin() {
            when(prod.getShelfLifeDays()).thenReturn(100);
            // 경과 30일 → 잔여 70.0%
            Map<String, Object> para = rule.validatePara(Map.of("minPercent", 70));

            assertTrue(rule.check(ctx(RECEIPT_DT.minusDays(30)), para).isEmpty());
        }

        @Test
        @DisplayName("기준 미만이면 위반 — 잔여율과 입고 가능 마지막 일자를 사유에 담는다")
        void violatesBelowMinWithLastOkDate() {
            when(prod.getShelfLifeDays()).thenReturn(100);
            Map<String, Object> para = rule.validatePara(Map.of("minPercent", 80));

            Optional<Violation> violation = rule.check(ctx(RECEIPT_DT.minusDays(30)), para);

            assertTrue(violation.isPresent());
            assertEquals("70.0%", violation.get().actual());
            assertEquals(">= 80%", violation.get().expected());
            // 경과 허용 20일 → 제조 2026-07-15 기준 입고 가능 마지막 일자 2026-08-04
            assertTrue(violation.get().message().contains("2026-08-04"));
        }

        @Test
        @DisplayName("유통기한 미관리 상품(NULL)과 0 이하는 판정 대상이 아니다 — 0으로 나누기 방어")
        void skipsUnmanagedAndZeroShelfLife() {
            when(prod.getShelfLifeDays()).thenReturn(null);
            assertEquals("유통기한 미관리 상품", rule.skipReason(ctx(RECEIPT_DT)).orElseThrow());

            when(prod.getShelfLifeDays()).thenReturn(0);
            assertEquals("유통기한 미관리 상품", rule.skipReason(ctx(RECEIPT_DT)).orElseThrow());
        }

        @Test
        @DisplayName("제조일자가 없으면 판정하지 않고 사유를 남긴다")
        void skipsWhenMfgDtMissing() {
            when(prod.getShelfLifeDays()).thenReturn(100);
            assertEquals("제조일자 없음", rule.skipReason(ctx(null)).orElseThrow());
        }

        @Test
        @DisplayName("입고 가능한 가장 이른 제조일자 — 입고일자 − 허용 경과일수. 그 날로 검수하면 통과하고 하루 전이면 위반이어야 한다")
        void minMfgDtIsEarliestPassingDate() {
            when(prod.getShelfLifeDays()).thenReturn(100);
            Map<String, Object> para = rule.validatePara(Map.of("minPercent", 80));

            // 경과 허용 20일 → 2026-07-25
            LocalDate min = rule.minMfgDt(ctx(null), para).orElseThrow();
            assertEquals(LocalDate.of(2026, 7, 25), min);
            assertTrue(rule.check(ctx(min), para).isEmpty());
            assertTrue(rule.check(ctx(min.minusDays(1)), para).isPresent());
        }

        @Test
        @DisplayName("기준에 소수가 있어도 하한은 실제 판정과 어긋나지 않는다 — 잔여율 절사 때문에 하루 밀릴 수 있다")
        void minMfgDtStaysConsistentWithCheckRounding() {
            when(prod.getShelfLifeDays()).thenReturn(7);
            Map<String, Object> para = rule.validatePara(Map.of("minPercent", "70.55"));

            LocalDate min = rule.minMfgDt(ctx(null), para).orElseThrow();
            assertTrue(rule.check(ctx(min), para).isEmpty());
            assertTrue(rule.check(ctx(min.minusDays(1)), para).isPresent());
        }

        @Test
        @DisplayName("유통기한 미관리 상품은 하한이 없다")
        void minMfgDtEmptyWhenUnmanaged() {
            when(prod.getShelfLifeDays()).thenReturn(null);
            assertTrue(rule.minMfgDt(ctx(null), rule.validatePara(Map.of("minPercent", 80))).isEmpty());
        }

        @Test
        @DisplayName("반품에서 양품이 0인 라인(불량만)은 판정하지 않는다 — 불량으로 받는 물건에 잔여수명 하한을 걸 이유가 없다")
        void skipsRjctOnlyLine() {
            when(prod.getShelfLifeDays()).thenReturn(100);
            assertEquals("양품 없음 (불량만 입고)", rule.skipReason(rtngsCtx(RECEIPT_DT.minusDays(90), true)).orElseThrow());
            assertTrue(rule.skipReason(rtngsCtx(RECEIPT_DT.minusDays(1), false)).isEmpty());
        }

        @Test
        @DisplayName("저장 검증 — minPercent는 필수, 0~100, 정의되지 않은 키는 거부한다 (P2)")
        void validateParaRejectsBadInput() {
            assertThrows(IllegalArgumentException.class, () -> rule.validatePara(Map.of()));
            assertThrows(IllegalArgumentException.class, () -> rule.validatePara(Map.of("minPercent", "abc")));
            assertThrows(IllegalArgumentException.class, () -> rule.validatePara(Map.of("minPercent", 101)));
            Map<String, Object> unknown = new HashMap<>(Map.of("minPercent", 30, "typo", 1));
            assertThrows(IllegalArgumentException.class, () -> rule.validatePara(unknown));

            // 정상 입력은 BigDecimal로 정규화된다 — 실행이 타입을 다시 의심하지 않는 근거
            assertEquals(new BigDecimal("30"), rule.validatePara(Map.of("minPercent", 30)).get("minPercent"));
        }
    }

    @Nested
    @DisplayName("역순제한 (LOT_DATE_REVERSE)")
    class LotDateReverse {

        private final InspectionRule rule = InspectionRule.LOT_DATE_REVERSE;

        @Test
        @DisplayName("기존 보유 재고의 최신 제조일자보다 과거인 제조일자는 위반이다")
        void violatesWhenMfgDtIsOlderThanStock() {
            when(prod.getShelfLifeDays()).thenReturn(100);
            when(prod.getId()).thenReturn(1L);
            when(lotQuery.latestMfgDtWithStock(1L, RECEIPT_DT)).thenReturn(LocalDate.of(2026, 7, 20));
            Map<String, Object> para = rule.validatePara(Map.of());

            Optional<Violation> violation = rule.check(ctx(LocalDate.of(2026, 7, 15)), para);

            assertTrue(violation.isPresent());
            assertEquals("2026-07-15", violation.get().actual());
            assertEquals(">= 2026-07-20", violation.get().expected());
        }

        @Test
        @DisplayName("같은 날짜·이후 날짜·기존 재고 없음은 전부 통과한다")
        void passesWhenNotOlder() {
            when(prod.getShelfLifeDays()).thenReturn(100);
            when(prod.getId()).thenReturn(1L);
            Map<String, Object> para = rule.validatePara(Map.of());

            when(lotQuery.latestMfgDtWithStock(1L, RECEIPT_DT)).thenReturn(LocalDate.of(2026, 7, 20));
            assertTrue(rule.check(ctx(LocalDate.of(2026, 7, 20)), para).isEmpty());
            assertTrue(rule.check(ctx(LocalDate.of(2026, 7, 25)), para).isEmpty());

            when(lotQuery.latestMfgDtWithStock(1L, RECEIPT_DT)).thenReturn(null);
            assertTrue(rule.check(ctx(LocalDate.of(2020, 1, 1)), para).isEmpty());
        }

        @Test
        @DisplayName("excludeSameDay=false면 당일 입고 로트도 기준에 포함한다 (기본값 true)")
        void excludeSameDayIsParameterized() {
            when(prod.getShelfLifeDays()).thenReturn(100);
            when(prod.getId()).thenReturn(1L);

            rule.check(ctx(LocalDate.of(2026, 7, 15)), rule.validatePara(Map.of()));
            verify(lotQuery).latestMfgDtWithStock(1L, RECEIPT_DT); // 기본 true → 당일 제외

            rule.check(ctx(LocalDate.of(2026, 7, 15)), rule.validatePara(Map.of("excludeSameDay", false)));
            verify(lotQuery).latestMfgDtWithStock(1L, null);       // false → 제외 없음
        }

        @Test
        @DisplayName("입고 가능한 가장 이른 제조일자 = 기존 재고의 최신 제조일자(같은 날 허용). 재고가 없으면 하한 없음")
        void minMfgDtIsLatestStockMfgDt() {
            when(prod.getShelfLifeDays()).thenReturn(100);
            when(prod.getId()).thenReturn(1L);
            Map<String, Object> para = rule.validatePara(Map.of());

            when(lotQuery.latestMfgDtWithStock(1L, RECEIPT_DT)).thenReturn(LocalDate.of(2026, 7, 20));
            assertEquals(LocalDate.of(2026, 7, 20), rule.minMfgDt(ctx(null), para).orElseThrow());

            when(lotQuery.latestMfgDtWithStock(1L, RECEIPT_DT)).thenReturn(null);
            assertTrue(rule.minMfgDt(ctx(null), para).isEmpty());

            when(prod.getShelfLifeDays()).thenReturn(null);
            assertTrue(rule.minMfgDt(ctx(null), para).isEmpty());
        }

        @Test
        @DisplayName("반품은 역순 제한 대상이 아니다 — 오래된 Lot이 FEFO 앞으로 가는 것이 반품에서는 맞다")
        void skipsForRtngs() {
            when(prod.getShelfLifeDays()).thenReturn(100);
            assertEquals("반품은 역순 제한 대상이 아님", rule.skipReason(rtngsCtx(RECEIPT_DT.minusDays(30), false)).orElseThrow());
        }

        @Test
        @DisplayName("저장 검증 — excludeSameDay는 true/false만, 정의되지 않은 키는 거부한다 (P2)")
        void validateParaRejectsBadInput() {
            assertThrows(IllegalArgumentException.class, () -> rule.validatePara(Map.of("excludeSameDay", "maybe")));
            assertThrows(IllegalArgumentException.class, () -> rule.validatePara(Map.of("typo", true)));

            assertEquals(Boolean.TRUE, rule.validatePara(Map.of()).get("excludeSameDay"));
            assertEquals(Boolean.FALSE, rule.validatePara(Map.of("excludeSameDay", "false")).get("excludeSameDay"));
        }
    }
}
