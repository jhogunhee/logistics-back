package com.project.wmsback.master.controller;

import com.project.wmsback.master.dto.LocResponse;
import com.project.wmsback.master.dto.LocSaveRequest;
import com.project.wmsback.master.dto.LocSearchCond;
import com.project.wmsback.master.service.LocService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/master/locs")
@RequiredArgsConstructor
public class LocController {

    private final LocService locService;

    @GetMapping
    public List<LocResponse> list(@ModelAttribute LocSearchCond cond) {
        return locService.list(cond);
    }

    @PostMapping("/bulk")
    public void saveAll(@RequestBody List<LocSaveRequest> rows) {
        locService.saveAll(rows);
    }
}
