package com.project.wmsback.strategy.allocation.field;

import com.project.wmsback.strategy.core.condition.ConditionField;
import com.project.wmsback.strategy.core.condition.ConditionOperator;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.project.wmsback.strategy.core.condition.ConditionOperator.BETWEEN;
import static com.project.wmsback.strategy.core.condition.ConditionOperator.EQ;
import static com.project.wmsback.strategy.core.condition.ConditionOperator.GE;
import static com.project.wmsback.strategy.core.condition.ConditionOperator.IN;
import static com.project.wmsback.strategy.core.condition.ConditionOperator.LE;
import static com.project.wmsback.strategy.core.condition.ConditionOperator.NE;
import static com.project.wmsback.strategy.core.condition.ConditionOperator.NOT_IN;

/**
 * 분배 대상 선별 필드 (meta 도메인 "allocation-line"). 「누구에게 먼저 나눌지」를 정한다 —
 * 재고가 모자랄 때 특정 점포·유형의 라인만 골라 먼저 배분하는 정의가 이 필드들로 표현된다.
 *
 * <p>적용대상(AlocTgtField)과 달리 <b>점포가 있다.</b> 적용대상은 대상 주문 전부가 만족해야 하는
 * 판정이라 웨이브 안에서 갈리는 축을 쓸 수 없지만, 분배 대상은 <b>갈리는 것이 목적</b>이기
 * 때문이다 — 같은 웨이브의 라인들을 점포로 나누는 것이 이 슬롯의 존재 이유다.
 *
 * <p>출고예정일에만 대소·범위 연산자를 연다. 값이 ISO 일자(yyyy-MM-dd)라 문자열 사전순 비교가
 * 곧 날짜순 비교이기 때문이고, 나머지 코드값 필드는 등가 비교만 의미가 있다.
 */
public enum AlocLineField implements ConditionField<AlocLineTarget> {

    STORE_CD("점포", Set.of(EQ, NE, IN, NOT_IN), "stores", AlocLineTarget::storeCd),
    OUTB_TYP("출고유형", Set.of(EQ, NE, IN, NOT_IN), "outbTyps", AlocLineTarget::outbTyp),
    VHCL_FLTNO("차량편수", Set.of(EQ, NE, IN, NOT_IN), "vhclFltnos", AlocLineTarget::vhclFltno),
    EXPCT_DE("출고예정일", Set.of(EQ, NE, GE, LE, BETWEEN), null,
            target -> Objects.toString(target.expctDe(), null));

    private final String label;
    private final Set<ConditionOperator> allowedOps;
    private final String optionSource;
    private final Function<AlocLineTarget, String> extractor;

    AlocLineField(String label, Set<ConditionOperator> allowedOps, String optionSource,
                  Function<AlocLineTarget, String> extractor) {
        this.label = label;
        this.allowedOps = allowedOps;
        this.optionSource = optionSource;
        this.extractor = extractor;
    }

    public static final Map<String, AlocLineField> BY_CODE =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(Enum::name, f -> f));

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
    public String extract(AlocLineTarget target) {
        return extractor.apply(target);
    }
}
