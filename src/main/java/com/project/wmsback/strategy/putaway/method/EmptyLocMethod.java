package com.project.wmsback.strategy.putaway.method;

import com.project.wmsback.strategy.core.descriptor.ComponentDescriptor;
import com.project.wmsback.strategy.core.param.ParamValues;
import org.springframework.stereotype.Component;

import java.util.List;

/** 빈로케이션 — 재고가 전혀 없는 보관 로케이션을 후보로 */
@Component
public class EmptyLocMethod implements PutawayMethod {

    public static final String CODE = "EMPTY_LOC";

    private static final ComponentDescriptor DESCRIPTOR = ComponentDescriptor.of(
            CODE,
            "빈로케이션",
            "재고가 전혀 없는 보관 로케이션에 적치합니다. 새 로케이션을 여는 단계 — 보통 적재로케이션 뒤에 둡니다.",
            List.of()
    );

    @Override
    public ComponentDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public List<PutawayMethodContext.LocStock> candidates(PutawayMethodContext ctx, ParamValues params) {
        return ctx.storageLocs().stream().filter(ls -> ls.occupiedQty() == 0).toList();
    }
}
