package com.project.mdm.code.controller;

import com.project.mdm.code.dto.CodeResponse;
import com.project.mdm.code.dto.CodeSaveRequest;
import com.project.mdm.code.dto.CodeSearchCond;
import com.project.mdm.code.service.CodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 그룹 관련 엔드포인트는 CodeGroupController(/master/code-groups)에 있다 — 분리 이유도 거기에 */
@RestController
@RequestMapping("/master/codes")
@RequiredArgsConstructor
public class CodeController {

    private final CodeService codeService;

    /** 화면 콤보박스용 — 그룹의 전체 코드 (srt_seq 순). 사용여부 컬럼은 두지 않는다 (CodeService.delete 참고) */
    @GetMapping("/{grpCd}")
    public List<CodeResponse> list(@PathVariable String grpCd) {
        return codeService.list(grpCd);
    }

    /** 공통코드 관리 화면용 — 코드/코드명 부분일치 검색 */
    @GetMapping("/{grpCd}/search")
    public List<CodeResponse> search(@PathVariable String grpCd, @ModelAttribute CodeSearchCond cond) {
        return codeService.search(grpCd, cond);
    }

    @PostMapping("/{grpCd}/bulk")
    public void saveAll(@PathVariable String grpCd, @RequestBody List<CodeSaveRequest> rows) {
        codeService.saveAll(grpCd, rows);
    }
}
