package com.project.wmsback.master.service;

import com.project.wmsback.master.dto.ProdUomResponse;
import com.project.wmsback.master.dto.ProdUomSaveRequest;
import com.project.wmsback.master.dto.ProdUomSearchCond;
import com.project.wmsback.master.entity.Prod;
import com.project.wmsback.master.entity.ProdUom;
import com.project.wmsback.master.repository.ProdRepository;
import com.project.wmsback.master.repository.ProdUomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
     * 마지막에 손댄 상품마다 환산 전제를 다시 확인한다 — 역할 지정과 낱개수량 수정이
     * 이 화면에서 함께 일어나므로 둘을 합친 결과가 성립하는지는 끝나봐야 안다.
     */
    @Transactional
    public void saveAll(List<ProdUomSaveRequest> rows) {
        Set<Prod> touched = new LinkedHashSet<>();

        for (ProdUomSaveRequest row : rows) {
            switch (row.getStatus()) {
                case "C" -> touched.add(create(row));
                case "U" -> touched.add(update(row));
                case "D" -> { /* 아래에서 따로 처리한다 */ }
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        for (ProdUomSaveRequest row : rows) {
            if ("D".equals(row.getStatus())) {
                touched.add(delete(row));
            }
        }

        touched.forEach(this::validateConversion);
        prodUomRepository.flush();
    }

    /**
     * 입고단위 낱개수량이 출고단위 낱개수량으로 나누어떨어지는지. 안 떨어지면
     * {@link Prod#toOutbQty}의 정수 나눗셈이 발주 → ASN 변환에서 수량을 조용히 깎는다.
     * (예: 입고 BOX 24, 출고 PACK 5 → 24 / 5 = 4, 낱개 4개가 증발한다)
     */
    private void validateConversion(Prod prod) {
        long inbEaQty = prod.eaQtyOf(prod.getInbUomCd());
        long outbEaQty = prod.eaQtyOf(prod.getOutbUomCd());
        if (inbEaQty % outbEaQty != 0) {
            throw new IllegalArgumentException(
                    "입고단위 낱개수량(%d)은 출고단위 낱개수량(%d)의 배수여야 합니다: %s"
                            .formatted(inbEaQty, outbEaQty, prod.getProdNm()));
        }
    }

    /** 행이 지정한 역할(입고단위/출고단위)을 상품에 반영한다. true인 행만 본다 */
    private void applyRoles(ProdUomSaveRequest row, Prod prod, String uomCd) {
        if (Boolean.TRUE.equals(row.getInbUom())) {
            prod.assignInbUomCd(uomCd);
        }
        if (Boolean.TRUE.equals(row.getOutbUom())) {
            prod.assignOutbUomCd(uomCd);
        }
    }

    private Prod create(ProdUomSaveRequest row) {
        if (row.getProdId() == null) {
            throw new IllegalArgumentException("상품은 필수입니다.");
        }
        if (row.getUomCd() == null || row.getUomCd().isBlank()) {
            throw new IllegalArgumentException("단위는 필수입니다.");
        }
        Prod prod = prodRepository.findById(row.getProdId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다: " + row.getProdId()));
        // uq_prod_uom 위반을 커밋 시점 예외가 아니라 여기서 잡는다
        if (prodUomRepository.existsByProdIdAndUomCd(prod.getId(), row.getUomCd())) {
            throw new IllegalArgumentException(
                    "이미 등록된 단위입니다: " + prod.getProdNm() + " / " + row.getUomCd());
        }
        validateQty(row, prod.getProdNm() + " / " + row.getUomCd());

        prod.addUom(ProdUom.builder()
                .uomCd(row.getUomCd())
                .eaQty(row.getEaQty())
                .wgt(row.getWgt())
                .build());
        applyRoles(row, prod, row.getUomCd());
        return prod;
    }

    private Prod update(ProdUomSaveRequest row) {
        ProdUom uom = find(row.getProdUomId());
        Prod prod = uom.getProd();
        validateQty(row, prod.getProdNm() + " / " + uom.getUomCd());
        uom.update(row.getEaQty(), row.getWgt());
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

    private ProdUom find(Long prodUomId) {
        if (prodUomId == null) {
            throw new IllegalArgumentException("포장 식별자가 없습니다.");
        }
        return prodUomRepository.findById(prodUomId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 포장입니다: " + prodUomId));
    }

    private void validateQty(ProdUomSaveRequest row, String label) {
        // 낱개수량이 0이나 음수면 환산이 수량을 0으로 만들거나 부호를 뒤집는다
        if (row.getEaQty() == null || row.getEaQty() < 1) {
            throw new IllegalArgumentException("낱개수량은 1 이상이어야 합니다: " + label);
        }
        // 미측정(NULL)은 허용하되 0이나 음수 중량은 실측값일 수 없다
        if (row.getWgt() != null && row.getWgt().signum() <= 0) {
            throw new IllegalArgumentException("중량은 비워두거나(미측정) 0보다 커야 합니다: " + label);
        }
    }
}
