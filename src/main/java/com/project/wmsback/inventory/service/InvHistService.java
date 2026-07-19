package com.project.wmsback.inventory.service;

import com.project.wmsback.inventory.dto.InvHistResponse;
import com.project.wmsback.inventory.dto.InvHistSearchCond;
import com.project.wmsback.inventory.repository.InvHistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvHistService {

    private final InvHistRepository invHistRepository;

    public List<InvHistResponse> list(InvHistSearchCond cond) {
        return invHistRepository.search(cond);
    }
}
