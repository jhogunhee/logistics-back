package com.project.wmsback.strategy.putaway.method;

import com.project.wmsback.strategy.core.descriptor.StrategyComponent;
import com.project.wmsback.strategy.core.param.ParamValues;

import java.util.List;

/**
 * 적치 추천 방식 구현체 규약. 후보 로케이션 선정만 담당한다 —
 * 조건 필터·정렬·수량 분할은 추천 서비스가 담당 (구현체는 후보 판정에 집중).
 */
public interface PutawayMethod extends StrategyComponent {

    /** ctx의 보관 로케이션 재고 현황에서 이 방식의 후보를 고른다 */
    List<PutawayMethodContext.LocStock> candidates(PutawayMethodContext ctx, ParamValues params);
}
