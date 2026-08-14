package com.project.wmsback.strategy.inspection.component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 검수 규칙. 상수 추가 = 메타 API(화면 선택지)·저장 검증·실행에 동시 반영이라
 * 코드에 없는 규칙은 화면에 존재할 수 없다 (P1). name()이 DB의 rule_cd다.
 * 각 상수가 파라미터 검증(validatePara)과 판정(skipReason/check)을 함께 가진다 —
 * 검증을 통과한 para만 저장되므로 check는 타입·범위를 다시 의심하지 않는다 (P2).
 */
public enum InspectionRule {

    /**
     * 유통기한 잔여비율 — 잔여% = (유통기한일수 − 경과일수) ÷ 유통기한일수 × 100 이
     * 기준 미만이면 입고를 차단한다. 제조일자 기준과 유통기한 기준은 같은 값을 다른 쪽에서
     * 재는 것이라 규칙을 하나만 둔다. 위반 메시지에 잔여율과 입고 가능 마지막 일자를 포함한다.
     */
    SHELF_LIFE_PCT("유통기한 잔여비율",
            "입고 시점의 잔여 유통기한 비율이 기준 미만이면 입고를 차단합니다. "
                    + "잔여% = (유통기한일수 − 경과일수) ÷ 유통기한일수 × 100. 유통기한 미관리 상품은 대상에서 제외됩니다.") {
        @Override
        public Map<String, Object> validatePara(Map<String, Object> raw) {
            Map<String, Object> remaining = mutableCopy(raw);
            BigDecimal minPercent = requireNumber(this, remaining, "minPercent", "최소 잔여비율(%)",
                    BigDecimal.ZERO, new BigDecimal(100));
            rejectUnknown(this, remaining);
            return Map.of("minPercent", minPercent);
        }

        @Override
        public Optional<String> skipReason(InspectionContext ctx) {
            // 0 이하는 잔여비율의 분모가 성립하지 않는다 — 미관리와 같게 제외
            if (ctx.prod().getShelfLifeDays() == null || ctx.prod().getShelfLifeDays() <= 0) {
                return Optional.of("유통기한 미관리 상품");
            }
            if (ctx.mfgDt() == null) {
                return Optional.of("제조일자 없음");
            }
            return Optional.empty();
        }

        @Override
        public Optional<Violation> check(InspectionContext ctx, Map<String, Object> para) {
            BigDecimal minPercent = number(para, "minPercent", BigDecimal.ZERO);
            int shelfLifeDays = ctx.prod().getShelfLifeDays();
            long elapsedDays = ChronoUnit.DAYS.between(ctx.mfgDt(), ctx.receiptDt());

            BigDecimal remainPercent = new BigDecimal(shelfLifeDays - elapsedDays)
                    .multiply(new BigDecimal(100))
                    .divide(new BigDecimal(shelfLifeDays), 1, RoundingMode.DOWN);

            if (remainPercent.compareTo(minPercent) < 0) {
                // 입고 가능 마지막 일자: 경과일수 <= 유통기한일수 × (100 − min) / 100 을 만족하는 마지막 날
                long maxElapsed = new BigDecimal(shelfLifeDays)
                        .multiply(new BigDecimal(100).subtract(minPercent))
                        .divide(new BigDecimal(100), 0, RoundingMode.DOWN)
                        .longValue();
                LocalDate lastOkDate = ctx.mfgDt().plusDays(maxElapsed);
                return Optional.of(new Violation(
                        "잔여 유통기한 " + remainPercent + "%가 최소 기준 " + minPercent
                                + "% 미만입니다 (입고 가능 마지막 일자 " + lastOkDate + ")",
                        remainPercent + "%",
                        ">= " + minPercent + "%"));
            }
            return Optional.empty();
        }
    },

    /**
     * 역순제한 — 기존 보유 재고의 최신 제조일자보다 과거인 제조일자의 입고를 차단한다.
     * "같은 날 들어온 로트를 기준에서 뺄지"는 암묵 동작으로 두지 않고 excludeSameDay
     * 파라미터로 노출한다 — 관리자가 화면에서 보고 선택한다.
     */
    LOT_DATE_REVERSE("역순제한",
            "기존 보유 재고의 최신 제조일자보다 과거인 제조일자는 입고를 차단합니다. "
                    + "유통기한 미관리 상품과 제조일자 없는 라인은 대상에서 제외됩니다.") {
        @Override
        public Map<String, Object> validatePara(Map<String, Object> raw) {
            Map<String, Object> remaining = mutableCopy(raw);
            boolean excludeSameDay = optionalBool(this, remaining, "excludeSameDay", "당일 입고분 제외", true);
            rejectUnknown(this, remaining);
            return Map.of("excludeSameDay", excludeSameDay);
        }

        @Override
        public Optional<String> skipReason(InspectionContext ctx) {
            // 0 이하는 잔여비율의 분모가 성립하지 않는다 — 미관리와 같게 제외
            if (ctx.prod().getShelfLifeDays() == null || ctx.prod().getShelfLifeDays() <= 0) {
                return Optional.of("유통기한 미관리 상품");
            }
            if (ctx.mfgDt() == null) {
                return Optional.of("제조일자 없음");
            }
            return Optional.empty();
        }

        @Override
        public Optional<Violation> check(InspectionContext ctx, Map<String, Object> para) {
            boolean excludeSameDay = bool(para, "excludeSameDay", true);
            LocalDate latest = ctx.lotQuery().latestMfgDtWithStock(
                    ctx.prod().getId(), excludeSameDay ? ctx.receiptDt() : null);
            if (latest != null && ctx.mfgDt().isBefore(latest)) {
                return Optional.of(new Violation(
                        "기존 재고의 최신 제조일자(" + latest + ")보다 과거인 제조일자입니다 — 역순 입고 차단",
                        ctx.mfgDt().toString(),
                        ">= " + latest));
            }
            return Optional.empty();
        }
    };

    private final String label;
    private final String dscr;

    InspectionRule(String label, String dscr) {
        this.label = label;
        this.dscr = dscr;
    }

    public String label() {
        return label;
    }

    public String dscr() {
        return dscr;
    }

    /** 은퇴 규칙 — 화면 신규 선택 불가, 기존 정의는 계속 실행. 현재는 없다 */
    public boolean deprecated() {
        return false;
    }

    /**
     * 저장 시 파라미터 검증·정규화 (P2). 실패는 저장 거부 —
     * "등록은 되는데 실행하면 예외"인 상태를 만들지 않는다.
     * @return 정규화된 값 맵 (NUMBER는 BigDecimal, BOOLEAN은 Boolean, 기본값 채움)
     */
    public abstract Map<String, Object> validatePara(Map<String, Object> raw);

    /**
     * 이 라인이 규칙의 관리 대상이 아니면 사유 반환 (예: 유통기한 미관리 상품).
     * 스킵은 통과로 간주하되 trace에 사유가 남는다 — 조용히 건너뛰지 않는다.
     */
    public abstract Optional<String> skipReason(InspectionContext ctx);

    /** 위반 없으면 empty, 위반 시 사유 반환. 예외를 흐름 제어에 쓰지 않는다 */
    public abstract Optional<Violation> check(InspectionContext ctx, Map<String, Object> para);

    /** 저장 시 검증용 조회 — 없으면 empty를 돌려주고 저장 서비스가 거부 메시지를 만든다 (P2) */
    public static Optional<InspectionRule> find(String code) {
        for (InspectionRule rule : values()) {
            if (rule.name().equals(code)) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    /**
     * 실행용 조회. 없으면 예외 — "저장된 정의가 배포본과 어긋남"을 뜻하며,
     * 저장 시 검증(P2) 때문에 정상 경로에서는 나올 수 없다 (운영 알람 대상).
     */
    public static InspectionRule of(String code) {
        return find(code).orElseThrow(() -> new IllegalStateException(
                "저장된 정책 정의가 배포본과 어긋납니다 — 미등록 검수 규칙: " + code));
    }

    // ── 파라미터 검증·읽기 공통 (validatePara·check가 같은 변환을 쓴다) ──────────

    private static Map<String, Object> mutableCopy(Map<String, Object> raw) {
        return raw != null ? new HashMap<>(raw) : new HashMap<>();
    }

    /** 필수 숫자 파라미터 — 없거나 숫자가 아니거나 범위 밖이면 저장 거부. 처리한 키는 맵에서 제거 */
    private static BigDecimal requireNumber(InspectionRule rule, Map<String, Object> remaining,
                                            String key, String label, BigDecimal min, BigDecimal max) {
        Object value = remaining.remove(key);
        if (value == null) {
            throw new IllegalArgumentException(rule.label() + ": 필수 파라미터가 없습니다 — " + label);
        }
        BigDecimal number;
        try {
            number = value instanceof Number n ? new BigDecimal(n.toString()) : new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(rule.label() + ": " + label + "은(는) 숫자여야 합니다 — " + value);
        }
        if (min != null && number.compareTo(min) < 0 || max != null && number.compareTo(max) > 0) {
            throw new IllegalArgumentException(rule.label() + ": " + label + " 값이 허용 범위("
                    + min + "~" + max + ")를 벗어났습니다 — " + number);
        }
        return number;
    }

    /** 선택 불리언 파라미터 — 없으면 기본값. 처리한 키는 맵에서 제거 */
    private static boolean optionalBool(InspectionRule rule, Map<String, Object> remaining,
                                        String key, String label, boolean defaultValue) {
        Object value = remaining.remove(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s && (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false"))) {
            return Boolean.parseBoolean(s);
        }
        throw new IllegalArgumentException(rule.label() + ": " + label + "은(는) true/false여야 합니다 — " + value);
    }

    /** 검증에서 소비되지 않고 남은 키 = 이 규칙에 없는 파라미터 → 저장 거부 */
    private static void rejectUnknown(InspectionRule rule, Map<String, Object> remaining) {
        if (!remaining.isEmpty()) {
            throw new IllegalArgumentException(rule.label() + ": 정의되지 않은 파라미터입니다 — " + remaining.keySet());
        }
    }

    /** 실행 시 숫자 읽기 — JSONB 역직렬화 타입(Integer/Double/BigDecimal)을 흡수한다 */
    private static BigDecimal number(Map<String, Object> para, String key, BigDecimal defaultValue) {
        Object value = para != null ? para.get(key) : null;
        if (value == null) {
            return defaultValue;
        }
        return value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
    }

    /** 실행 시 불리언 읽기 */
    private static boolean bool(Map<String, Object> para, String key, boolean defaultValue) {
        Object value = para != null ? para.get(key) : null;
        if (value == null) {
            return defaultValue;
        }
        return value instanceof Boolean b ? b : Boolean.parseBoolean(value.toString());
    }
}
