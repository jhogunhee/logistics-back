package com.project.wmsback.master.controller;

import com.project.wmsback.master.dto.CodeGroupResponse;
import com.project.wmsback.master.dto.CodeGroupSaveRequest;
import com.project.wmsback.master.dto.CodeResponse;
import com.project.wmsback.master.dto.CodeSaveRequest;
import com.project.wmsback.master.dto.CodeSearchCond;
import com.project.wmsback.master.service.CodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/master/codes")
@RequiredArgsConstructor
public class CodeController {

    private final CodeService codeService;

    /** 그룹 목록. 그룹 자체는 조회만 한다 — 스키마와 함께 결정되는 값이라 화면에서 늘리지 않는다 */
    @GetMapping("/groups")
    public List<CodeGroupResponse> groups() {
        return codeService.groups();
    }

    /** 그룹 일괄 저장. 그룹 코드는 신규 행에서만 받고, 삭제는 하위 코드가 없을 때만 된다 */
    @PostMapping("/groups/bulk")
    public void saveAllGroups(@RequestBody List<CodeGroupSaveRequest> rows) {
        codeService.saveAllGroups(rows);
    }

    /** 화면 콤보박스용 — 사용중(Y) 코드만 */
    @GetMapping("/{grpCd}")
    public List<CodeResponse> list(@PathVariable String grpCd) {
        return codeService.list(grpCd);
    }

    /** 공통코드 관리 화면용 — 폐기(N) 포함 전체 */
    @GetMapping("/{grpCd}/search")
    public List<CodeResponse> search(@PathVariable String grpCd, @ModelAttribute CodeSearchCond cond) {
        return codeService.search(grpCd, cond);
    }

    @PostMapping("/{grpCd}/bulk")
    public void saveAll(@PathVariable String grpCd, @RequestBody List<CodeSaveRequest> rows) {
        codeService.saveAll(grpCd, rows);
    }
}
