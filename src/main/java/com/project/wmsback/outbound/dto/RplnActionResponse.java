package com.project.wmsback.outbound.dto;

import java.util.List;

/** 보충 확정·취소 결과 — 처리된 보충지시 번호 */
public record RplnActionResponse(int count, List<String> invMovNos) {
}
