package com.project.wmsback.strategy.core.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.wmsback.strategy.core.entity.StgyExecLog;

import java.time.LocalDateTime;

/** 실행 로그 행. dcsnTrc는 JSON 객체로 심는다 — 프론트가 그대로 판정 표를 그린다 */
public record ExecLogResponse(
        Long id,
        String stgyTyp,
        Long stgyId,
        Long rvsnNo,
        String trgrTyp,
        String tgtRef,
        String rsltSmry,
        JsonNode dcsnTrc,
        LocalDateTime executedAt,
        String executedBy
) {

    public static ExecLogResponse from(StgyExecLog log, ObjectMapper objectMapper) {
        JsonNode trace = null;
        if (log.getDcsnTrc() != null) {
            try {
                trace = objectMapper.readTree(log.getDcsnTrc());
            } catch (JsonProcessingException ignored) {
                // 손상된 trace는 null로 — 목록 조회가 한 행 때문에 통째로 죽지 않게
            }
        }
        return new ExecLogResponse(log.getId(), log.getStgyTyp().name(), log.getStgyId(), log.getRvsnNo(),
                log.getTrgrTyp().name(), log.getTgtRef(), log.getRsltSmry(), trace,
                log.getCreatedAt(), log.getCreatedBy());
    }
}
