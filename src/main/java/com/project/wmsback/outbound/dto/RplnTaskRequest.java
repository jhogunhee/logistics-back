package com.project.wmsback.outbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** 보충 확정·취소 요청 — 보충지시(inv_mov_task) id 목록. 확정은 전량만이라 수량이 없다 */
@Getter
@Setter
@NoArgsConstructor
public class RplnTaskRequest {
    private List<Long> taskIds;
}
