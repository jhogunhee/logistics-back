package com.project.wmsback.inventory.controller;

import com.project.wmsback.inventory.dto.InvHistResponse;
import com.project.wmsback.inventory.dto.InvHistSearchCond;
import com.project.wmsback.inventory.service.InvHistService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/inventory/history")
@RequiredArgsConstructor
public class InvHistController {

    private final InvHistService invHistService;

    @GetMapping
    public List<InvHistResponse> list(@ModelAttribute InvHistSearchCond cond) {
        return invHistService.list(cond);
    }
}
