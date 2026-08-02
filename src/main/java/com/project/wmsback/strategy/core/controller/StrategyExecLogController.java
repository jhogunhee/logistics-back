package com.project.wmsback.strategy.core.controller;

import com.project.wmsback.strategy.core.dto.ExecLogResponse;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import com.project.wmsback.strategy.core.service.StgyExecLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 전략 실행 로그 조회 — "이 라인이 왜 차단됐나 / 이 배치가 왜 이렇게 배정됐나"의 근거 데이터 */
@RestController
@RequestMapping("/strategy/executions")
@RequiredArgsConstructor
public class StrategyExecLogController {

    private final StgyExecLogService stgyExecLogService;

    @GetMapping
    public List<ExecLogResponse> list(@RequestParam StgyTyp stgyTyp,
                                      @RequestParam(required = false) Long stgyId) {
        return stgyExecLogService.list(stgyTyp, stgyId);
    }
}
