package com.project.wmsback.inventory.service;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.wmsback.inventory.dto.LotAttrChngRequest;
import com.project.wmsback.inventory.dto.LotAttrChngResponse;
import com.project.wmsback.inventory.dto.LotAttrChngSearchCond;
import com.project.wmsback.inventory.dto.LotAttrTargetResponse;
import com.project.wmsback.inventory.dto.LotAttrTargetSearchCond;
import com.project.wmsback.inventory.entity.LotAttrChng;
import com.project.wmsback.inventory.repository.LotAttrChngRepository;
import com.project.wmsback.inventory.repository.LotAttrQueryRepository;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.wmsback.warehouse.repository.LotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 재고 속성변경 — Lot 속성(제조일자·유통기한) 정정.
 *
 * **재고를 한 톨도 움직이지 않는다.** inv·inv_hist 어느 쪽도 건드리지 않고 lot 행만 UPDATE하며,
 * 정정의 원장은 lot_attr_chng 한 테이블이다(수량 변동이 없어 inv_hist에 실을 수 없다 —
 * 보류 실적 테이블과 같은 예외 성격). 재고상태 전환은 재고 보류가, 수량 정정은 재고조사가 담당한다.
 *
 * 이 서비스의 유일한 구조적 위험은 **배치 재사용 키 충돌**이다 — 검수(ReceivingService)는
 * (상품+입고일자+제조일자)가 같으면 기존 Lot을 재사용하는데 제조일자 정정이 그 키를 바꾼다.
 * uq_lot은 (prod_id, lot_no)에만 걸려 DB가 잡아주지 않으므로 여기 검증이 유일한 방어선이고,
 * 동시성은 검수와 같은 락 순서(상품 → Lot)로 막는다.
 *
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LotAttrChngService {

    private static final String LOT_ATTR_RSN_GRP_CD = "LOT_ATTR_RSN";

    private final LotRepository lotRepository;
    private final ProdRepository prodRepository;
    private final LotAttrChngRepository lotAttrChngRepository;
    private final LotAttrQueryRepository lotAttrQueryRepository;
    private final RsnValidator rsnValidator;

    /** 정정 대상 Lot 목록 (영향 범위 = 재고 행 수·보유 합계 포함) */
    public List<LotAttrTargetResponse> listTargets(LotAttrTargetSearchCond cond) {
        return lotAttrQueryRepository.searchTargets(cond);
    }

    /** 정정 이력 조회 (append-only 로그) */
    public List<LotAttrChngResponse> listChngs(LotAttrChngSearchCond cond) {
        return lotAttrChngRepository.search(cond);
    }

    /**
     * Lot 속성 정정. 여러 건의 lot UPDATE + lot_attr_chng INSERT가 **한 트랜잭션**이다 —
     * 한 건이라도 검증에 걸리면 전량 롤백된다. 정정에는 취소 경로가 없어서, 절반만 반영된 결과를
     * 되돌리는 방법이 반대 방향 정정뿐이기 때문이다.
     *
     * Lot 단위 정정이므로 그 Lot을 공유하는 모든 재고 행(로케이션이 달라도)에 일괄 반영된다.
     * 이미 생성된 할당은 건드리지 않는다 — 재검증·재할당 없이 이후 할당부터 새 값이 반영된다.
     */
    @Transactional
    public void change(LotAttrChngRequest request) {
        List<LotAttrChngRequest.Item> items = request.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("정정할 Lot이 없습니다.");
        }

        // 정정 1건이 잠그는 것은 (상품, Lot) 한 쌍인데, 다건이면 한 트랜잭션이 여러 쌍을 동시에 쥔다.
        // 두 요청이 겹치는 Lot을 서로 반대 순서로 보내면 교착이 나므로 상품·Lot 오름차순으로 정렬해
        // 모든 요청이 같은 순서로 잡게 만든다. 상품은 Lot이 정해지면 바뀌지 않아 락 없이 미리 읽어도 되지만,
        // 엔티티가 아니라 스칼라로 읽는다 (LotLockKey 참고 — 미리 올라간 인스턴스는 락을 걸어도 낡은 값이다)
        Set<Long> lotIds = new LinkedHashSet<>();
        for (LotAttrChngRequest.Item item : items) {
            Long lotId = item.getLotId();
            if (lotId == null) {
                throw new IllegalArgumentException("정정할 Lot이 지정되지 않았습니다.");
            }
            // 같은 Lot을 두 번 실으면 뒤가 앞을 덮어쓰면서 이력만 두 줄 남는다 — 애초에 거부한다
            if (!lotIds.add(lotId)) {
                throw new IllegalArgumentException("같은 Lot이 두 번 실렸습니다 — 한 번에 한 값으로만 정정할 수 있습니다: " + lotId);
            }
        }
        Map<Long, Long> prodIdByLotId = new LinkedHashMap<>();
        for (LotLockKey row : lotAttrChngRepository.findLotLockKeysByIdIn(lotIds)) {
            prodIdByLotId.put(row.lotId(), row.prodId());
        }
        for (Long lotId : lotIds) {
            if (!prodIdByLotId.containsKey(lotId)) {
                throw new IllegalArgumentException("존재하지 않는 Lot입니다: " + lotId);
            }
        }

        items.stream()
                .sorted(Comparator
                        .comparing((LotAttrChngRequest.Item it) -> prodIdByLotId.get(it.getLotId()))
                        .thenComparing(LotAttrChngRequest.Item::getLotId))
                .forEach(item -> changeOne(prodIdByLotId.get(item.getLotId()), item));
    }

    /**
     * 정정 1건. 앞 건의 변경은 이 시점에 이미 영속성 컨텍스트에 있어, 배치 재사용 키 검사가
     * 같은 요청 안의 앞 건까지 보고 판단한다 (A→B, B→C처럼 이어지는 정정이 한 번에 통과한다).
     */
    private void changeOne(Long prodId, LotAttrChngRequest.Item item) {
        Long lotId = item.getLotId();
        String rsnDscr = rsnValidator.validate(LOT_ATTR_RSN_GRP_CD, "정정사유", item.getRsnCd(), item.getRsnDscr());

        // 상품 로우 락 → Lot 로우 락. 검수(findOrCreateLot)와 같은 순서라 교착이 없고,
        // 「정정이 배치 키를 X로 바꾸는 사이 검수가 X 배치를 새로 만드는」 경합이 직렬화된다.
        // FK가 없어 상품 행 부재를 DB가 못 잡는다 — 조용히 락 없이 지나가지 않게 정합성 오류로 세운다
        Prod prod = prodRepository.findByIdForUpdate(prodId)
                .orElseThrow(() -> new IllegalStateException("Lot이 참조하는 상품이 없습니다 (정합성 오류): " + prodId));
        Lot lot = lotRepository.findByIdForUpdate(lotId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 Lot입니다: " + lotId));

        // 유통기한 미관리 상품의 Lot은 두 날짜가 항상 NULL인 것이 그 상품의 정의다 —
        // 값을 넣는 것은 정정이 아니라 상품 관리방식 전환이고 그건 상품 마스터의 소관이다.
        if (prod.getShelfLifeDays() == null) {
            throw new IllegalArgumentException("유통기한 미관리 상품의 Lot은 속성 정정 대상이 아닙니다: "
                    + prod.getProdCd() + " / " + lot.getLotNo());
        }

        LocalDate mfgDt = item.getMfgDt();
        LocalDate expiryDt = item.getExpiryDt();
        if (mfgDt == null || expiryDt == null) {
            throw new IllegalArgumentException("제조일자와 유통기한은 모두 필수입니다 (관리 상품의 Lot에서 비울 수 없습니다): "
                    + lot.getLotNo());
        }
        if (lot.getReceiptDt() != null && mfgDt.isAfter(lot.getReceiptDt())) {
            throw new IllegalArgumentException("제조일자가 입고일자보다 미래일 수 없습니다 (입고 " + lot.getReceiptDt() + "): "
                    + lot.getLotNo());
        }
        if (expiryDt.isBefore(mfgDt)) {
            throw new IllegalArgumentException("유통기한이 제조일자보다 이전일 수 없습니다: " + lot.getLotNo());
        }

        LocalDate bfrMfgDt = lot.getMfgDt();
        LocalDate bfrExpiryDt = lot.getExpiryDt();
        if (Objects.equals(bfrMfgDt, mfgDt) && Objects.equals(bfrExpiryDt, expiryDt)) {
            throw new IllegalArgumentException("변경 전후 값이 같습니다 — 정정할 내용이 없습니다: " + lot.getLotNo());
        }

        // 배치 재사용 키(상품+입고일자+제조일자) 충돌 — 겹치면 같은 배치를 가리키는 Lot이 둘이 되어
        // 이후 검수가 어느 쪽을 재사용할지 비결정이 된다. uq_lot은 (prod_id, lot_no)에만 걸려 DB가 안 막는다.
        if (!Objects.equals(bfrMfgDt, mfgDt)) {
            Long conflictLotId = lotRepository
                    .findByProdIdAndReceiptDtAndMfgDt(prod.getId(), lot.getReceiptDt(), mfgDt)
                    .map(Lot::getId)
                    .orElse(null);
            if (conflictLotId != null && !conflictLotId.equals(lot.getId())) {
                throw new IllegalArgumentException("같은 배치(상품+입고일자+제조일자)의 Lot이 이미 있습니다"
                        + " — 그 Lot으로 합치려면 재고 로트변경에서 이 Lot의 각 재고 행을 전량 변경하십시오"
                        + " (제조일자 " + mfgDt + "): " + lot.getLotNo());
            }
        }

        lot.correctAttr(mfgDt, expiryDt);
        lotAttrChngRepository.save(LotAttrChng.builder()
                .lot(lot).prod(prod).lotNo(lot.getLotNo())
                .bfrMfgDt(bfrMfgDt).aftMfgDt(mfgDt)
                .bfrExpiryDt(bfrExpiryDt).aftExpiryDt(expiryDt)
                .rsnCd(item.getRsnCd()).rsnDscr(rsnDscr)
                .build());
    }
}
