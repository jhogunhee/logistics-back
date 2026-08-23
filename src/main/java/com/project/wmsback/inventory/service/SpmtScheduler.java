package com.project.wmsback.inventory.service;

import com.project.wmsback.inventory.dto.SpmtIssueRequest;
import com.project.wmsback.inventory.dto.SpmtTargetResponse;
import com.project.wmsback.inventory.dto.SpmtTargetSearchCond;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 정기 보충 스케줄러 — 매일 정해진 시각(spmt.cron, "-"면 비활성)에 산정 결과의 추천 배정을
 * 그대로 발행한다. 화면의 [대상 조회 → 발행]과 완전히 같은 경로(plan → issue)라 검증·예약
 * 규칙이 갈리지 않고, 화면은 임의 시점 재계산·보정용으로 병행된다.
 * <p>
 * 실행 이력은 로그로 남긴다 — 알림 인프라가 없어 실패도 로그가 전부이고, 원천 부족으로 못 채운
 * 잔량은 상태를 남기지 않는다(다음 주기 산정이 유입 잔량을 얹어 다시 잡는다 — 중복 발행 없음).
 * 기본 스케줄러는 단일 스레드라 이전 실행이 끝나기 전에 겹쳐 돌지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SpmtScheduler {

    private static final Logger log = LoggerFactory.getLogger(SpmtScheduler.class);

    private final SpmtService spmtService;

    @Scheduled(cron = "${spmt.cron}")
    public void issueDaily() {
        try {
            List<SpmtTargetResponse> targets = spmtService.plan(new SpmtTargetSearchCond());
            List<SpmtIssueRequest.Item> items = new ArrayList<>();
            for (SpmtTargetResponse target : targets) {
                for (SpmtTargetResponse.Assignment assignment : target.assignments()) {
                    SpmtIssueRequest.Item item = new SpmtIssueRequest.Item();
                    item.setInvId(assignment.invId());
                    item.setToLocId(target.locId());
                    item.setQty(assignment.qty());
                    items.add(item);
                }
            }
            if (items.isEmpty()) {
                log.info("[SPMT] 정기 보충 — 발행할 배정 없음 (미달 {}곳)", targets.size());
                return;
            }
            SpmtIssueRequest request = new SpmtIssueRequest();
            request.setItems(items);
            List<String> movNos = spmtService.issue(request);
            long totalQty = items.stream().mapToLong(SpmtIssueRequest.Item::getQty).sum();
            log.info("[SPMT] 정기 보충 — 대상 {}곳, 지시 {}건 발행 (총 {}개): {}",
                    targets.size(), movNos.size(), totalQty, movNos);
        } catch (Exception e) {
            // 던지지 않는다 — 스케줄은 계속되고, 발행은 전량 롤백이라 다음 주기가 처음부터 다시 시도한다
            log.error("[SPMT] 정기 보충 실패 — 다음 주기에 재시도", e);
        }
    }
}
