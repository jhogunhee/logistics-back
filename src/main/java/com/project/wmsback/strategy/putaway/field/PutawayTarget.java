package com.project.wmsback.strategy.putaway.field;

import com.project.wmsback.master.entity.Prod;

/**
 * 적치 조건 판정 대상 — 적용대상(tgt_cond)과 단계 라인 조건(line_cond)이 공유한다.
 * vndrCd는 가상 미리보기(상품·수량 직접 입력)에서는 null일 수 있다.
 */
public record PutawayTarget(Prod prod, String vndrCd) {
}
