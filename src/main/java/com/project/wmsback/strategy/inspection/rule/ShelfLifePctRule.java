package com.project.wmsback.strategy.inspection.rule;

import com.project.wmsback.strategy.core.descriptor.ComponentDescriptor;
import com.project.wmsback.strategy.core.descriptor.ParamSpec;
import com.project.wmsback.strategy.core.param.ParamValues;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 유통기한 잔여비율 — 잔여% = (유통기한일수 − 경과일수) ÷ 유통기한일수 × 100 이
 * 기준 미만이면 입고를 차단한다. 레거시 D02(제조일자)/D03(유통기한)은 계산식이 완전히
 * 동일했으므로 하나로 통합했다. 위반 메시지에 잔여율과 입고 가능 마지막 일자를 포함한다.
 */
@Component
public class ShelfLifePctRule implements InspectionRule {

    public static final String CODE = "SHELF_LIFE_PCT";

    private static final ComponentDescriptor DESCRIPTOR = ComponentDescriptor.of(
            CODE,
            "유통기한 잔여비율",
            "입고 시점의 잔여 유통기한 비율이 기준 미만이면 입고를 차단합니다. "
                    + "잔여% = (유통기한일수 − 경과일수) ÷ 유통기한일수 × 100. 유통기한 미관리 상품은 대상에서 제외됩니다.",
            List.of(ParamSpec.number("minPercent", "최소 잔여비율(%)", true,
                    BigDecimal.ZERO, new BigDecimal(100), null))
    );

    @Override
    public ComponentDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Optional<String> skipReason(InspectionContext ctx) {
        if (ctx.prod().getShelfLifeDays() == null) {
            return Optional.of("유통기한 미관리 상품");
        }
        if (ctx.mfgDt() == null) {
            return Optional.of("제조일자 없음");
        }
        return Optional.empty();
    }

    @Override
    public Optional<Violation> check(InspectionContext ctx, ParamValues params) {
        BigDecimal minPercent = params.getNumber("minPercent", BigDecimal.ZERO);
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
}
