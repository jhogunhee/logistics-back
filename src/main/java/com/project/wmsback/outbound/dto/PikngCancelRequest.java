package com.project.wmsback.outbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 피킹지시 취소 요청 — <b>웨이브 단위 · 실적 0일 때만.</b> 지시 행 단위 취소는 없다 —
 * 발행이 웨이브 단위 원자 조작이므로 취소도 그 역이고, 일부만 물리고 싶으면
 * 전량 취소 → PLANNED에서 편성 변경 → 재발행 경로를 쓴다.
 */
@Getter
@Setter
@NoArgsConstructor
public class PikngCancelRequest {

    private List<Long> wavIds;
}
