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
        NbrRule rule = row.toEntity();
        if (nbrRuleRepository.existsById(rule.getRuleCd())) {
            throw new IllegalArgumentException("이미 존재하는 채번 규칙 코드입니다: " + rule.getRuleCd());
        }
        // rule_cd가 비생성(assigned) PK라 save()가 내부적으로 merge()를 타 SELECT가 한 번 더 나간다.
        // 관리 화면에서 사람이 저장하는 빈도라 무시 가능한 비용이다.
        nbrRuleRepository.save(rule);
    }

    private void update(NbrRuleSaveRequest row) {
        row.updateEntity(find(row.getRuleCd()));
    }

    /**
     * 물리삭제. 발급 이력(nbr_seq 카운터)이 있는 규칙은 거부한다 — 이미 발급된 번호가 문서·마스터에
     * 박혀 있는데 규칙을 지우면 그 코드의 발급이 전면 중단되고, 지웠다 재등록하면 카운터가 고아로
     * 남거나(이어받음) 같이 지우면 번호가 재사용돼 기존 데이터와 충돌한다. 한 번이라도 발급한
     * 규칙은 지울 수 없다는 것이 가장 안전한 정책이다.
     */
    private void delete(NbrRuleSaveRequest row) {
        NbrRule rule = find(row.getRuleCd());
        if (nbrSeqRepository.existsByRuleCd(rule.getRuleCd())) {
            throw new IllegalArgumentException(
                    "발급 이력이 있는 채번 규칙은 삭제할 수 없습니다: " + rule.getRuleCd());
        }
        nbrRuleRepository.delete(rule);
    }

    private NbrRule find(String ruleCd) {
        return nbrRuleRepository.findById(ruleCd)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채번 규칙입니다: " + ruleCd));
    }
}
