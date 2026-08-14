package com.project.wmsback.strategy.core.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.wmsback.strategy.core.dto.RvsnResponse;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import com.project.wmsback.strategy.core.service.StgyRvsnService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 전략 리비전 조회 — 네 유형 공용. 리비전은 (유형, 전략 id, 리비전 번호)로만 식별되고
 * 유형별로 다르게 읽을 것이 없어서 실행 로그와 같이 core에 하나만 둔다.
 * 스냅샷 쓰기는 전략 저장 트랜잭션 안에서 일어나므로 여기엔 조회만 있다.
 */
@RestController
@RequestMapping("/strategy/revisions")
@RequiredArgsConstructor
public class StrategyRvsnController {

    private final StgyRvsnService stgyRvsnService;

    @GetMapping
    public List<RvsnResponse> list(@RequestParam StgyTyp stgyTyp, @RequestParam Long stgyId) {
        return stgyRvsnService.list(stgyTyp, stgyId);
    }

    @GetMapping("/{rvsnNo}")
    public JsonNode snapshot(@RequestParam StgyTyp stgyTyp, @RequestParam Long stgyId,
                             @PathVariable Long rvsnNo) {
        return stgyRvsnService.snapshotTree(stgyTyp, stgyId, rvsnNo);
    }
}
