package com.project.wmsback.outbound.service;

import com.project.wmsback.outbound.entity.OutbAlloc;
import com.project.wmsback.warehouse.entity.BizDvsn;
import com.project.wmsback.warehouse.entity.FxngLoc;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.inventory.service.LocCapacityService;
import com.project.wmsback.warehouse.repository.FxngLocRepository;
import com.project.wmsback.warehouse.repository.LocRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 수시보충 도착지 결정 — 보관존에 잡힌 할당분을 어느 피킹존 로케이션으로 옮길지.
 *
 * <p>순서는 셋이고 각 단계 안에서는 여유(적재가능수량)가 큰 곳부터다:
 * ① 상품의 고정 로케이션({@code fxng_loc}) ② 같은 상품이 이미 있는 피킹존 로케이션 ③ 빈 피킹존 로케이션
 * (다른 상품의 고정 로케이션은 제외). 셋 다 없거나 여유가 모자라면 도착지 없음 — 그 할당은 발행에서
 * 빠지고 화면이 「피킹 로케이션 없음」으로 보여 준다(웨이브 통째 차단 아님).
 *
 * <p>후보 로케이션은 <b>id 오름차순으로 잠근 뒤</b> 여유를 읽는다. 적재가능수량은 락 없는 집계라,
 * 잠그지 않으면 같은 도착지로 향하는 두 발행이 서로의 유입을 못 본 채 합산이 {@code max_qty}를
 * 넘긴다(이동지시 등록이 도착 loc를 선락하는 것과 같은 이유). 발행은 이 앞에서 웨이브 락만 쥐고
 * 있어 전역 락 계층(웨이브 → loc → inv)을 그대로 따른다. 재고 행은 잠그지 않는다 — 보충지시는
 * 예약을 잡지 않는다(할당이 든다).
 *
 * <p>한 할당분을 로케이션 둘로 쪼개지 않는다 — 할당 행 하나는 재고 행 하나를 가리킨다.
 */
@Component
@RequiredArgsConstructor
public class RplnDestinationResolver {

    private final LocRepository locRepository;
    private final FxngLocRepository fxngLocRepository;
    private final LocCapacityService locCapacityService;

    /** 피킹존 판정. 존이 없는 로케이션은 피킹존이 아니다 — 거기 잡힌 할당은 보충 대상이다 */
    public static boolean inPikngZon(Loc loc) {
        return loc.getZon() != null && loc.getZon().getBizDvsn() == BizDvsn.PIKNG;
    }

    /**
     * 발행 대상 할당의 도착지. 보관존 할당만 판정 대상이고(피킹존 할당은 그 자리에서 집는다),
     * 같은 상품의 할당 여럿은 같은 후보를 두고 여유를 누적해 가며 배정된다.
     */
    public Destinations resolve(List<OutbAlloc> allocs) {
        List<OutbAlloc> storageAllocs = allocs.stream()
                .filter(alloc -> !inPikngZon(alloc.getInv().getLoc()))
                .toList();
        Map<Long, Loc> result = new LinkedHashMap<>();
        if (storageAllocs.isEmpty()) {
            return new Destinations(result);
        }

        // ① 상품별 후보 (세 단계 순서 그대로, 중복 로케이션은 앞 단계 것만)
        Map<Long, List<Candidate>> candidatesByProd = new LinkedHashMap<>();
        for (OutbAlloc alloc : storageAllocs) {
            Prod prod = alloc.getInv().getProd();
            candidatesByProd.computeIfAbsent(prod.getId(), id -> candidates(prod));
        }

        // ② 후보 로케이션 선락 — id 오름차순
        Set<Long> locIds = new TreeSet<>();
        candidatesByProd.values().forEach(list -> list.forEach(c -> locIds.add(c.loc().getId())));
        for (Long locId : locIds) {
            locRepository.findByIdForUpdate(locId);
        }

        // ③ 여유 읽기 (락 뒤) + 단계 안에서 여유 큰 순 정렬
        Map<Long, Long> capacityByLoc = new HashMap<>();
        for (List<Candidate> list : candidatesByProd.values()) {
            for (Candidate c : list) {
                capacityByLoc.computeIfAbsent(c.loc().getId(), id -> capacity(c));
            }
            list.sort(Comparator.comparingInt(Candidate::tier)
                    .thenComparing(c -> capacityByLoc.get(c.loc().getId()),
                            Comparator.nullsFirst(Comparator.<Long>reverseOrder())));
        }

        // ④ 배정 — 할당 id 순. 이번 발행에서 먼저 배정한 몫(planned)을 여유에서 뺀다
        Map<Long, Long> planned = new HashMap<>();
        List<OutbAlloc> ordered = new ArrayList<>(storageAllocs);
        ordered.sort(Comparator.comparing(OutbAlloc::getId));
        for (OutbAlloc alloc : ordered) {
            long qty = alloc.getAlocQty();   // 지시수량과 같다 — 발행 대상 할당은 아직 집힌 것이 없다
            Loc chosen = null;
            for (Candidate c : candidatesByProd.get(alloc.getInv().getProd().getId())) {
                Long capacity = capacityByLoc.get(c.loc().getId());
                long used = planned.getOrDefault(c.loc().getId(), 0L);
                if (capacity == null || capacity - used >= qty) {
                    chosen = c.loc();
                    planned.merge(c.loc().getId(), qty, Long::sum);
                    break;
                }
            }
            result.put(alloc.getId(), chosen);
        }
        return new Destinations(result);
    }

    /**
     * 판정 결과. 보관존 할당만 키로 들어 있고, 값이 null이면 「도착지 없음」이다.
     * 피킹존 할당은 키가 없고 {@link #pickLocOf}가 할당의 자리 그대로를 돌려준다.
     */
    public record Destinations(Map<Long, Loc> byAlloc) {

        /** 보관존 할당인데 옮겨 둘 자리가 없다 — 이번 발행에서 빠진다 */
        public boolean unresolved(OutbAlloc alloc) {
            return byAlloc.containsKey(alloc.getId()) && byAlloc.get(alloc.getId()) == null;
        }

        /** 보충 도착지. 보충이 필요 없는(피킹존) 할당이면 null */
        public Loc replenishTo(OutbAlloc alloc) {
            return byAlloc.get(alloc.getId());
        }

        /** 작업자가 집으러 가는 곳 — 보충분은 도착지, 피킹존 할당은 그 자리 */
        public Loc pickLocOf(OutbAlloc alloc) {
            Loc dest = replenishTo(alloc);
            return dest != null ? dest : alloc.getInv().getLoc();
        }
    }

    private List<Candidate> candidates(Prod prod) {
        Set<Long> seen = new LinkedHashSet<>();
        List<Candidate> list = new ArrayList<>();
        for (FxngLoc fxng : fxngLocRepository.findAllWithLocByProdId(prod.getId())) {
            if (seen.add(fxng.getLoc().getId())) {
                list.add(new Candidate(1, fxng.getLoc(), fxng.getMaxQty()));
            }
        }
        for (Loc loc : locRepository.findPikngLocsHoldingProd(prod.getId(), LocTyp.STORAGE, BizDvsn.PIKNG)) {
            if (seen.add(loc.getId())) {
                list.add(new Candidate(2, loc, null));
            }
        }
        for (Loc loc : locRepository.findEmptyPikngLocs(prod.getTmpZon(), LocTyp.STORAGE, BizDvsn.PIKNG)) {
            if (seen.add(loc.getId())) {
                list.add(new Candidate(3, loc, null));
            }
        }
        return list;
    }

    /**
     * 여유. null = 무제한(max_qty 미설정 옛 행). 고정 로케이션은 {@code fxng_loc.max_qty}가 상한이라
     * loc 기준 여유에서 그 차이만큼 더 뺀다 — 유입 집계는 LocCapacityService 하나를 그대로 쓴다.
     */
    private Long capacity(Candidate c) {
        Long base = locCapacityService.availCapacity(c.loc());
        if (base == null || c.fxngMaxQty() == null || c.loc().getMaxQty() == null) {
            return base;
        }
        return Math.max(0, base - (c.loc().getMaxQty() - c.fxngMaxQty()));
    }

    private record Candidate(int tier, Loc loc, Long fxngMaxQty) {
    }
}
