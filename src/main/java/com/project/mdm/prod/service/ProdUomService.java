package com.project.mdm.prod.service;

import com.project.mdm.prod.dto.ProdUomResponse;
import com.project.mdm.prod.dto.ProdUomSaveRequest;
import com.project.mdm.prod.dto.ProdUomSearchCond;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.ProdUom;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.mdm.prod.repository.ProdUomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 단위(상품 포장) 관리. 상품마다 낱개·박스·파렛트를 등록하고 그 낱개수량·중량을 정한다.
 * <p>
 * 상품 저장(ProdService)과 분리돼 있다 — 포장은 상품 한 건에 여러 행이라 상품 그리드 한 줄에
 * 담기지 않고, 낱개수량은 상품 정보를 고칠 때마다 다시 보낼 값도 아니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProdUomService {

    private final ProdUomRepository prodUomRepository;
    private final ProdRepository prodRepository;
    private final ProdUomChangeGuard prodUomChangeGuard;

    public List<ProdUomResponse> list(ProdUomSearchCond cond) {
        return prodUomRepository.search(cond).stream()
                .map(ProdUomResponse::from)
                .toList();
    }

    /**
     * 신규(C)/수정(U)/삭제(D) 행 일괄 저장. 한 건이라도 실패하면 전체 롤백.
     * <p>
     * <b>C·U를 전부 처리한 뒤에 D를 돌린다.</b> 한 저장 안에서 입고단위를 다른 포장으로 옮기면서
     * 원래 포장을 지우는 조작이 자연스러운데, 행 순서대로 처리하면 삭제가 먼저 실행될 때
     * 그 포장이 아직 입고단위라 {@link #delete} 가드에 걸린다.
     * <p>
     * 저장 후 환산 검증은 없다 — 재고 저장 단위가 낱개(EA)로 통일되면서 환산(toEaQty)이
     * 곱셈만 남아, 어떤 포장 조합이어도 수량이 깎일 수 없다 (예전엔 입고단위 낱개수량이
     * 출고단위 낱개수량의 배수인지 마지막에 확인했다).
     */
    @Transactional
    public void saveAll(List<ProdUomSaveRequest> rows) {
        for (ProdUomSaveRequest row : rows) {
            switch (row.getStatus()) {
                case "C" -> create(row);
                case "U" -> update(row);
                case "D" -> { /* 아래에서 따로 처리한다 */ }
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        for (ProdUomSaveRequest row : rows) {
            if ("D".equals(row.getStatus())) {
                delete(row);
            }
        }
        prodUomRepository.flush();
    }

    /**
     * 행이 지정한 역할(입고단위/출고단위)을 상품에 반영한다. true인 행만 본다.
     * 다른 단위로 옮기는 이동이면 환산이 남은 주문이 없는지 먼저 확인한다({@link ProdUomChangeGuard}) —
     * 역할이 옮겨지는 순간 그 주문 수량의 낱개 환산이 새 단위 것으로 바뀐다.
     */
    private void applyRoles(ProdUomSaveRequest row, Prod prod, String uomCd) {
        if (Boolean.TRUE.equals(row.getInbUom())) {
            if (!uomCd.equals(prod.getInbUomCd())) {
                prodUomChangeGuard.requireInbChangeable(prod);
            }
            prod.assignInbUomCd(uomCd);
        }
        if (Boolean.TRUE.equals(row.getOutbUom())) {
            if (!uomCd.equals(prod.getOutbUomCd())) {
                prodUomChangeGuard.requireOutbChangeable(prod);
            }
            prod.assignOutbUomCd(uomCd);
        }
    }

    private Prod create(ProdUomSaveRequest row) {
        Prod prod = findProd(row.getProdId());
        // uq_prod_uom 위반을 커밋 시점 예외가 아니라 여기서 잡는다
        if (prodUomRepository.existsByProdIdAndUomCd(prod.getId(), row.getUomCd())) {
            throw new IllegalArgumentException(
                    "이미 등록된 단위입니다: " + prod.getProdNm() + " / " + row.getUomCd());
        }
        ProdUom uom = row.toEntity(prod);
        applyRoles(row, prod, uom.getUomCd());
        return prod;
    }

    private Prod update(ProdUomSaveRequest row) {
        ProdUom uom = find(row.getProdUomId());
        Prod prod = uom.getProd();
        boolean eaQtyChanges = !uom.getEaQty().equals(row.getEaQty());
        // 반영 뒤에 검사해도 예외면 트랜잭션이 롤백되므로 필드 검사(updateEntity)가 먼저 걸리게 둔다
        row.updateEntity(uom);
        // 입고/출고단위로 쓰이는 포장의 낱개수량 변경은 환산이 남은 주문이 없을 때만 —
        // 주문 수량은 그 단위 기준이라 계수가 바뀌면 확정/검수 때 다른 낱개 수가 된다
        if (eaQtyChanges) {
            if (uom.getUomCd().equals(prod.getInbUomCd())) {
                prodUomChangeGuard.requireInbChangeable(prod);
            }
            if (uom.getUomCd().equals(prod.getOutbUomCd())) {
                prodUomChangeGuard.requireOutbChangeable(prod);
            }
        }
        applyRoles(row, prod, uom.getUomCd());
        return prod;
    }

    /**
     * 물리삭제. 상품의 입고단위·출고단위로 지정된 포장은 지울 수 없다 —
     * 지우면 {@code Prod.eaQtyOf()}가 환산 시점에 예외를 던져 발주 변환이 죽는다.
     * 바꾸려면 상품 화면에서 단위를 먼저 다른 것으로 옮긴다.
     */
    private Prod delete(ProdUomSaveRequest row) {
        ProdUom uom = find(row.getProdUomId());
        Prod prod = uom.getProd();
        // C·U를 먼저 돌린 뒤라 여기서 보는 입고/출고단위는 이미 옮겨진 결과다 —
        // "입고를 BOX로 옮기고 원래 PACK을 삭제"가 한 번에 된다
        if (uom.getUomCd().equals(prod.getInbUomCd())) {
            throw new IllegalArgumentException(
                    "입고단위로 쓰이는 포장은 삭제할 수 없습니다. 입고단위를 다른 포장으로 옮긴 뒤 지우세요: "
                            + prod.getProdNm() + " / " + uom.getUomCd());
        }
        if (uom.getUomCd().equals(prod.getOutbUomCd())) {
            throw new IllegalArgumentException(
                    "출고단위로 쓰이는 포장은 삭제할 수 없습니다. 출고단위를 다른 포장으로 옮긴 뒤 지우세요: "
                            + prod.getProdNm() + " / " + uom.getUomCd());
        }
        prod.removeUom(uom); // orphanRemoval이 DELETE를 낸다
        return prod;
    }

    private Prod findProd(Long prodId) {
        if (prodId == null) {
            throw new IllegalArgumentException("상품은 필수입니다.");
        }
        return prodRepository.findById(prodId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다: " + prodId));
    }

    private ProdUom find(Long prodUomId) {
        if (prodUomId == null) {
            throw new IllegalArgumentException("포장 식별자가 없습니다.");
        }
        return prodUomRepository.findById(prodUomId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 포장입니다: " + prodUomId));
    }
}
