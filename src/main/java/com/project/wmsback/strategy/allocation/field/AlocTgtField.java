package com.project.wmsback.strategy.allocation.field;

import com.project.wmsback.strategy.core.condition.ConditionField;
import com.project.wmsback.strategy.core.condition.ConditionOperator;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.project.wmsback.strategy.core.condition.ConditionOperator.EQ;
import static com.project.wmsback.strategy.core.condition.ConditionOperator.IN;
import static com.project.wmsback.strategy.core.condition.ConditionOperator.NE;
import static com.project.wmsback.strategy.core.condition.ConditionOperator.NOT_IN;

/**
 * 할당 전략 적용대상 필드 (meta 도메인 "allocation-target"). 필드 추가 = 상수 추가 → 화면 자동 노출.
 *
 * <p>판정은 <b>대상 주문 전부</b>가 만족해야 매칭이다 — 전략이 실행 1회당 1건이라 「대부분 만족」
 * 같은 중간 상태를 둘 수 없다. 그래서 필드는 <b>웨이브 안에서 값이 갈리지 않는 축</b>이어야 한다.
 *
 * <p><b>점포를 넣지 않는 이유가 여기 있다.</b> 한 웨이브에 여러 점포가 섞이는 것이 정상이라
 * 점포 조건은 「전부 만족」이 사실상 성립하지 않고, 등록은 되는데 아무 웨이브에도 안 걸리는
 * 옵션이 된다 (P1이 막으려는 상태와 같다). 출고유형·차량편수는 웨이브 편성 조건과 같은 축이라
 * 실무적으로 「웨이브를 묶은 기준으로 할당 전략이 정해진다」가 된다.
 *
 * <p>두 필드 모두 대소·범위 연산자를 허용하지 않는다 — 조건 비교가 문자열 사전순이라
 * 차량편수에 GE/LE를 열면 "10"이 "2"보다 앞선다. 코드값 필드에는 등가 비교만 의미가 있다.
 */
public enum AlocTgtField implements ConditionField<AllocLineTarget> {

    OUTB_TYP("출고유형", Set.of(EQ, NE, IN, NOT_IN), "outbTyps", AllocLineTarget::outbTyp),
    VHCL_FLTNO("차량편수", Set.of(EQ, NE, IN, NOT_IN), "vhclFltnos", AllocLineTarget::vhclFltno);

    private final String label;
    private final Set<ConditionOperator> allowedOps;
    private final String optionSource;
    private final Function<AllocLineTarget, String> extractor;

    AlocTgtField(String label, Set<ConditionOperator> allowedOps, String optionSource,
                 Function<AllocLineTarget, String> extractor) {
        this.label = label;
        this.allowedOps = allowedOps;
        this.optionSource = optionSource;
        this.extractor = extractor;
    }

    public static final Map<String, AlocTgtField> BY_CODE =
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

    /** 차량편수는 배차 미정이면 null — 부정 연산자(NE/NOT_IN)만 참이 된다 (ConditionOperator 규약) */
    @Override
    public String extract(AllocLineTarget target) {
        return extractor.apply(target);
    }
}
