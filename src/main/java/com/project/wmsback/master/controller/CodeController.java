package com.project.wmsback.master.controller;

import com.project.wmsback.master.dto.CodeResponse;
import com.project.wmsback.master.service.CodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/master/codes")
@RequiredArgsConstructor
public class CodeController {

    private final CodeService codeService;

    @GetMapping("/{groupCd}")
    public List<CodeResponse> list(@PathVariable String groupCd) {
        return codeService.list(groupCd);
    }
}