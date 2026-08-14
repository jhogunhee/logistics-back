package com.project.wmsback.strategy.core.service;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.EnumSet;
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
    private final PlatformTransactionManager transactionManager;

    /**
     * 로그 실패가 업무 실행을 막으면 안 된다 — 직렬화·저장·커밋 어느 단계가 실패해도
     * 기록만 포기하고 경고를 남긴다. 애노테이션 대신 TransactionTemplate으로 REQUIRES_NEW를
     * 여는 이유가 이것이다 — 커밋은 프록시가 메서드 밖에서 수행하므로 메서드 안의 catch가
     * 커밋 실패를 잡을 수 없다.
     */
    public void log(StgyTyp stgyTyp, Long stgyId, Long rvsnNo, TrgrTyp trgrTyp,
                    String tgtRef, String rsltSmry, Object trace) {
        try {
            StgyExecLog row = StgyExecLog.builder()
                    .stgyTyp(stgyTyp)
                    .stgyId(stgyId)
                    .rvsnNo(rvsnNo)
                    .trgrTyp(trgrTyp)
                    .tgtRef(tgtRef)
                    .rsltSmry(rsltSmry)
                    .dcsnTrc(trace != null ? objectMapper.writeValueAsString(trace) : null)
                    .build();
            TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
            requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            requiresNew.executeWithoutResult(status -> stgyExecLogRepository.save(row));
        } catch (Exception e) {
            log.warn("전략 실행 로그 기록 실패 (기록 생략): {} {}", stgyTyp, tgtRef, e);
        }
    }

    /**
     * 최근 실행 로그. trgrTyps가 비면 실행 기록만 본다 — 미리보기는 결과를 반영하지 않은
     * 산정이라 「무엇이 실제로 일어났나」를 묻는 기본 화면에 섞이면 안 되고, 100건 상한을
     * 나눠 쓰면 실행 이력이 밀려나기 때문이다. 미리보기까지 보려면 호출부가 명시한다.
     */
    public List<ExecLogResponse> list(StgyTyp stgyTyp, Long stgyId, Collection<TrgrTyp> trgrTyps) {
        Collection<TrgrTyp> effective = trgrTyps == null || trgrTyps.isEmpty()
                ? EnumSet.of(TrgrTyp.MANUAL, TrgrTyp.AUTO)
                : trgrTyps;
        List<StgyExecLog> rows = stgyId != null
                ? stgyExecLogRepository.findTop100ByStgyTypAndStgyIdAndTrgrTypInOrderByCreatedAtDesc(
                        stgyTyp, stgyId, effective)
                : stgyExecLogRepository.findTop100ByStgyTypAndTrgrTypInOrderByCreatedAtDesc(
                        stgyTyp, effective);
        return rows.stream().map(row -> ExecLogResponse.from(row, objectMapper)).toList();
    }
}
