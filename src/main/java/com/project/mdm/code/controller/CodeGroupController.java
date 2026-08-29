package com.project.mdm.code.controller;

import com.project.mdm.code.dto.CodeGroupResponse;
import com.project.mdm.code.dto.CodeGroupSaveRequest;
import com.project.mdm.code.dto.CodeGroupSearchCond;
import com.project.mdm.code.service.CodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 코드 그룹을 코드와 다른 컨트롤러로 나눈 이유 — CodeController는 /master/codes/{grpCd}
 * 패턴을 쓰는데, 그룹 엔드포인트가 그 밑에 들어가면 리터럴 groups가 {grpCd} 패턴과 한 자리를
 * 두고 경쟁한다. 그래도 2026-08-29에 이름공간만은 /master/codes 아래로 들여왔다 — 한 화면이
 * 쓰기 API 접두를 하나만 가질 수 있어서(mnu.api_prfx), 밖에 두면 주인 없는 엔드포인트가 된다.
 * 경쟁은 실제로는 안 일어난다: 그룹코드는 전부 대문자이고 경로 매칭은 대소문자를 가린다.
 */
@RestController
@RequestMapping("/master/codes/groups")
@RequiredArgsConstructor
public class CodeGroupController {

    private final CodeService codeService;

    /** 그룹 목록 */
    @GetMapping
    public List<CodeGroupResponse> groups() {
        return codeService.groups();
    }

    /** 관리 화면용 그룹 검색 (그룹코드/그룹명 부분일치) */
    @GetMapping("/search")
    public List<CodeGroupResponse> search(@ModelAttribute CodeGroupSearchCond cond) {
        return codeService.searchGroups(cond);
    }

    /** 그룹 일괄 저장. 그룹 코드는 신규 행에서만 받고, 삭제는 하위 코드가 없을 때만 된다 */
    @PostMapping("/bulk")
    public void saveAllGroups(@RequestBody List<CodeGroupSaveRequest> rows) {
        codeService.saveAllGroups(rows);
    }
}
