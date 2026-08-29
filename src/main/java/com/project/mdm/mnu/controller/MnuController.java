package com.project.mdm.mnu.controller;

import com.project.mdm.mnu.dto.MnuListResponse;
import com.project.mdm.mnu.dto.MnuRoleGridResponse;
import com.project.mdm.mnu.dto.MnuRoleSaveRequest;
import com.project.mdm.mnu.dto.MnuSaveRequest;
import com.project.mdm.mnu.entity.MnuDvsn;
import com.project.mdm.mnu.service.MnuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 메뉴 카탈로그 관리. /master/** 는 SecurityRules에서 ADMR 전용이라 시스템관리자만 연다 */
@RestController
@RequestMapping("/master/mnus")
@RequiredArgsConstructor
public class MnuController {

    private final MnuService mnuService;

    @GetMapping
    public MnuListResponse list(@RequestParam(required = false) MnuDvsn dvsn) {
        return mnuService.list(dvsn);
    }

    @PostMapping("/bulk")
    public void saveAll(@RequestBody List<MnuSaveRequest> rows) {
        mnuService.saveAll(rows);
    }

    @GetMapping("/roles")
    public List<MnuRoleGridResponse> roleGrid(@RequestParam(required = false) MnuDvsn dvsn) {
        return mnuService.roleGrid(dvsn);
    }

    /** 그 구분의 매핑을 통째로 교체한다. 안 보낸 메뉴는 권한이 없어진다 */
    @PutMapping("/roles")
    public void replaceRoles(@RequestParam(required = false) MnuDvsn dvsn,
                             @RequestBody List<MnuRoleSaveRequest> rows) {
        mnuService.replaceRoles(dvsn, rows);
    }
}
