package com.project.wmsback.warehouse.service;

import com.project.wmsback.warehouse.dto.LotResponse;
import com.project.wmsback.warehouse.repository.LotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Lot 조회 전용 서비스. Lot 생성은 검수(ReceivingService)만 한다 —
 * Lot은 입고 배치(상품+입고일자+제조일자)의 식별자라 입고 없이 생기지 않는다.
 * 재고조사의 라인 수동 추가가 「이미 있는 Lot 중 고르기」로만 동작하는 이유이기도 하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LotService {

    private final LotRepository lotRepository;

    /** 상품별 Lot 목록 (유통기한 빠른 순 — FEFO와 같은 순서로 보인다) */
    public List<LotResponse> listByProd(Long prodId) {
        return lotRepository.findByProdIdOrderByExpiryDtAscLotNoAsc(prodId).stream()
                .map(LotResponse::from)
                .toList();
    }
}
