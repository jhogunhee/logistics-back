package com.project.wmsback.inventory.service;

import com.project.wmsback.inventory.dto.InvAlocRecResponse;
import com.project.wmsback.inventory.dto.InvResponse;
import com.project.wmsback.inventory.dto.InvSearchCond;
import com.project.wmsback.inventory.repository.InvAlocRecRepository;
import com.project.wmsback.inventory.repository.InvRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvService {

    private final InvRepository invRepository;
    private final InvAlocRecRepository invAlocRecRepository;

    public List<InvResponse> list(InvSearchCond cond) {
        return invRepository.search(cond);
    }

    /** 예약 대사 — inv.aloc_qty와 원천별 미소진 합을 재고 키마다 나란히. 어긋난 행(diff ≠ 0)이 곧 예약 잔류·누락이다 */
    public List<InvAlocRecResponse> reconcileAloc() {
        return invAlocRecRepository.reconcile();
    }
}
