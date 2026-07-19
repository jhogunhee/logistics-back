package com.project.wmsback.inventory.service;

import com.project.wmsback.inventory.dto.InvResponse;
import com.project.wmsback.inventory.dto.InvSearchCond;
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

    public List<InvResponse> list(InvSearchCond cond) {
        return invRepository.search(cond);
    }
}
