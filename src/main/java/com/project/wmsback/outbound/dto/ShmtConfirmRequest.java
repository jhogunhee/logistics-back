package com.project.wmsback.outbound.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 출고확정 요청 — 주문 단위. 여러 웨이브의 주문을 섞어 보내도 되고, 전부 한 트랜잭션이다 */
@Getter
@Setter
public class ShmtConfirmRequest {
    private List<Long> outbOrderIds;
}
