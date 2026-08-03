package com.project.wmsback.strategy.allocation.field;

import com.project.wmsback.strategy.core.condition.ConditionField;
import com.project.wmsback.strategy.core.condition.ConditionOperator;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 재고위치 계층 지정 필드 (meta 도메인 "allocation-invn"). 적치의 적치위치 지정과 같은 모양이다 —
 * 조건이 아니라 <b>계층 지정</b>이라 필드는 존 업무유형 하나, 연산자는 IN 하나이고,
 * 화면은 업무유형 멀티선택만 보여준다.
 *
 * <p>온도대는 여기 없다. 상품이 온도대를 이미 고정하므로 한 상품의 후보가 온도대로 갈리지 않고,
 * 갈리지 않는 축은 계층이 될 수 없다. 보관(STORAGE) 로케이션 한정도 마찬가지로 지정이 아니라
 * 모든 계층의 불변 전제다 (후보 조회에서 강제).
 *
 * <p>존이 등록되지 않은 로케이션은 값이 null이라 IN 조건에서 자연히 빠진다.
 * 현재 시드는 보관존이 온도대로만 갈리고 셋 다 STRG라, 피킹존을 등록하기 전까지는
 * 계층을 나눠도 1계층으로만 동작한다.
 */
public enum AlocInvnField implements ConditionField<AllocInvnCandidate> {

    /** 존 업무유형 (zon.biz_dvsn — 피킹 PIKNG / 보관 STRG …) */
    BIZ_DVSN("존 업무유형", Set.of(ConditionOperator.IN), "bizDvsns", AllocInvnCandidate::bizDvsn);

    private final String label;
    private final Set<ConditionOperator> allowedOps;
    private final String optionSource;
    private final Function<AllocInvnCandidate, String> extractor;

    AlocInvnField(String label, Set<ConditionOperator> allowedOps, String optionSource,
                  Function<AllocInvnCandidate, String> extractor) {
        this.label = label;
        this.allowedOps = allowedOps;
        this.optionSource = optionSource;
        this.extractor = extractor;
    }

    public static final Map<String, AlocInvnField> BY_CODE =
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
    public String extract(AllocInvnCandidate candidate) {
        return extractor.apply(candidate);
    }
}
