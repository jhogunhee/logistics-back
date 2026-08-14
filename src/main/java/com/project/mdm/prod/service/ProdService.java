package com.project.mdm.prod.service;

import com.project.mdm.nbr.service.NbrService;
import com.project.mdm.prod.dto.ProdResponse;
import com.project.mdm.prod.dto.ProdSaveRequest;
import com.project.mdm.prod.dto.ProdSearchCond;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.ProdUom;
import com.project.mdm.prod.repository.ProdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProdService {

    private final ProdRepository prodRepository;
    private final NbrService nbrService;
    /** 상품을 참조하는 앱들의 신고 창구. @Order 순으로 주입된다 (WMS → OMS) */
    private final List<ProdRefChecker> prodRefCheckers;

    public List<ProdResponse> list(ProdSearchCond cond) {
        return prodRepository.search(cond).stream()
                .map(ProdResponse::from)
                .toList();
    }

    /** 신규(C)/수정(U)/삭제(D) 행 일괄 저장. 한 건이라도 실패하면 전체 롤백. */
    @Transactional
    public void saveAll(List<ProdSaveRequest> rows) {
        for (ProdSaveRequest row : rows) {
            switch (row.getStatus()) {
                case "C" -> { validate(row); create(row); }
                case "U" -> { validate(row); update(row); }
                case "D" -> delete(row);
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        // 제약 위반(코드 중복 등)을 커밋 시점이 아니라 여기서 터뜨려 예외 변환이 되게 한다
        prodRepository.flush();
    }

    private void create(ProdSaveRequest row) {
        // 클라이언트가 보낸 코드는 받지 않는다 — 채번 규칙 PROD_CD로 발급 (PROD-0001 형식)
        String prodCd = nbrService.issue("PROD_CD");
        // 저장을 바로 하지 않고 변수로 받는 이유는 ensureUoms 때문이다 — 포장을 붙인 뒤
        // 한 번에 저장해야 cascade가 상품과 포장을 같이 넣는다.
        Prod prod = Prod.builder()
                .prodCd(prodCd)
                .prodNm(row.getProdNm())
                .tmpZon(row.getTmpZon())
                .inbUomCd(row.getInbUomCd())
                .outbUomCd(row.getOutbUomCd())
                .shelfLifeDays(row.getShelfLifeDays())
                .build();
        ensureUoms(prod);
        prodRepository.save(prod); // cascade로 포장까지 함께 저장
    }

    private void update(ProdSaveRequest row) {
        Prod prod = prodRepository.findById(row.getProdId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다: " + row.getProdId()));
        prod.update(row.getProdNm(), row.getTmpZon(),
                row.getInbUomCd(), row.getOutbUomCd(), row.getShelfLifeDays());
        ensureUoms(prod);
    }

    /**
     * 입고단위·출고단위의 포장 행을 보장한다. 없으면 낱개수량 1로 만든다 —
     * {@link Prod#eaQtyOf}가 포장 없는 단위에서 예외를 던지므로 상품만 저장하고 끝내면
     * 그 상품은 조회도 발주 변환도 되지 않는다. 실제 입수량(BOX 24 등)은 단위 관리 화면에서 넣는다.
     * <p>
     * 나머지 포장(파렛트 등)은 여기서 건드리지 않는다 — 상품 저장은 포장 목록을 받지 않는다.
     */
    private void ensureUoms(Prod prod) {
        ensureUom(prod, prod.getOutbUomCd());
        ensureUom(prod, prod.getInbUomCd());
        // 나누어떨어짐 검증은 없다 — 재고 저장 단위가 낱개(EA)로 통일되면서 환산(toEaQty)이
        // 곱셈만 남아, 어떤 포장 조합이어도 수량이 깎일 수 없다.
    }

    private void ensureUom(Prod prod, String uomCd) {
        boolean exists = prod.getUoms().stream().anyMatch(u -> u.getUomCd().equals(uomCd));
        if (!exists) {
            prod.addUom(ProdUom.builder().uomCd(uomCd).eaQty(1L).build());
        }
    }

    /**
     * 물리삭제. 포장은 cascade로 함께 사라지지만, 재고·이력·문서가 참조 중이면 거부한다 —
     * FK가 0건이라 DB가 막아주지 않아서 그냥 지우면 그 행들이 없는 상품을 가리키게 되고
     * 조회에서 상품명이 빈 채로 남는다. 되살릴 방법도 없다.
     * <p>
     * 참조 검사는 {@link ProdRefChecker} 구현체가 한다 — mdm은 자기 데이터를 누가 쓰는지 모른다.
     */
    private void delete(ProdSaveRequest row) {
        Prod prod = prodRepository.findById(row.getProdId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다: " + row.getProdId()));
        String usedBy = findAnyReference(prod.getId());
        if (usedBy != null) {
            throw new IllegalArgumentException(
                    "%s에서 사용 중이라 삭제할 수 없습니다: %s".formatted(usedBy, prod.getProdNm()));
        }
        prodRepository.delete(prod);
    }

    /** 첫 참조에서 멈춘다 — 어느 앱이 먼저 걸리든 삭제가 막히는 결과는 같다 */
    private String findAnyReference(Long prodId) {
        for (ProdRefChecker checker : prodRefCheckers) {
            String usedBy = checker.findReference(prodId);
            if (usedBy != null) return usedBy;
        }
        return null;
    }

    private void validate(ProdSaveRequest row) {
        if (row.getProdNm() == null || row.getProdNm().isBlank()) {
            throw new IllegalArgumentException("상품명은 필수입니다.");
        }
        if (row.getTmpZon() == null) {
            throw new IllegalArgumentException("온도대는 필수입니다: " + row.getProdNm());
        }
        // 단위 코드가 공통코드 UOM 그룹에 실재하는지는 확인하지 않는다 — 화면 콤보박스로만
        // 들어오는 사내 시스템이라 저장 때마다 재조회는 쿼리만 늘린다. 빈 값만 막는다.
        requireUomCd(row.getInbUomCd(), "입고단위", row.getProdNm());
        requireUomCd(row.getOutbUomCd(), "출고단위", row.getProdNm());
        // NULL = 유통기한 미관리(공산품 등). 값이 있으면 1 이상이어야 한다.
        if (row.getShelfLifeDays() != null && row.getShelfLifeDays() < 1) {
            throw new IllegalArgumentException("유통기한(일)은 비워두거나(미관리) 1 이상이어야 합니다: " + row.getProdNm());
        }
    }

    private void requireUomCd(String uomCd, String label, String prodNm) {
        if (uomCd == null || uomCd.isBlank()) {
            throw new IllegalArgumentException(label + "는 필수입니다: " + prodNm);
        }
    }
}