package com.project.wmsback.strategy.allocation.service;

import com.project.wmsback.outbound.entity.OutbLine;
import com.project.wmsback.outbound.repository.OutbAllocRepository;
import com.project.wmsback.strategy.allocation.dto.AlocPreviewRequest;
import com.project.wmsback.strategy.allocation.dto.AlocPreviewResponse;
import com.project.wmsback.strategy.allocation.dto.AlocStgyDefinition;
import com.project.wmsback.strategy.allocation.dto.AllocGroupPlan;
import com.project.wmsback.strategy.allocation.field.AllocInvnCandidate;
import com.project.wmsback.strategy.allocation.field.AllocLineTarget;
import com.project.wmsback.strategy.allocation.repository.AllocQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 할당 미리보기 — <b>락도 저장도 하지 않는다.</b> 실전과 같은 산정기({@link AllocationPlanner})를
 * 쓰되 결과를 반영하지 않을 뿐이다 (P4).
 *
 * <p>상품 그룹 분할까지 실전과 같게 맞춘다. 그룹은 락 단위이자 후보 풀 단위라, 미리보기가
 * 라인 전체를 한 덩어리로 계산하면 「같은 상품끼리만 경쟁한다」는 성질이 사라져 결과가 달라진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllocPreviewService {

    private final OutbAllocRepository outbAllocRepository;
    private final AllocQueryRepository allocQueryRepository;

    public AlocPreviewResponse preview(AlocStgyDefinition definition, Long alocStgyId, Long rvsnNo,
                                       AlocPreviewRequest request) {
        List<Long> wavIds = request.wavIds() != null
                ? request.wavIds().stream().filter(java.util.Objects::nonNull).distinct().toList()
                : List.of();
        if (wavIds.isEmpty()) {
            throw new IllegalArgumentException("미리보기할 웨이브를 선택하세요.");
        }
        List<OutbLine> lines = outbAllocRepository.findTargetLines(wavIds);
        if (lines.isEmpty()) {
            return AlocPreviewResponse.of(definition != null ? definition.stgyNm() : null,
                    alocStgyId, rvsnNo, List.of());
        }

        Map<Long, Long> alreadyByLine = outbAllocRepository.sumAlocQtyByLineIds(
                lines.stream().map(OutbLine::getId).toList());

        // 실전과 같은 그룹 분할 — findTargetLines가 prod_id ASC로 주므로 삽입 순서를 지키는 맵이면
        // 그룹 순회도 prod_id ASC다
        Map<Long, List<AllocLineTarget>> byProd = new LinkedHashMap<>();
        Map<Long, String> prodCds = new LinkedHashMap<>();
        for (OutbLine line : lines) {
            Long prodId = line.getProd().getId();
            prodCds.putIfAbsent(prodId, line.getProd().getProdCd());
            byProd.computeIfAbsent(prodId, key -> new ArrayList<>())
                    .add(AllocLineTarget.of(line, alreadyByLine.getOrDefault(line.getId(), 0L)));
        }

        Map<Long, List<AllocInvnCandidate>> candidates =
                allocQueryRepository.candidatesByProd(List.copyOf(byProd.keySet()));

        List<AllocGroupPlan> groups = new ArrayList<>();
        for (Map.Entry<Long, List<AllocLineTarget>> group : byProd.entrySet()) {
            groups.add(AllocationPlanner.plan(definition, group.getKey(), prodCds.get(group.getKey()),
                    group.getValue(), candidates.getOrDefault(group.getKey(), List.of())));
        }
        return AlocPreviewResponse.of(definition != null ? definition.stgyNm() : null,
                alocStgyId, rvsnNo, groups);
    }
}
