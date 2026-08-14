package com.project.mdm.code.controller;

import com.project.mdm.code.dto.CodeGroupResponse;
import com.project.mdm.code.dto.CodeGroupSaveRequest;
import com.project.mdm.code.service.CodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 코드 그룹을 코드와 다른 리소스로 나눈 이유 — CodeController는 /master/codes/{grpCd}
 * 패턴을 쓰는데, 그룹 엔드포인트를 그 밑에 두면 리터럴 경로(groups)가 {grpCd} 패턴과
 * 한 자리를 두고 경쟁한다(GROUPS라는 그룹 코드는 만들 수 없게 된다).
 */
@RestController
@RequestMapping("/master/code-groups")
@RequiredArgsConstructor
public class CodeGroupController {

    private final CodeService codeService;

    /** 그룹 목록 */
    @GetMapping
    public List<CodeGroupResponse> groups() {
        return codeService.groups();
    }

    /** 그룹 일괄 저장. 그룹 코드는 신규 행에서만 받고, 삭제는 하위 코드가 없을 때만 된다 */
    @PostMapping("/bulk")
    public void saveAllGroups(@RequestBody List<CodeGroupSaveRequest> rows) {
        codeService.saveAllGroups(rows);
    }
}
