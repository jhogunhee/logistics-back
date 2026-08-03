package com.project.wmsback.strategy.putaway.field;

import com.project.wmsback.strategy.core.condition.ConditionField;
import com.project.wmsback.strategy.core.condition.ConditionOperator;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.project.wmsback.strategy.core.condition.ConditionOperator.EQ;
import static com.project.wmsback.strategy.core.condition.ConditionOperator.IN;
import static com.project.wmsback.strategy.core.condition.ConditionOperator.NE;
import static com.project.wmsback.strategy.core.condition.ConditionOperator.NOT_IN;

/**
 * 적치 적용대상·라인 조건 필드 (meta 도메인 "putaway-target").
 * 필드 추가 = 상수 추가 → 화면(ConditionBuilder)에 자동 노출.
 * 현행 입고 모델에 실존하는 필드만 둔다 — 값을 꺼낼 컬럼이 없는 조건은 만들지 않는다.
 */
public enum PutawayTargetField implements ConditionField<PutawayTarget> {

    TMP_ZON("상품 온도대", Set.of(EQ, NE, IN, NOT_IN), "tmpZones", t -> t.prod().getTmpZon().name()),
    VNDR("입고 벤더", Set.of(EQ, NE, IN, NOT_IN), "vendors", PutawayTarget::vndrCd),
    PROD("상품", Set.of(EQ, NE, IN, NOT_IN), "prods", t -> t.prod().getProdCd()),
    /** 입고단위(포장단위). 파렛트 입고를 보관존으로 직행시키는 류의 단계 분기에 쓴다 */
    INB_UOM("입고단위", Set.of(EQ, NE, IN, NOT_IN), "uoms", t -> t.prod().getInbUomCd());

    private final String label;
    private final Set<ConditionOperator> allowedOps;
    private final String optionSource;
    private final Function<PutawayTarget, String> extractor;

    PutawayTargetField(String label, Set<ConditionOperator> allowedOps, String optionSource,
                       Function<PutawayTarget, String> extractor) {
        this.label = label;
        this.allowedOps = allowedOps;
        this.optionSource = optionSource;
        this.extractor = extractor;
    }

    public static final Map<String, PutawayTargetField> BY_CODE =
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
    public String extract(PutawayTarget target) {
        return extractor.apply(target);
    }
}
