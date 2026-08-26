package com.project.wmsback.inventory.service;

import com.project.common.dto.PageCond;
import com.project.common.dto.PageResponse;
import com.project.wmsback.inventory.dto.InvHistResponse;
import com.project.wmsback.inventory.dto.InvHistSearchCond;
import com.project.wmsback.inventory.repository.InvHistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvHistService {

    private final InvHistRepository invHistRepository;

    public PageResponse<InvHistResponse> list(InvHistSearchCond cond, PageCond pageCond) {
        return invHistRepository.search(cond, pageCond);
    }
}
