package com.project.wmsback.master.service;

import com.project.wmsback.master.dto.ProdResponse;
import com.project.wmsback.master.dto.ProdSaveRequest;
import com.project.wmsback.master.dto.ProdSearchCond;
import com.project.wmsback.master.entity.Prod;
import com.project.wmsback.master.entity.ProdUom;
import com.project.wmsback.master.repository.ProdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProdService {

    private final ProdRepository prodRepository;

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
        // FK 위반(참조 중인 상품 삭제 등)을 커밋 시점이 아니라 여기서 터뜨려 예외 변환이 되게 한다
        prodRepository.flush();
    }

    private void create(ProdSaveRequest row) {
        // 클라이언트가 보낸 코드는 받지 않는다 — 시퀀스로 채번 (PROD-0001 형식)
        String prodCd = String.format("PROD-%04d", prodRepository.nextProdCdSeq());
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

        // 나누어떨어지지 않으면 toOutbQty의 정수 나눗셈이 조용히 수량을 깎는다.
        // 자동 생성분은 둘 다 1이라 항상 통과하고, 단위 관리 화면에서 낱개수량을 고친 뒤
        // 상품의 입고/출고단위를 바꾸는 경로에서만 걸린다.
        long inbEaQty = prod.eaQtyOf(prod.getInbUomCd());
        long outbEaQty = prod.eaQtyOf(prod.getOutbUomCd());
        if (inbEaQty % outbEaQty != 0) {
            throw new IllegalArgumentException(
                    "입고단위 낱개수량(%d)은 출고단위 낱개수량(%d)의 배수여야 합니다: %s"
                            .formatted(inbEaQty, outbEaQty, prod.getProdNm()));
        }
    }

    private void ensureUom(Prod prod, String uomCd) {
        boolean exists = prod.getUoms().stream().anyMatch(u -> u.getUomCd().equals(uomCd));
        if (!exists) {
            prod.addUom(ProdUom.builder().uomCd(uomCd).eaQty(1L).build());
        }
    }

    private void delete(ProdSaveRequest row) {
        Prod prod = prodRepository.findById(row.getProdId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다: " + row.getProdId()));
        prodRepository.delete(prod);
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