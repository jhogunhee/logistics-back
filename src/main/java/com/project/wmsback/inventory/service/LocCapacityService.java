package com.project.wmsback.inventory.service;

import com.project.wmsback.inventory.repository.LocCapacityQueryRepository;
import com.project.wmsback.warehouse.entity.Loc;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 로케이션 적재가능수량의 단일 정의.
 * <p>
 * {@code 적재가능 = max_qty − 현재고 − 미완료 지시 유입 잔량(적치지시 + 이동지시)}
 * <p>
 * 이 식이 이동지시 등록·적치 추천·적치지시 생성 세 곳에서 쓰이는데, 각자 계산하면 한 곳만 고쳐도
 * 나머지가 조용히 어긋난다. 조회는 {@link LocCapacityQueryRepository}가 맡고 여기서는 식만 세운다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocCapacityService {

    private final LocCapacityQueryRepository locCapacityQueryRepository;

    /**
     * 적재가능수량. {@code max_qty}가 없으면 무제한이라는 뜻으로 null을 돌려준다 —
     * STORAGE는 max_qty NOT NULL이 DB 강제이지만(ck_loc_storage_capacity) 강제 이전의 옛 행이 있을 수 있다.
     */
    public Long availCapacity(Loc loc) {
        return availCapacity(loc.getMaxQty(), locCapacityQueryRepository.onHandQty(loc.getId())
                + locCapacityQueryRepository.openInflowQty(loc.getId()));
    }

    /**
     * 사용량을 이미 들고 있는 쪽을 위한 같은 식 — 점유 맵처럼 로케이션 수백 건의 현재고·유입을
     * 한 번에 집계해 둔 자리가 쓴다. 로케이션마다 {@link #availCapacity(Loc)}를 부르면 그대로 N+1이다.
     */
    public Long availCapacity(Long maxQty, long usedQty) {
        if (maxQty == null) {
            return null;
        }
        return Math.max(0, maxQty - usedQty);
    }

    /** 로케이션별 미완료 유입 잔량. 추천이 후보 전체의 용량을 한 번에 계산할 때 쓴다 */
    public Map<Long, Long> openInflowQtyByLoc() {
        return locCapacityQueryRepository.openInflowQtyByLoc();
    }

    /**
     * 여러 로케이션의 적재가능수량을 한 번에. 위 두 오버로드와 <b>같은 식</b>이고 집계 조회만 다르다 —
     * 후보 목록처럼 로케이션 여럿을 한꺼번에 답해야 하는 자리가 {@link #availCapacity(Loc)}를
     * 반복하면 로케이션마다 쿼리 둘이 나가고, DB가 원격이라 그 왕복이 몇 초로 쌓인다.
     */
    public Map<Long, Long> availCapacityByLoc(List<Loc> locs) {
        Map<Long, Long> onHandByLoc = locCapacityQueryRepository.onHandQtyByLoc();
        Map<Long, Long> inflowByLoc = locCapacityQueryRepository.openInflowQtyByLoc();

        Map<Long, Long> byLoc = new HashMap<>();
        for (Loc loc : locs) {
            long used = onHandByLoc.getOrDefault(loc.getId(), 0L) + inflowByLoc.getOrDefault(loc.getId(), 0L);
            // 무제한(max_qty 없음)은 null이라 HashMap에 null 값으로 들어간다 — 부르는 쪽이 그대로 구분한다
            byLoc.put(loc.getId(), availCapacity(loc.getMaxQty(), used));
        }
        return byLoc;
    }

    /**
     * 상품×로케이션별 미완료 유입 잔량. 보충 산정이 고정 상품의 유입만 얹어 판정할 때 쓴다 —
     * 적재가능 식의 유입 항(전 상품)과 달리 상품으로 한 번 더 거른 값이다
     */
    public Map<ProdLocKey, Long> openInflowQtyByProdLoc() {
        return locCapacityQueryRepository.openInflowQtyByProdLoc();
    }

    /** 특정 상품이 특정 로케이션으로 오는 유입 잔량 단건. 보충 발행의 부족량 재검증용 (위 집계와 같은 정의) */
    public long openInflowQty(Long prodId, Long locId) {
        return locCapacityQueryRepository.openInflowQty(prodId, locId);
    }
}
