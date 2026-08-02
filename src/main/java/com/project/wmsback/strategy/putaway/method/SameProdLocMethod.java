package com.project.wmsback.strategy.putaway.method;

import com.project.wmsback.strategy.core.descriptor.ComponentDescriptor;
import com.project.wmsback.strategy.core.param.ParamValues;
import org.springframework.stereotype.Component;

import java.util.List;

/** 적재로케이션 — 같은 상품 재고(on_hand>0)가 이미 있는 보관 로케이션을 후보로 */
@Component
public class SameProdLocMethod implements PutawayMethod {

    public static final String CODE = "SAME_PROD_LOC";

    private static final ComponentDescriptor DESCRIPTOR = ComponentDescriptor.of(
            CODE,
            "적재로케이션",
            "같은 상품의 재고가 이미 있는 보관 로케이션에 합쳐 적치합니다. 로케이션 파편화를 줄일 때 앞 단계로 둡니다.",
            List.of()
    );

    @Override
    public ComponentDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public List<PutawayMethodContext.LocStock> candidates(PutawayMethodContext ctx, ParamValues params) {
        return ctx.storageLocs().stream().filter(PutawayMethodContext.LocStock::hasProd).toList();
    }
}
