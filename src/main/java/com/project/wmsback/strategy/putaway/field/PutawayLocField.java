package com.project.wmsback.strategy.putaway.field;

import com.project.wmsback.strategy.core.condition.ConditionField;
import com.project.wmsback.strategy.core.condition.ConditionOperator;
import com.project.wmsback.strategy.putaway.method.PutawayMethodContext;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 적치위치 지정 필드 (meta 도메인 "putaway-loc"). 조건이 아니라 적용기준값 지정이라
 * 필드는 존 업무유형 하나, 연산자는 IN 하나다 — 화면은 업무유형 멀티선택만 보여준다.
 * (존·로케이션코드 기준은 2026-08-03 제거 — 위치 지정은 업무유형 단위로만 한다.)
 * 온도대·STORAGE는 여기 없다 — 지정이 아니라 모든 단계의 불변 전제(후보 모집 시 강제)다.
 */
public enum PutawayLocField implements ConditionField<PutawayMethodContext.LocStock> {

    /** 존 업무유형 (zon.biz_dvsn — 보관 STRG/피킹 PIKNG …). 존 미등록 로케이션은 지정 시 후보 제외 */
    BIZ_DVSN("존 업무유형", Set.of(ConditionOperator.IN), "bizDvsns", PutawayMethodContext.LocStock::bizDvsn);

    private final String label;
    private final Set<ConditionOperator> allowedOps;
    private final String optionSource;
    private final java.util.function.Function<PutawayMethodContext.LocStock, String> extractor;

    PutawayLocField(String label, Set<ConditionOperator> allowedOps, String optionSource,
                    java.util.function.Function<PutawayMethodContext.LocStock, String> extractor) {
        this.label = label;
        this.allowedOps = allowedOps;
        this.optionSource = optionSource;
        this.extractor = extractor;
    }

    public static final Map<String, PutawayLocField> BY_CODE =
            java.util.Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(Enum::name, f -> f));

    @Override
    public String code() {
        return name();
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public Set<ConditionOperator> allowedOps() {
        return allowedOps;
    }

    @Override
    public String optionSource() {
        return optionSource;
    }

    @Override
    public String extract(PutawayMethodContext.LocStock locStock) {
        return extractor.apply(locStock);
    }
}
