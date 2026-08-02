package com.project.wmsback.strategy.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.wmsback.strategy.core.dto.ExecLogResponse;
import com.project.wmsback.strategy.core.entity.StgyExecLog;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import com.project.wmsback.strategy.core.entity.TrgrTyp;
import com.project.wmsback.strategy.core.repository.StgyExecLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 실행 로그 기록·조회. 기록은 REQUIRES_NEW — 검수 위반으로 업무 트랜잭션이 롤백돼도
 * "위반으로 차단했다"는 로그는 남아야 한다 (검수는 결과 행이 없는 유형이라 이게 유일한 흔적).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StgyExecLogService {

    private static final Logger log = LoggerFactory.getLogger(StgyExecLogService.class);

    private final StgyExecLogRepository stgyExecLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(StgyTyp stgyTyp, Long stgyId, Long rvsnNo, TrgrTyp trgrTyp,
                    String tgtRef, String rsltSmry, Object trace) {
        try {
            stgyExecLogRepository.save(StgyExecLog.builder()
                    .stgyTyp(stgyTyp)
                    .stgyId(stgyId)
                    .rvsnNo(rvsnNo)
                    .trgrTyp(trgrTyp)
                    .tgtRef(tgtRef)
                    .rsltSmry(rsltSmry)
                    .dcsnTrc(trace != null ? objectMapper.writeValueAsString(trace) : null)
                    .build());
        } catch (JsonProcessingException e) {
            // 로그 실패가 업무 실행을 막으면 안 된다 — 기록만 포기하고 경고
            log.warn("전략 실행 로그 직렬화 실패 (기록 생략): {} {}", stgyTyp, tgtRef, e);
        }
    }

    public List<ExecLogResponse> list(StgyTyp stgyTyp, Long stgyId) {
        List<StgyExecLog> rows = stgyId != null
                ? stgyExecLogRepository.findTop100ByStgyTypAndStgyIdOrderByCreatedAtDesc(stgyTyp, stgyId)
                : stgyExecLogRepository.findTop100ByStgyTypOrderByCreatedAtDesc(stgyTyp);
        return rows.stream().map(row -> ExecLogResponse.from(row, objectMapper)).toList();
    }
}
