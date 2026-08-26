package com.project.wmsback.inventory.service;

import com.project.wmsback.inventory.dto.InvAlocRecResponse;
import com.project.wmsback.inventory.dto.InvResponse;
import com.project.wmsback.inventory.dto.InvSearchCond;
import com.project.wmsback.inventory.dto.LocMapResponse;
import com.project.wmsback.inventory.repository.InvAlocRecRepository;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.inventory.repository.LocMapQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvService {

    private final InvRepository invRepository;
    private final LocMapQueryRepository locMapQueryRepository;
    private final InvAlocRecRepository invAlocRecRepository;
    private final LocCapacityService locCapacityService;

    public List<InvResponse> list(InvSearchCond cond) {
        return invRepository.search(cond);
    }

    /**
     * 로케이션 점유 맵 — 로케이션 기본행에 재고 합·고정상품 현재고·유입을 얹고 보충 미달을 판정한다.
     * 미달 식은 정기보충 산정(SpmtService.plan)과 같다 — 지정 상품의 현재고 + 유입 < min.
     * 점유율은 프론트 파생
     */
    public List<LocMapResponse> locMap() {
        Map<Long, LocMapQueryRepository.QtySums> qtyByLoc = locMapQueryRepository.qtySumsByLoc();
        Map<Long, Long> fxngOnHandByLoc = locMapQueryRepository.fxngOnHandByLoc();
        Map<ProdLocKey, Long> inflowByProdLoc = locCapacityService.openInflowQtyByProdLoc();
        Map<Long, Long> inflowByLoc = locCapacityService.openInflowQtyByLoc();

        return locMapQueryRepository.locRows().stream()
                .map(row -> {
                    LocMapQueryRepository.QtySums sums =
                            qtyByLoc.getOrDefault(row.locId(), new LocMapQueryRepository.QtySums(0L, 0L, 0L));
                    Long fxngOnHand = null;
                    Long fxngInflow = null;
                    Boolean fxngShort = null;
                    if (row.fxngProdId() != null) {
                        // 고정 자리는 지정 상품 재고·유입이 없어도 0 — 미달 판정의 입력이다
                        fxngOnHand = fxngOnHandByLoc.getOrDefault(row.locId(), 0L);
                        fxngInflow = inflowByProdLoc.getOrDefault(new ProdLocKey(row.fxngProdId(), row.locId()), 0L);
                        long min = row.fxngMinQty() != null ? row.fxngMinQty() : 0L;
                        fxngShort = fxngOnHand + fxngInflow < min;
                    }
                    long inflow = inflowByLoc.getOrDefault(row.locId(), 0L);
                    return new LocMapResponse(row.locId(), row.locCd(), row.zonCd(), row.zonNm(),
                            row.bizDvsn(), row.tmpZon(), row.maxQty(),
                            sums.onHandQty(), sums.alocQty(), sums.hldQty(),
                            inflow, locCapacityService.availCapacity(row.maxQty(), sums.onHandQty() + inflow),
                            row.fxngProdCd(), row.fxngProdNm(), row.fxngProdImgUrl(),
                            row.fxngMinQty(), fxngOnHand, fxngInflow, fxngShort);
                })
                .toList();
    }

    /** 예약 대사 — inv.aloc_qty와 원천별 미소진 합을 재고 키마다 나란히. 어긋난 행(diff ≠ 0)이 곧 예약 잔류·누락이다 */
    public List<InvAlocRecResponse> reconcileAloc() {
        return invAlocRecRepository.reconcile();
    }
}
