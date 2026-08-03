package com.project.wmsback.strategy.wave.service;

import com.project.mdm.nbr.service.NbrService;
import com.project.wmsback.outbound.dto.OutbOrderSearchCond;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbStatus;
import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.entity.WavRegTyp;
import com.project.wmsback.outbound.repository.OutbOrderRepository;
import com.project.wmsback.outbound.repository.OutbWaveRepository;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import com.project.wmsback.strategy.core.entity.TrgrTyp;
import com.project.wmsback.strategy.core.service.StgyExecLogService;
import com.project.wmsback.strategy.wave.dto.WavPreviewRequest;
import com.project.wmsback.strategy.wave.dto.WavPreviewResponse;
import com.project.wmsback.strategy.wave.dto.WavStgyDefinition;
import com.project.wmsback.strategy.wave.dto.WaveMatchResult;
import com.project.wmsback.strategy.wave.dto.WaveStgyExecRequest;
import com.project.wmsback.strategy.wave.dto.WaveStgyExecResponse;
import com.project.wmsback.strategy.wave.entity.WavStgy;
import com.project.wmsback.strategy.wave.field.WaveOrderTarget;
import com.project.wmsback.strategy.wave.repository.WavStgyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 웨이브 전략 실행·미리보기.
 *
 * <p><b>실행 = 전략마다 웨이브 1개 생성 + 조건에 맞는 미편성 주문 편입.</b> 전략은 prty 순으로
 * 순회하고, 앞 전략이 가져간 주문은 뒤 전략의 후보에서 빠진다(주문은 웨이브 1개 — 선점).
 *
 * <p><b>편입 0건이면 웨이브를 만들지 않는다.</b> 이것이 재실행 안전장치다 — 매칭된 주문은 첫
 * 실행에서 편성돼 후보에서 빠지므로, 두 번째 실행은 편입 0건 → 웨이브 생성 없음으로 끝난다.
 * "오늘 이미 실행됨" 같은 별도 키를 두지 않는 이유이자, 실행 후 새 주문이 들어오면 하루에도
 * 여러 번 다시 실행할 수 있는 이유다. 빈 웨이브는 어차피 따로 치워야 하는 찌꺼기다.
 *
 * <p>미리보기는 같은 판정 함수를 쓰되 아무것도 쓰지 않는다 (P4).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaveStgyExecService {

    private final WavStgyRepository wavStgyRepository;
    private final OutbOrderRepository outbOrderRepository;
    private final OutbWaveRepository outbWaveRepository;
    private final NbrService nbrService;
    private final StgyExecLogService stgyExecLogService;

    /**
     * 전략 실행. wavStgyId를 주면 그 전략만(선택실행), 비우면 전 전략을 순서대로(자동실행).
     * 한 트랜잭션이다 — 도중 실패하면 이번 실행의 편성 전체가 롤백된다(부분 편성 없음).
     */
    @Transactional
    public WaveStgyExecResponse execute(WaveStgyExecRequest request) {
        List<WavStgy> strategies = selectStrategies(request.wavStgyId());
        if (strategies.isEmpty()) {
            throw new IllegalArgumentException("실행할 웨이브 전략이 없습니다 — 먼저 전략을 등록하세요.");
        }

        List<OutbOrder> candidates = new ArrayList<>(
                targetOrders(request.expctDeFrom(), request.expctDeTo()));
        int tgtCount = candidates.size();

        List<WaveStgyExecResponse.StgyResult> results = new ArrayList<>();
        int assignedTotal = 0;

        for (WavStgy stgy : strategies) {
            // 앞 전략이 이미 가져간 주문은 candidates에서 빠져 있다 — 선점 규칙이 목록 자체로 표현된다
            List<WaveMatchResult> traces = new ArrayList<>();
            List<OutbOrder> matched = new ArrayList<>();
            for (OutbOrder order : candidates) {
                WaveMatchResult trace = WaveMatcher.evaluate(stgy.getCondGrp(), WaveOrderTarget.from(order));
                traces.add(trace);
                if (trace.matched()) {
                    matched.add(order);
                }
            }

            if (matched.isEmpty()) {
                results.add(new WaveStgyExecResponse.StgyResult(stgy.getId(), stgy.getStgyNm(),
                        stgy.getLastRvsnNo(), null, null, 0, "조건에 맞는 미편성 주문이 없어 웨이브를 만들지 않았습니다."));
                logExec(stgy, null, traces, candidates.size(), 0);
                continue;
            }

            OutbWave wave = outbWaveRepository.save(OutbWave.builder()
                    .wavNo(nbrService.issue("OUTB_WAV_NO", LocalDate.now()))
                    .wavStgyId(stgy.getId())
                    .rvsnNo(stgy.getLastRvsnNo())
                    .build());
            matched.forEach(order -> order.assignWave(wave, WavRegTyp.STGY));
            candidates.removeAll(matched);

            results.add(new WaveStgyExecResponse.StgyResult(stgy.getId(), stgy.getStgyNm(),
                    stgy.getLastRvsnNo(), wave.getId(), wave.getWavNo(), matched.size(), null));
            logExec(stgy, wave.getWavNo(), traces, traces.size(), matched.size());
            assignedTotal += matched.size();
        }
        return new WaveStgyExecResponse(tgtCount, assignedTotal, results);
    }

    /** 미저장 정의로 미리보기 — DB 변경 없음, 실행 로그도 남기지 않는다 (프로세스정의서 §4.4) */
    public WavPreviewResponse preview(WavStgyDefinition definition, WavPreviewRequest request) {
        List<WaveMatchResult> orders = targetOrders(request.expctDeFrom(), request.expctDeTo()).stream()
                .map(order -> WaveMatcher.evaluate(definition.condGrp(), WaveOrderTarget.from(order)))
                .toList();
        return new WavPreviewResponse(orders.size(),
                (int) orders.stream().filter(WaveMatchResult::matched).count(), orders);
    }

    private List<WavStgy> selectStrategies(Long wavStgyId) {
        if (wavStgyId == null) {
            return wavStgyRepository.findAllByOrderByPrtyAscIdAsc();
        }
        return List.of(wavStgyRepository.findById(wavStgyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 웨이브 전략입니다: " + wavStgyId)));
    }

    /**
     * 편성 대상 — 아직 어느 웨이브에도 속하지 않은 CREATED 주문.
     * 취소·할당 이후 주문은 상태 조건에서 자연히 빠진다. 주문 헤더에는 보류·마감 개념이 없으므로
     * 별도 제외 조건이 없다 (보류는 재고 쪽 inv.hld_qty의 개념이다).
     */
    private List<OutbOrder> targetOrders(LocalDate from, LocalDate to) {
        OutbOrderSearchCond cond = new OutbOrderSearchCond();
        cond.setStatus(OutbStatus.CREATED);
        cond.setUnassigned(true);
        cond.setDateFrom(from);
        cond.setDateTo(to);
        return outbOrderRepository.search(cond);
    }

    /** 실행 로그. 웨이브를 안 만든 경우(편입 0건)도 남긴다 — "왜 안 만들어졌나"의 근거 */
    private void logExec(WavStgy stgy, String wavNo, List<WaveMatchResult> traces, int tgtCount, int matchedCount) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("tgtCount", tgtCount);
        trace.put("matchedCount", matchedCount);
        trace.put("orders", traces);
        stgyExecLogService.log(StgyTyp.WAV, stgy.getId(), stgy.getLastRvsnNo(), TrgrTyp.MANUAL,
                wavNo, "대상 " + tgtCount + "건 중 편입 " + matchedCount + "건", trace);
    }
}
