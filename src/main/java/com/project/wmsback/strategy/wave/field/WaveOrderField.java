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
 * <p>편성 조건은 <b>출고유형 · 차량편수 · 납품처그룹 · 납품처유형</b> 넷이다. 앞 둘은 주문 헤더의
 * 스칼라, 뒤 둘은 주문이 가리키는 점포 마스터의 스칼라이고, 넷 다 값 목록을 공통코드가 소유하므로
 * 화면 선택지가 코드관리와 자동으로 맞는다.
 *
 * <p>납품처그룹 · 납품처유형은 원래 「점포 마스터에 컬럼이 없어 보류」였다 — 컬럼 신설(2026-08-14,
 * store.store_grp · store_typ)로 해제됐다. 미지정(NULL) 점포는 부정 연산자(NE·NOT_IN)만 참이다
 * ({@code ConditionOperator} 규칙).
 *
 * <p>모든 필드에 대소·범위 연산자를 허용하지 않는다. 조건 비교가 문자열 사전순이라 차량편수에
 * GE/LE를 열면 "10"이 "2"보다 앞서게 된다 — 코드값 필드에는 등가 비교만 의미가 있다.
 */
public enum WaveOrderField implements ConditionField<WaveOrderTarget> {

    OUTB_TYP("출고유형", Set.of(EQ, NE, IN, NOT_IN), "outbTyps", WaveOrderTarget::outbTyp),
    VHCL_FLTNO("차량편수", Set.of(EQ, NE, IN, NOT_IN), "vhclFltnos", WaveOrderTarget::vhclFltno),
    STORE_GRP("납품처그룹", Set.of(EQ, NE, IN, NOT_IN), "storeGrps", WaveOrderTarget::storeGrp),
    STORE_TYP("납품처유형", Set.of(EQ, NE, IN, NOT_IN), "storeTyps", WaveOrderTarget::storeTyp);

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
