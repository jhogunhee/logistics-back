package com.project.wmsback.master.controller;

import com.project.wmsback.master.dto.ZonResponse;
import com.project.wmsback.master.dto.ZonSaveRequest;
import com.project.wmsback.master.dto.ZonSearchCond;
import com.project.wmsback.master.service.ZonService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/master/zons")
@RequiredArgsConstructor
public class ZonController {

    private final ZonService zonService;

    @GetMapping
    public List<ZonResponse> list(@ModelAttribute ZonSearchCond cond) {
        return zonService.list(cond);
    }

    @PostMapping("/bulk")
    public void saveAll(@RequestBody List<ZonSaveRequest> rows) {
        zonService.saveAll(rows);
    }
}
