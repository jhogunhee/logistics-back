package com.project.wmsback.strategy.putaway.field;

import com.project.mdm.prod.entity.Prod;

/**
 * 적치 단계 라인 조건(line_cond)의 판정 대상. 적용대상은 조건이 아니라 odr_dvsn 스칼라 매칭이다.
 * vndrCd는 가상 미리보기(상품·수량 직접 입력)에서는 null일 수 있다.
 */
public record PutawayTarget(Prod prod, String vndrCd) {
}
