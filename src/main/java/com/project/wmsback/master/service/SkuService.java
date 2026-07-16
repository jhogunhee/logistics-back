package com.project.wmsback.master.service;

import com.project.wmsback.master.dto.SkuResponse;
import com.project.wmsback.master.dto.SkuSaveRequest;
import com.project.wmsback.master.dto.SkuSearchCond;
import com.project.wmsback.master.entity.Sku;
import com.project.wmsback.master.repository.SkuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkuService {

    private final SkuRepository skuRepository;

    public List<SkuResponse> list(SkuSearchCond cond) {
        return skuRepository.search(cond).stream()
                .map(SkuResponse::from)
                .toList();
    }

    /** 신규(C)/수정(U)/삭제(D) 행 일괄 저장. 한 건이라도 실패하면 전체 롤백. */
    @Transactional
    public void saveAll(List<SkuSaveRequest> rows) {
        for (SkuSaveRequest row : rows) {
            switch (row.getStatus()) {
                case "C" -> { validate(row); create(row); }
                case "U" -> { validate(row); update(row); }
                case "D" -> delete(row);
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        // FK 위반(참조 중인 SKU 삭제 등)을 커밋 시점이 아니라 여기서 터뜨려 예외 변환이 되게 한다
        skuRepository.flush();
    }

    private void create(SkuSaveRequest row) {
        // 클라이언트가 보낸 코드는 받지 않는다 — 시퀀스로 채번 (SKU-0001 형식)
        String skuCd = String.format("SKU-%04d", skuRepository.nextSkuCdSeq());
        skuRepository.save(Sku.builder()
                .skuCd(skuCd)
                .skuNm(row.getSkuNm())
                .tempZone(row.getTempZone())
                .shelfLifeDays(row.getShelfLifeDays())
                .build());
    }

    private void update(SkuSaveRequest row) {
        Sku sku = skuRepository.findById(row.getSkuId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 SKU입니다: " + row.getSkuId()));
        sku.update(row.getSkuNm(), row.getTempZone(), row.getShelfLifeDays());
    }

    private void delete(SkuSaveRequest row) {
        Sku sku = skuRepository.findById(row.getSkuId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 SKU입니다: " + row.getSkuId()));
        skuRepository.delete(sku);
    }

    private void validate(SkuSaveRequest row) {
        if (row.getSkuNm() == null || row.getSkuNm().isBlank()) {
            throw new IllegalArgumentException("상품명은 필수입니다.");
        }
        if (row.getTempZone() == null) {
            throw new IllegalArgumentException("온도대는 필수입니다: " + row.getSkuNm());
        }
        // NULL = 유통기한 미관리(공산품 등). 값이 있으면 1 이상이어야 한다.
        if (row.getShelfLifeDays() != null && row.getShelfLifeDays() < 1) {
            throw new IllegalArgumentException("유통기한(일)은 비워두거나(미관리) 1 이상이어야 합니다: " + row.getSkuNm());
        }
    }
}