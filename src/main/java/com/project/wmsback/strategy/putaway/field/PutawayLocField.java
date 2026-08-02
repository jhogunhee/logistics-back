package com.project.wmsback.strategy.putaway.field;

import com.project.wmsback.strategy.core.condition.ConditionField;
import com.project.wmsback.strategy.core.condition.ConditionOperator;
import com.project.wmsback.strategy.putaway.method.PutawayMethodContext;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.project.wmsback.strategy.core.condition.ConditionOperator.EQ;
import static com.project.wmsback.strategy.core.condition.ConditionOperator.IN;
import static com.project.wmsback.strategy.core.condition.ConditionOperator.LIKE;
import static com.project.wmsback.strategy.core.condition.ConditionOperator.NE;
import static com.project.wmsback.strategy.core.condition.ConditionOperator.NOT_IN;

/**
 * 적치 단계의 로케이션 범위 조건 필드 (meta 도메인 "putaway-loc"). 판정 대상은 후보 조회가
 * 만든 LocStock — 존 업무유형처럼 로케이션 밖(존)에 있는 속성도 조회 시 함께 실어 온다.
 * 온도대·STORAGE는 여기 없다 — 조건이 아니라 모든 단계의 불변 전제(후보 모집 시 강제)다.
 */
public enum PutawayLocField implements ConditionField<PutawayMethodContext.LocStock> {

    ZON("존", Set.of(EQ, NE, IN, NOT_IN), "zones", ls -> ls.loc().getZonCd()),
    LOC_CD("로케이션코드", Set.of(EQ, NE, IN, NOT_IN, LIKE), null, ls -> ls.loc().getLocCd()),
    /** 존 업무유형 (zon.biz_dvsn — 보관 STRG/피킹 PIKNG …). 레거시 "로케이션 유형(보관/피킹)" 조건의 대응 */
    BIZ_DVSN("존 업무유형", Set.of(EQ, NE, IN, NOT_IN), "bizDvsns", PutawayMethodContext.LocStock::bizDvsn);

    private final String label;
    private final Set<ConditionOperator> allowedOps;
    private final String optionSource;
    private final Function<PutawayMethodContext.LocStock, String> extractor;

    PutawayLocField(String label, Set<ConditionOperator> allowedOps, String optionSource,
                    Function<PutawayMethodContext.LocStock, String> extractor) {
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
