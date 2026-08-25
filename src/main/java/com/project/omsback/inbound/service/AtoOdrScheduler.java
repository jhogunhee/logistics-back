package com.project.omsback.inbound.service;

import com.project.common.batch.BatchResult;
import com.project.omsback.inbound.dto.AtoOdrIssueRequest;
import com.project.omsback.inbound.dto.AtoOdrProposalResponse;
import com.project.omsback.inbound.dto.AtoOdrSearchCond;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 자동발주 스케줄러 — 매일 정해진 시각(ato-odr.cron, "-"면 비활성)에 산정 결과를 그대로 발행한다.
 * 화면의 [산정 → 발주 생성]과 완전히 같은 경로(plan → issueAll)라 검증이 갈리지 않고, 화면은
 * 임의 시점 재계산·수량 보정용으로 병행된다 (정기보충 스케줄러와 같은 구조).
 * <p>
 * 만들어지는 것은 <b>작성(CREATED) 상태 입고주문</b>까지다 — 확정(→ASN)은 사람이 누른다.
 * 무인 경로가 창고에 「온다」고 알리는 데까지 가면 기준값이 틀렸을 때 오지 않을 물건을 기다리게 된다.
 * <p>
 * 트랜잭션은 벤더 단위다(issueAll → BatchExecutor) — 한 벤더가 걸려도 나머지 벤더는 나간다.
 * 실행 이력은 로그가 전부이고(알림 인프라 없음), 발행하지 못한 부족분은 상태를 남기지 않는다 —
 * 다음 주기 산정이 미확정 발주를 순재고에 얹어 다시 잡으므로 중복 발주가 나지 않는다.
 * 기본 스케줄러는 단일 스레드라 이전 실행이 끝나기 전에 겹쳐 돌지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AtoOdrScheduler {

    private static final Logger log = LoggerFactory.getLogger(AtoOdrScheduler.class);

    private final AtoOdrService atoOdrService;

    @Scheduled(cron = "${ato-odr.cron}")
    public void issueDaily() {
        List<AtoOdrProposalResponse> proposals;
        try {
            proposals = atoOdrService.plan(new AtoOdrSearchCond());
        } catch (Exception e) {
            // 던지지 않는다 — 스케줄은 계속되고 다음 주기가 다시 시도한다
            log.error("[ATO] 자동발주 산정 실패 — 다음 주기에 재시도", e);
            return;
        }

        if (proposals.isEmpty()) {
            log.info("[ATO] 자동발주 — 발주점 미달 상품 없음");
            return;
        }

        List<AtoOdrIssueRequest> requests = proposals.stream().map(AtoOdrScheduler::toRequest).toList();
        BatchResult result = atoOdrService.issueAll(requests);

        int shortLines = proposals.stream().mapToInt(p -> p.lines().size()).sum();
        log.info("[ATO] 자동발주 — 부족 상품 {}건 · 벤더 {}곳 중 {}곳 발행·{}곳 실패",
                shortLines, proposals.size(), result.succeeded().size(), result.failed().size());
        result.failed().forEach(failure ->
                log.error("[ATO] 자동발주 — 벤더 {} 발행 실패, 건너뜀: {}", failure.id(), failure.reason()));
    }

    private static AtoOdrIssueRequest toRequest(AtoOdrProposalResponse proposal) {
        List<AtoOdrIssueRequest.Item> items = new ArrayList<>();
        for (AtoOdrProposalResponse.Line line : proposal.lines()) {
            AtoOdrIssueRequest.Item item = new AtoOdrIssueRequest.Item();
            item.setProdId(line.prodId());
            item.setOdrQty(line.odrQty());
            items.add(item);
        }
        AtoOdrIssueRequest request = new AtoOdrIssueRequest();
        request.setVendorId(proposal.vendorId());
        request.setExpctDe(proposal.expctDe());
        request.setItems(items);
        return request;
    }
}
