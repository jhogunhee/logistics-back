package com.project.mdm.nbr.service;

import com.project.mdm.nbr.entity.DyncKyTyp;
import com.project.mdm.nbr.entity.NbrRule;
import com.project.mdm.nbr.entity.NbrSeq;
import com.project.mdm.nbr.repository.NbrRuleRepository;
import com.project.mdm.nbr.repository.NbrSeqRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NbrService {

    /**
     * NONE 규칙의 동적키. nbr_seq는 PK가 (rule_cd, dync_ky)이고 dync_ky가 NOT NULL이라
     * 리셋 단위가 없는 규칙도 카운터 행을 가지려면 키 값이 필요하다 — 스키마 주석이 정한 고정값 "-".
     */
    private static final String NONE_DYNC_KY = "-";

    private final NbrRuleRepository nbrRuleRepository;
    private final NbrSeqRepository nbrSeqRepository;

    /** NONE 규칙 전용 발급 */
    @Transactional
    public String issue(String ruleCd) {
        NbrRule rule = findRule(ruleCd);
        if (rule.getDyncKyTyp() != DyncKyTyp.NONE) {
            throw new IllegalStateException(
                    "NONE 이외 규칙은 issue(ruleCd, LocalDate)를 써야 합니다: " + ruleCd);
        }
        long seq = nextSeq(rule, NONE_DYNC_KY);
        return NbrPattern.render(rule.getPrfx(), rule.getPrfxDlmt(), rule.getDeDlmt(), rule.getSeqDgt(),
                seq, rule.getDyncKyTyp(), null);
    }

    /**
     * YEAR/MONTH/DAY 규칙 전용 발급. de가 동적키(리셋 단위)이자 패턴의 날짜 조각 렌더링 기준이다.
     */
    @Transactional
    public String issue(String ruleCd, LocalDate de) {
        NbrRule rule = findRule(ruleCd);
        if (rule.getDyncKyTyp() == DyncKyTyp.NONE) {
            throw new IllegalStateException(
                    "NONE 규칙은 issue(ruleCd)를 써야 합니다: " + ruleCd);
        }
        String dyncKy = de.format(DateTimeFormatter.ofPattern(rule.getDyncKyTyp().getDyncKyPattern()));
        long seq = nextSeq(rule, dyncKy);
        return NbrPattern.render(rule.getPrfx(), rule.getPrfxDlmt(), rule.getDeDlmt(), rule.getSeqDgt(),
                seq, rule.getDyncKyTyp(), de);
    }

    /** DB 접근 없이 오늘 날짜 + seq=1로 렌더링만 — 규칙 저장 전 화면 미리보기용 */
    public String preview(String prfx, String prfxDlmt, String deDlmt, Integer seqDgt, DyncKyTyp dyncKyTyp) {
        NbrPattern.validate(prfx, prfxDlmt, deDlmt, seqDgt, dyncKyTyp);
        return NbrPattern.render(prfx, prfxDlmt, deDlmt, seqDgt, 1, dyncKyTyp, LocalDate.now());
    }

    private NbrRule findRule(String ruleCd) {
        return nbrRuleRepository.findById(ruleCd)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채번 규칙입니다: " + ruleCd));
    }

    /** rule_cd+dync_ky 카운터를 행 락 아래 1 증가시키고 그 값을 돌려준다 */
    private long nextSeq(NbrRule rule, String dyncKy) {
        NbrSeq row = nbrSeqRepository.findByIdForUpdate(rule.getRuleCd(), dyncKy)
                .orElseGet(() -> {
                    nbrSeqRepository.insertIfAbsent(rule.getRuleCd(), dyncKy);
                    return nbrSeqRepository.findByIdForUpdate(rule.getRuleCd(), dyncKy)
                            .orElseThrow(() -> new IllegalStateException(
                                    "채번 카운터 초기화에 실패했습니다: " + rule.getRuleCd()));
                });
        row.increment();
        return row.getSeq();
    }
}
