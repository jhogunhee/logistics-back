package com.project.mdm.prod.controller;

import com.project.mdm.prod.dto.ProdVndrResponse;
import com.project.mdm.prod.dto.ProdVndrSaveRequest;
import com.project.mdm.prod.dto.ProdVndrSearchCond;
import com.project.mdm.prod.service.ProdVndrService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/master/prod-vndrs")
@RequiredArgsConstructor
public class ProdVndrController {

    private final ProdVndrService prodVndrService;

    @GetMapping
    public List<ProdVndrResponse> list(@ModelAttribute ProdVndrSearchCond cond) {
        return prodVndrService.list(cond);
    }

    @PostMapping("/bulk")
    public void saveAll(@RequestBody List<ProdVndrSaveRequest> rows) {
        prodVndrService.saveAll(rows);
    }
}
