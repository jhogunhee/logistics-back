package com.project.wmsback.strategy.wave.field;

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
 * 웨이브 조건 필드 (meta 도메인 "wave-order"). 필드 추가 = 상수 추가 → 화면 자동 노출.
 *
 * <p>편성 조건은 <b>출고유형 · 차량편수</b> 둘이다. 둘 다 주문 헤더의 스칼라이고 값 목록을
 * 공통코드가 소유하므로 화면 선택지가 코드관리와 자동으로 맞는다.
 *
 * <p><b>납품처그룹 · 납품처유형은 보류</b>다 — 점포 마스터에 그룹·유형 컬럼이 없어 값을 꺼낼 곳이
 * 없다. 「등록만 되고 동작하지 않는 옵션」을 만들지 않는다는 원칙(P1) 때문에 컬럼 신설이 선행돼야
 * 하고, 그때 이 enum에 상수 두 개를 추가하면 화면·저장 검증·실행에 동시에 반영된다.
 *
 * <p>두 필드 모두 대소·범위 연산자를 허용하지 않는다. 조건 비교가 문자열 사전순이라 차량편수에
 * GE/LE를 열면 "10"이 "2"보다 앞서게 된다 — 코드값 필드에는 등가 비교만 의미가 있다.
 */
public enum WaveOrderField implements ConditionField<WaveOrderTarget> {

    OUTB_TYP("출고유형", Set.of(EQ, NE, IN, NOT_IN), "outbTyps", WaveOrderTarget::outbTyp),
    VHCL_FLTNO("차량편수", Set.of(EQ, NE, IN, NOT_IN), "vhclFltnos", WaveOrderTarget::vhclFltno);

    private final String label;
    private final Set<ConditionOperator> allowedOps;
    private final String optionSource;
    private final Function<WaveOrderTarget, String> extractor;

    WaveOrderField(String label, Set<ConditionOperator> allowedOps, String optionSource,
                   Function<WaveOrderTarget, String> extractor) {
        this.label = label;
        this.allowedOps = allowedOps;
        this.optionSource = optionSource;
        this.extractor = extractor;
    }

    public static final Map<String, WaveOrderField> BY_CODE =
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
    public String extract(WaveOrderTarget target) {
        return extractor.apply(target);
    }
}
