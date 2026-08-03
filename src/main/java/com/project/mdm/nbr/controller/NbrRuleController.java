package com.project.mdm.nbr.controller;

import com.project.mdm.nbr.dto.NbrIssueResponse;
import com.project.mdm.nbr.dto.NbrPreviewRequest;
import com.project.mdm.nbr.dto.NbrPreviewResponse;
import com.project.mdm.nbr.dto.NbrRuleResponse;
import com.project.mdm.nbr.dto.NbrRuleSaveRequest;
import com.project.mdm.nbr.dto.NbrRuleSearchCond;
import com.project.mdm.nbr.dto.NbrSeqResponse;
import com.project.mdm.nbr.service.NbrRuleService;
import com.project.mdm.nbr.service.NbrService;
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
@RequestMapping("/master/nbr-rules")
@RequiredArgsConstructor
public class NbrRuleController {

    private final NbrRuleService nbrRuleService;
    private final NbrService nbrService;

    @GetMapping
    public List<NbrRuleResponse> list(@ModelAttribute NbrRuleSearchCond cond) {
        return nbrRuleService.list(cond);
    }

    @PostMapping("/bulk")
    public void saveAll(@RequestBody List<NbrRuleSaveRequest> rows) {
        nbrRuleService.saveAll(rows);
    }

    @GetMapping("/{ruleCd}/seqs")
    public List<NbrSeqResponse> seqs(@PathVariable String ruleCd) {
        return nbrRuleService.seqs(ruleCd);
    }

    /** 테스트/외부 호출용. 항상 오늘 날짜 기준 — 내부 Java 호출과 달리 클라이언트가 날짜를 넘길 수 없다 */
    @PostMapping("/{ruleCd}/issue")
    public NbrIssueResponse issue(@PathVariable String ruleCd) {
        return new NbrIssueResponse(nbrService.issue(ruleCd));
    }

    @PostMapping("/preview")
    public NbrPreviewResponse preview(@RequestBody NbrPreviewRequest req) {
        return new NbrPreviewResponse(nbrService.preview(
                req.getPrfx(), req.getPrfxDlmt(), req.getDeDlmt(), req.getSeqDgt(), req.getDyncKyTyp()));
    }
}
