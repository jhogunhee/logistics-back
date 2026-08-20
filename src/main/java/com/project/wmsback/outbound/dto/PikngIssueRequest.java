package com.project.wmsback.outbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 피킹지시 발행 요청. 웨이브를 여러 건 고를 수 있지만 <b>한 트랜잭션이다</b> —
 * 도중 실패(할당 0건 주문 존재 등)하면 이번 발행 전체가 롤백된다 (부분 성공 없음 —
 * 자동할당·웨이브 전략 실행과 같은 원칙).
 */
@Getter
@Setter
@NoArgsConstructor
public class PikngIssueRequest {

    private List<Long> wavIds;
}
