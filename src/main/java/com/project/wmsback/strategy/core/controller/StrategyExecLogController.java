package com.project.wmsback.strategy.core.controller;

import com.project.common.dto.PageCond;
import com.project.common.dto.PageResponse;
import com.project.wmsback.strategy.core.dto.ExecLogResponse;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import com.project.wmsback.strategy.core.entity.TrgrTyp;
import com.project.wmsback.strategy.core.service.StgyExecLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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

    /** trgrTyp를 주지 않으면 실행 기록만(MANUAL·AUTO) — 미리보기까지 보려면 명시한다 */
    @GetMapping
    public PageResponse<ExecLogResponse> list(@RequestParam StgyTyp stgyTyp,
                                              @RequestParam(required = false) Long stgyId,
                                              @RequestParam(required = false) List<TrgrTyp> trgrTyp,
                                              @ModelAttribute PageCond pageCond) {
        return stgyExecLogService.list(stgyTyp, stgyId, trgrTyp, pageCond);
    }
}
