package com.project.wmsback.master.controller;

import com.project.wmsback.master.dto.ProdResponse;
import com.project.wmsback.master.dto.ProdSaveRequest;
import com.project.wmsback.master.dto.ProdSearchCond;
import com.project.wmsback.master.service.ProdService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/master/prods")
@RequiredArgsConstructor
public class ProdController {

    private final ProdService prodService;

    @GetMapping
    public List<ProdResponse> list(@ModelAttribute ProdSearchCond cond) {
        return prodService.list(cond);
    }

    @PostMapping("/bulk")
    public void saveAll(@RequestBody List<ProdSaveRequest> rows) {
        prodService.saveAll(rows);
    }
}