package com.project.wmsback.master.service;

import com.project.wmsback.master.dto.ProdResponse;
import com.project.wmsback.master.dto.ProdSaveRequest;
import com.project.wmsback.master.dto.ProdSearchCond;
import com.project.wmsback.master.entity.Prod;
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
        prodRepository.save(Prod.builder()
                .prodCd(prodCd)
                .prodNm(row.getProdNm())
                .tempZone(row.getTempZone())
                .shelfLifeDays(row.getShelfLifeDays())
                .build());
    }

    private void update(ProdSaveRequest row) {
        Prod prod = prodRepository.findById(row.getProdId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다: " + row.getProdId()));
        prod.update(row.getProdNm(), row.getTempZone(), row.getShelfLifeDays());
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
        if (row.getTempZone() == null) {
            throw new IllegalArgumentException("온도대는 필수입니다: " + row.getProdNm());
        }
        // NULL = 유통기한 미관리(공산품 등). 값이 있으면 1 이상이어야 한다.
        if (row.getShelfLifeDays() != null && row.getShelfLifeDays() < 1) {
            throw new IllegalArgumentException("유통기한(일)은 비워두거나(미관리) 1 이상이어야 합니다: " + row.getProdNm());
        }
    }
}