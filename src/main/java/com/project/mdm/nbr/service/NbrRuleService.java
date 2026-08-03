package com.project.mdm.nbr.service;

import com.project.mdm.nbr.dto.NbrRuleResponse;
import com.project.mdm.nbr.dto.NbrRuleSaveRequest;
import com.project.mdm.nbr.dto.NbrRuleSearchCond;
import com.project.mdm.nbr.dto.NbrSeqResponse;
import com.project.mdm.nbr.entity.NbrRule;
import com.project.mdm.nbr.repository.NbrRuleRepository;
import com.project.mdm.nbr.repository.NbrSeqRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NbrRuleService {

    private final NbrRuleRepository nbrRuleRepository;
    private final NbrSeqRepository nbrSeqRepository;

    public List<NbrRuleResponse> list(NbrRuleSearchCond cond) {
        return nbrRuleRepository.search(cond).stream()
                .map(NbrRuleResponse::from)
                .toList();
    }

    public List<NbrSeqResponse> seqs(String ruleCd) {
        if (!nbrRuleRepository.existsById(ruleCd)) {
            throw new IllegalArgumentException("존재하지 않는 채번 규칙입니다: " + ruleCd);
        }
        return nbrSeqRepository.findByRuleCdOrderByDyncKy(ruleCd).stream()
                .map(NbrSeqResponse::from)
                .toList();
    }

    /** 신규(C)/수정(U)/삭제(D) 행 일괄 저장. 한 건이라도 실패하면 전체 롤백. */
    @Transactional
    public void saveAll(List<NbrRuleSaveRequest> rows) {
        for (NbrRuleSaveRequest row : rows) {
            switch (row.getStatus()) {
                case "C" -> create(row);
                case "U" -> update(row);
                case "D" -> delete(row);
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        nbrRuleRepository.flush();
    }

    private void create(NbrRuleSaveRequest row) {
        if (row.getRuleCd() == null || row.getRuleCd().isBlank()) {
            throw new IllegalArgumentException("규칙 코드는 필수입니다.");
        }
        if (row.getRuleNm() == null || row.getRuleNm().isBlank()) {
            throw new IllegalArgumentException("규칙명은 필수입니다: " + row.getRuleCd());
        }
        if (row.getDyncKyTyp() == null) {
            throw new IllegalArgumentException("동적키유형은 필수입니다: " + row.getRuleCd());
        }
        if (nbrRuleRepository.existsById(row.getRuleCd())) {
            throw new IllegalArgumentException("이미 존재하는 채번 규칙 코드입니다: " + row.getRuleCd());
        }
        NbrPattern.validate(row.getPrfx(), row.getPrfxDlmt(), row.getDeDlmt(), row.getSeqDgt(), row.getDyncKyTyp());
        // rule_cd가 비생성(assigned) PK라 save()가 내부적으로 merge()를 타 SELECT가 한 번 더 나간다.
        // 관리 화면에서 사람이 저장하는 빈도라 무시 가능한 비용이다.
        nbrRuleRepository.save(NbrRule.builder()
                .ruleCd(row.getRuleCd())
                .ruleNm(row.getRuleNm())
                .prfx(row.getPrfx())
                .prfxDlmt(row.getPrfxDlmt())
                .deDlmt(row.getDeDlmt())
                .seqDgt(row.getSeqDgt())
                .dyncKyTyp(row.getDyncKyTyp())
                .build());
    }

    private void update(NbrRuleSaveRequest row) {
        NbrRule rule = nbrRuleRepository.findById(row.getRuleCd())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채번 규칙입니다: " + row.getRuleCd()));
        if (row.getDyncKyTyp() != null && row.getDyncKyTyp() != rule.getDyncKyTyp()) {
            throw new IllegalArgumentException(
                    "동적키유형은 변경할 수 없습니다. 새 규칙으로 등록하세요: " + row.getRuleCd());
        }
        if (row.getRuleNm() == null || row.getRuleNm().isBlank()) {
            throw new IllegalArgumentException("규칙명은 필수입니다: " + row.getRuleCd());
        }
        NbrPattern.validate(row.getPrfx(), row.getPrfxDlmt(), row.getDeDlmt(), row.getSeqDgt(), rule.getDyncKyTyp());
        rule.update(row.getRuleNm(), row.getPrfx(), row.getPrfxDlmt(), row.getDeDlmt(), row.getSeqDgt());
    }

    private void delete(NbrRuleSaveRequest row) {
        NbrRule rule = nbrRuleRepository.findById(row.getRuleCd())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채번 규칙입니다: " + row.getRuleCd()));
        nbrRuleRepository.delete(rule);
    }
}
