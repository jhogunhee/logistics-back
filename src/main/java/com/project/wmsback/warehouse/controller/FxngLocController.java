package com.project.wmsback.warehouse.controller;

import com.project.wmsback.warehouse.dto.FxngLocResponse;
import com.project.wmsback.warehouse.dto.FxngLocSaveRequest;
import com.project.wmsback.warehouse.dto.FxngLocSearchCond;
import com.project.wmsback.warehouse.service.FxngLocService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/master/fxng-locs")
@RequiredArgsConstructor
public class FxngLocController {

    private final FxngLocService fxngLocService;

    @GetMapping
    public List<FxngLocResponse> list(@ModelAttribute FxngLocSearchCond cond) {
        return fxngLocService.list(cond);
    }

    @PostMapping("/bulk")
    public void saveAll(@RequestBody List<FxngLocSaveRequest> rows) {
        fxngLocService.saveAll(rows);
    }
}
