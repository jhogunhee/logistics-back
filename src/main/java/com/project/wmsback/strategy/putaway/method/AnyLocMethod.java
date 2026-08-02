package com.project.wmsback.strategy.putaway.method;

import com.project.wmsback.strategy.core.descriptor.ComponentDescriptor;
import com.project.wmsback.strategy.core.param.ParamValues;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 전체 보관 — 온도대 일치 보관 로케이션 전부를 후보로. 현행 수동 후보 목록과 같은 모집단이라
 * 마지막 단계의 안전망 용도다 (앞 단계에서 못 채운 잔여를 어디든 넣는다).
 */
@Component
public class AnyLocMethod implements PutawayMethod {

    public static final String CODE = "ANY_LOC";

    private static final ComponentDescriptor DESCRIPTOR = ComponentDescriptor.of(
            CODE,
            "전체 보관",
            "온도대가 맞는 모든 보관 로케이션을 후보로 합니다. 앞 단계에서 못 채운 잔여의 안전망으로 마지막에 둡니다.",
            List.of()
    );

    @Override
    public ComponentDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public List<PutawayMethodContext.LocStock> candidates(PutawayMethodContext ctx, ParamValues params) {
        return ctx.storageLocs();
    }
}
