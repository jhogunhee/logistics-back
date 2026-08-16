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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 단위(상품 포장) 관리. 상품마다 낱개·박스·파렛트를 등록하고 그 낱개수량·중량을 정한다.
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
     * 행은 온 순서대로 처리하고, <b>입고단위·출고단위가 가리키는 포장이 남아 있는지는 손댄 상품마다
     * 마지막에 한 번 검사한다</b>({@link Prod#requireRoleUoms}).
     */
    @Transactional
    public void saveAll(List<ProdUomSaveRequest> rows) {
        Set<Prod> touched = new LinkedHashSet<>();
        for (ProdUomSaveRequest row : rows) {
            touched.add(switch (row.getStatus()) {
                case "C" -> create(row);
                case "U" -> update(row);
                case "D" -> delete(row);
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            });
        }
        touched.forEach(Prod::requireRoleUoms);
        prodUomRepository.flush();
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

    /**
     * 물리삭제. 상품의 입고단위·출고단위로 지정된 포장은 지울 수 없는데, 그 검사는 여기가 아니라
     * {@link #saveAll} 끝에서 한다({@link Prod#requireRoleUoms}) — 같은 저장 안에서 역할을
     * 다른 포장으로 옮기는 행이 이 행 뒤에 올 수 있다.
     */
    private Prod delete(ProdUomSaveRequest row) {
        ProdUom uom = find(row.getProdUomId());
        Prod prod = uom.getProd();
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
