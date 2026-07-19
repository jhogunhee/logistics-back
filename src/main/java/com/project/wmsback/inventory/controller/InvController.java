package com.project.wmsback.inventory.controller;

import com.project.wmsback.inventory.dto.InvResponse;
import com.project.wmsback.inventory.dto.InvSearchCond;
import com.project.wmsback.inventory.service.InvService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/inventory/stock")
@RequiredArgsConstructor
public class InvController {

    private final InvService invService;

    @GetMapping
    public List<InvResponse> list(@ModelAttribute InvSearchCond cond) {
        return invService.list(cond);
    }
}
