package com.project.mdm.usr.controller;

import com.project.mdm.usr.dto.UsrResponse;
import com.project.mdm.usr.dto.UsrSaveRequest;
import com.project.mdm.usr.dto.UsrSearchCond;
import com.project.mdm.usr.service.UsrService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 조회까지 시스템관리자만이다 — 접근 규칙은 SecurityConfig의 /master/usrs/** 한 줄에 있다. */
@RestController
@RequestMapping("/master/usrs")
@RequiredArgsConstructor
public class UsrController {

    private final UsrService usrService;

    @GetMapping
    public List<UsrResponse> list(@ModelAttribute UsrSearchCond cond) {
        return usrService.list(cond);
    }

    @PostMapping("/bulk")
    public void saveAll(@RequestBody List<UsrSaveRequest> rows) {
        usrService.saveAll(rows);
    }
}
