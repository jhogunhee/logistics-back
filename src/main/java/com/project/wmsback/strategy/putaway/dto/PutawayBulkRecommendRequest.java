package com.project.wmsback.strategy.putaway.dto;

import java.util.List;

/**
 * 적치지시 일괄 추천 요청. 화면이 고른 배치들을 <b>보이는 순서(유통기한 FEFO) 그대로</b> 보낸다 —
 * 이 순서가 곧 로케이션 용량을 먼저 차지하는 순서다.
 */
public record PutawayBulkRecommendRequest(List<Item> items) {

    public record Item(Long ibLineId, Long lotId, Long qty) {
    }
}
