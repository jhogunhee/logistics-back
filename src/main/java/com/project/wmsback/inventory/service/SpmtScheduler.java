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
 * 발행은 대상(고정로케이션) 단위로 따로 부른다 — issue는 한 호출이 한 트랜잭션이라, 화면처럼 전부를
 * 한 번에 넘기면 한 자리의 재검증 실패(산정과 발행 사이의 재고 변동, 오염된 자리)가 창고 전체의
 * 보충을 다음 주기까지 막는다. 무인 경로엔 고치고 다시 누를 사람이 없으므로 실패한 자리만 건너뛰고 기록한다.
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
        List<SpmtTargetResponse> targets;
        try {
            targets = spmtService.plan(new SpmtTargetSearchCond());
        } catch (Exception e) {
            // 던지지 않는다 — 스케줄은 계속되고 다음 주기가 다시 시도한다
            log.error("[SPMT] 정기 보충 산정 실패 — 다음 주기에 재시도", e);
            return;
        }

        int issuedTargets = 0;
        int failedTargets = 0;
        List<String> movNos = new ArrayList<>();
        long totalQty = 0;
        for (SpmtTargetResponse target : targets) {
            if (target.assignments().isEmpty()) {
                continue;
            }
            SpmtIssueRequest request = toRequest(target);
            try {
                movNos.addAll(spmtService.issue(request));
                issuedTargets++;
                totalQty += request.getItems().stream().mapToLong(SpmtIssueRequest.Item::getQty).sum();
            } catch (Exception e) {
                failedTargets++;
                log.error("[SPMT] 정기 보충 — {} ({}) 발행 실패, 건너뜀: {}",
                        target.locCd(), target.prodCd(), e.getMessage());
            }
        }

        if (issuedTargets == 0 && failedTargets == 0) {
            log.info("[SPMT] 정기 보충 — 발행할 배정 없음 (미달 {}곳)", targets.size());
            return;
        }
        log.info("[SPMT] 정기 보충 — 미달 {}곳 중 {}곳 발행·{}곳 실패, 지시 {}건 (총 {}개): {}",
                targets.size(), issuedTargets, failedTargets, movNos.size(), totalQty, movNos);
    }

    private static SpmtIssueRequest toRequest(SpmtTargetResponse target) {
        List<SpmtIssueRequest.Item> items = new ArrayList<>();
        for (SpmtTargetResponse.Assignment assignment : target.assignments()) {
            SpmtIssueRequest.Item item = new SpmtIssueRequest.Item();
            item.setInvId(assignment.invId());
            item.setToLocId(target.locId());
            item.setQty(assignment.qty());
            items.add(item);
        }
        SpmtIssueRequest request = new SpmtIssueRequest();
        request.setItems(items);
        return request;
    }
}
