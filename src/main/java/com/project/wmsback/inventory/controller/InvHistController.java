package com.project.wmsback.inventory.controller;

import com.project.common.dto.PageCond;
import com.project.common.dto.PageResponse;
import com.project.wmsback.inventory.dto.InvHistResponse;
import com.project.wmsback.inventory.dto.InvHistSearchCond;
import com.project.wmsback.inventory.service.InvHistService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inventory/history")
@RequiredArgsConstructor
public class InvHistController {

    private final InvHistService invHistService;

    @GetMapping
    public PageResponse<InvHistResponse> list(@ModelAttribute InvHistSearchCond cond,
                                              @ModelAttribute PageCond pageCond) {
        return invHistService.list(cond, pageCond);
    }
}
