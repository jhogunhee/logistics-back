package com.project.wmsback.strategy.inspection.rule;

import com.project.wmsback.strategy.core.descriptor.ComponentDescriptor;
import com.project.wmsback.strategy.core.descriptor.ParamSpec;
import com.project.wmsback.strategy.core.param.ParamValues;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 역순제한 — 기존 보유 재고의 최신 제조일자보다 과거인 제조일자의 입고를 차단한다.
 * 레거시의 "당일 입고분 제외" 암묵 동작을 excludeSameDay 파라미터로 승격 —
 * 관리자가 화면에서 보고 선택한다.
 */
@Component
public class LotDateReverseRule implements InspectionRule {

    public static final String CODE = "LOT_DATE_REVERSE";

    private static final ComponentDescriptor DESCRIPTOR = ComponentDescriptor.of(
            CODE,
            "역순제한",
            "기존 보유 재고의 최신 제조일자보다 과거인 제조일자는 입고를 차단합니다. "
                    + "유통기한 미관리 상품과 제조일자 없는 라인은 대상에서 제외됩니다.",
            List.of(ParamSpec.bool("excludeSameDay", "당일 입고분 제외", "true"))
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
        boolean excludeSameDay = params.getBool("excludeSameDay", true);
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
}
