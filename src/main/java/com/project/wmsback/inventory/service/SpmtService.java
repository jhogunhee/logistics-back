package com.project.wmsback.inventory.service;

import com.project.wmsback.inventory.dto.InvMovRegisterRequest;
import com.project.wmsback.inventory.dto.SpmtIssueRequest;
import com.project.wmsback.inventory.dto.SpmtTargetResponse;
import com.project.wmsback.inventory.dto.SpmtTargetSearchCond;
import com.project.wmsback.inventory.entity.InvMovDvsn;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.inventory.repository.SpmtQueryRepository;
import com.project.wmsback.inventory.repository.SpmtQueryRepository.SourceRow;
import com.project.wmsback.inventory.repository.SpmtQueryRepository.TargetRow;
import com.project.wmsback.warehouse.entity.FxngLoc;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.repository.FxngLocRepository;
import com.project.wmsback.warehouse.repository.LocRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 정기 보충(SPMT) — 피킹존 고정로케이션(fxng_loc)이 재보충점(min) 미달이면
 * 보관존 재고를 상한(max)까지 채우는 이동지시를 발행한다 (docs/design.md 「고정 로케이션 마스터」).
 * <p>
 * 산정(plan)은 조회 전용 추천이다 — 추천은 예약이 아니라서 발행(issue)이 같은 식으로 재검증한다
 * (적치 추천의 2회 검증과 같은 구조). 지시 자체는 {@link InvMovService#register}에 SPMT 유형으로
 * 위임해 예약·확정·용량 합산 인프라를 그대로 쓴다 — 고정로케이션 지식은 이 서비스 밖으로 내보내지 않는다.
 * 화면 없이 부를 수 있는 시그니처라 나중에 스케줄러가 plan→issue를 그대로 재사용할 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpmtService {

    private final SpmtQueryRepository spmtQueryRepository;
    private final LocCapacityService locCapacityService;
    private final InvMovService invMovService;
    private final FxngLocRepository fxngLocRepository;
    private final LocRepository locRepository;
    private final InvRepository invRepository;

    /**
     * 보충 대상 산정 — 현재고+미완료 유입이 min 미달인 고정로케이션과 FEFO 추천 배정.
     * 유입 잔량을 얹는 이유는 이미 지시를 걸어 둔 자리가 다시 대상으로 잡히지 않게 하려는 것.
     * 출발 재고 가용은 대상 간 전역 선점한다 — 같은 상품의 여러 대상이 같은 재고를 이중 배정하지 않게
     * (적치 일괄 추천의 배치 간 용량 공유와 같은 원리).
     */
    public List<SpmtTargetResponse> plan(SpmtTargetSearchCond cond) {
        List<TargetRow> rows = spmtQueryRepository.targets(cond);
        // 유입도 현재고처럼 고정 상품만 — 전용 자리로 오는 타상품 지시까지 얹으면 진짜 부족한 자리가 가려진다
        Map<ProdLocKey, Long> inflowByProdLoc = locCapacityService.openInflowQtyByProdLoc();
        // 물리 적재가능은 전 상품 기준(등록 창구의 용량 검증과 같은 식) — 배정을 여기까지로 잘라 발행에서 걸릴 추천을 내지 않는다
        Map<Long, Long> inflowByLoc = locCapacityService.openInflowQtyByLoc();

        List<TargetRow> shorts = new ArrayList<>();
        Set<Long> prodIds = new LinkedHashSet<>();
        for (TargetRow row : rows) {
            long inflow = inflowOf(inflowByProdLoc, row);
            if (row.onHandQty() + inflow < row.minQty()) {
                shorts.add(row);
                prodIds.add(row.prodId());
            }
        }

        Map<Long, List<SourceRow>> sourcesByProd = new LinkedHashMap<>();
        for (SourceRow source : spmtQueryRepository.sources(prodIds)) {
            sourcesByProd.computeIfAbsent(source.prodId(), key -> new ArrayList<>()).add(source);
        }

        Map<Long, Long> remainingByInv = new HashMap<>(); // 전역 선점 잔여
        List<SpmtTargetResponse> result = new ArrayList<>();
        for (TargetRow row : shorts) {
            long inflow = inflowOf(inflowByProdLoc, row);
            long shortQty = row.maxQty() - row.onHandQty() - inflow;
            List<SourceRow> sources = sourcesByProd.getOrDefault(row.prodId(), List.of());

            List<SpmtTargetResponse.Assignment> assignments = new ArrayList<>();
            long need = shortQty;
            if (row.locMaxQty() != null) { // null = max_qty 미설정(옛 행) → 물리 상한 없음 (LocCapacityService와 같은 해석)
                long physicalRoom = row.locMaxQty() - row.locOnHandQty() - inflowByLoc.getOrDefault(row.locId(), 0L);
                need = Math.max(0, Math.min(need, physicalRoom));
            }
            for (SourceRow source : sources) { // FEFO 순 — 쿼리가 정렬해 온다
                if (need == 0) {
                    break;
                }
                long remaining = remainingByInv.getOrDefault(source.invId(), source.avalQty());
                long assign = Math.min(remaining, need);
                if (assign == 0) {
                    continue;
                }
                remainingByInv.put(source.invId(), remaining - assign);
                need -= assign;
                assignments.add(new SpmtTargetResponse.Assignment(
                        source.invId(), source.fromLocCd(), source.lotNo(), source.expiryDt(),
                        source.avalQty(), assign));
            }

            result.add(new SpmtTargetResponse(
                    row.fxngLocId(), row.locId(), row.locCd(), row.zonCd(),
                    row.prodId(), row.prodCd(), row.prodNm(), row.tmpZon(),
                    row.minQty(), row.maxQty(), row.onHandQty(), inflow, shortQty,
                    assignments,
                    sources.stream()
                            .map(s -> new SpmtTargetResponse.Source(
                                    s.invId(), s.fromLocCd(), s.lotNo(), s.expiryDt(), s.avalQty()))
                            .toList()));
        }
        return result;
    }

    private static long inflowOf(Map<ProdLocKey, Long> inflowByProdLoc, TargetRow row) {
        return inflowByProdLoc.getOrDefault(new ProdLocKey(row.prodId(), row.locId()), 0L);
    }

    /**
     * 보충지시 발행 — 전체가 한 트랜잭션, 한 건이라도 검증에 걸리면 전량 롤백.
     * 도착 로케이션을 id 오름차순으로 선락한 뒤(등록 창구의 1단계 락과 같은 자리·같은 순서)
     * SPMT 고유 검증(고정 등재·상품 일치·부족량 상한)을 하고, 나머지 검증·예약·채번은
     * {@link InvMovService#register}가 맡는다.
     *
     * @return 발급된 보충지시 번호 목록
     */
    @Transactional
    public List<String> issue(SpmtIssueRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("보충지시 대상이 없습니다.");
        }
        Set<Long> toLocIds = new TreeSet<>();
        Set<Long> invIds = new LinkedHashSet<>();
        for (SpmtIssueRequest.Item item : request.getItems()) {
            if (item.getToLocId() == null) {
                throw new IllegalArgumentException("보충할 고정로케이션이 지정되지 않았습니다.");
            }
            if (item.getInvId() == null) {
                throw new IllegalArgumentException("보충 원천 재고가 지정되지 않았습니다.");
            }
            if (item.getQty() == null || item.getQty() < 1) {
                throw new IllegalArgumentException("보충수량은 1 이상이어야 합니다.");
            }
            toLocIds.add(item.getToLocId());
            invIds.add(item.getInvId());
        }

        // 도착 로케이션 선락 — 부족량 재검증의 직렬화 지점. 같은 자리로 향하는 두 발행이
        // 서로의 유입을 못 본 채 둘 다 통과하면 합산이 max를 넘긴다 (등록 창구의 용량 검증과 같은 원리)
        Map<Long, FxngLoc> fxngByLocId = new HashMap<>();
        for (Long toLocId : toLocIds) {
            Loc toLoc = locRepository.findByIdForUpdate(toLocId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로케이션입니다: " + toLocId));
            fxngByLocId.put(toLocId, fxngLocRepository.findByLoc(toLoc)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "고정 로케이션 마스터에 등재되지 않은 로케이션입니다: " + toLoc.getLocCd())));
        }

        // 상품 일치 — 전용 자리에 다른 상품을 채우는 발행을 막는다 (원천 존재·가용은 등록 창구가 검증).
        // 원천은 스칼라 키로만 본다 — 여기서 Inv 엔티티를 올리면 등록 창구의 락 재조회가 그 인스턴스를
        // 그대로 돌려줘 락 이전 가용수량으로 검증하게 된다 (InvLockKey 참고)
        Map<Long, InvKey> keyByInvId = new HashMap<>();
        for (InvLockKey row : invRepository.findLockKeysByIdIn(invIds)) {
            keyByInvId.put(row.id(), row.key());
        }
        // 원천 제외 — 고정 등재 자리의 재고는 상품 불문 원천이 아니다 (산정의 제외 규칙을 발행이 다시 본다).
        // 화면이 원천 보정을 허용하므로 다른 피킹면을 헐어 채우는 요청이 올 수 있고, 통과시키면 그 자리가 다음 주기 대상이 된다
        Set<Long> sourceLocIds = new HashSet<>();
        keyByInvId.values().forEach(key -> sourceLocIds.add(key.locId()));
        Set<Long> fxngSourceLocIds = sourceLocIds.isEmpty()
                ? Set.of() : fxngLocRepository.findLocIdsByLocIdIn(sourceLocIds);

        Map<Long, Long> qtyByLocId = new HashMap<>();
        for (SpmtIssueRequest.Item item : request.getItems()) {
            FxngLoc fxng = fxngByLocId.get(item.getToLocId());
            InvKey key = keyByInvId.get(item.getInvId());
            if (key == null) {
                throw new IllegalArgumentException("존재하지 않는 재고입니다: " + item.getInvId());
            }
            if (fxngSourceLocIds.contains(key.locId())) {
                String locCd = locRepository.findById(key.locId()).map(Loc::getLocCd).orElse("#" + key.locId());
                throw new IllegalArgumentException("고정로케이션에 등재된 자리의 재고는 보충 원천이 될 수 없습니다 (재고 #"
                        + item.getInvId() + " @ " + locCd + ")");
            }
            if (!key.prodId().equals(fxng.getProd().getId())) {
                throw new IllegalArgumentException("고정 상품과 다른 상품의 재고입니다 (고정 "
                        + fxng.getProd().getProdCd() + " / 재고 #" + item.getInvId() + ")");
            }
            qtyByLocId.merge(item.getToLocId(), item.getQty(), Long::sum);
        }

        // 부족량 재검증 (산정과 같은 식 — 2회 검증). 초과 보충과 중복 발행을 여기서 자른다
        for (Map.Entry<Long, Long> entry : qtyByLocId.entrySet()) {
            FxngLoc fxng = fxngByLocId.get(entry.getKey());
            Long prodId = fxng.getProd().getId();
            long shortQty = fxng.getMaxQty()
                    - spmtQueryRepository.prodOnHandQty(prodId, entry.getKey())
                    - locCapacityService.openInflowQty(prodId, entry.getKey());
            if (entry.getValue() > shortQty) {
                throw new IllegalArgumentException("보충수량이 부족량을 초과했습니다 (부족 "
                        + Math.max(shortQty, 0) + "): " + fxng.getProd().getProdCd());
            }
        }

        InvMovRegisterRequest delegate = new InvMovRegisterRequest();
        delegate.setItems(request.getItems().stream().map(item -> {
            InvMovRegisterRequest.Item moved = new InvMovRegisterRequest.Item();
            moved.setInvId(item.getInvId());
            moved.setToLocId(item.getToLocId());
            moved.setQty(item.getQty());
            return moved;
        }).toList());
        return invMovService.register(delegate, InvMovDvsn.SPMT);
    }
}
