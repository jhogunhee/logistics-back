package com.project.wmsback.master.service;

import com.project.wmsback.master.entity.DyncKyTyp;
import com.project.wmsback.master.entity.NbrRule;
import com.project.wmsback.master.entity.NbrSeq;
import com.project.wmsback.master.repository.NbrRuleRepository;
import com.project.wmsback.master.repository.NbrSeqRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NbrService {

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
        return issueWithKey(rule, NONE_DYNC_KY, LocalDate.now());
    }

    /**
     * YEAR/MONTH/DAY 규칙 전용 발급. de가 동적키(리셋 단위)이자 패턴의 날짜 조각 렌더링 기준이다.
     * 서버가 오늘 날짜로 강제하지 않는다 — 예정일·주문일처럼 호출자가 이미 들고 있는
     * 업무 일자를 그대로 쓴다 (신뢰된 서버 내부 호출이라 위변조 우려가 없다).
     */
    @Transactional
    public String issue(String ruleCd, LocalDate de) {
        NbrRule rule = findRule(ruleCd);
        if (rule.getDyncKyTyp() == DyncKyTyp.NONE) {
            throw new IllegalStateException(
                    "NONE 규칙은 issue(ruleCd)를 써야 합니다: " + ruleCd);
        }
        String dyncKy = de.format(DateTimeFormatter.ofPattern(rule.getDyncKyTyp().getDyncKyPattern()));
        return issueWithKey(rule, dyncKy, de);
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

    private String issueWithKey(NbrRule rule, String dyncKy, LocalDate de) {
        NbrSeq row = nbrSeqRepository.findByIdForUpdate(rule.getRuleCd(), dyncKy)
                .orElseGet(() -> {
                    nbrSeqRepository.insertIfAbsent(rule.getRuleCd(), dyncKy);
                    return nbrSeqRepository.findByIdForUpdate(rule.getRuleCd(), dyncKy)
                            .orElseThrow(() -> new IllegalStateException(
                                    "채번 카운터 초기화에 실패했습니다: " + rule.getRuleCd()));
                });
        row.increment();
        return NbrPattern.render(rule.getPrfx(), rule.getPrfxDlmt(), rule.getDeDlmt(), rule.getSeqDgt(),
                row.getSeq(), rule.getDyncKyTyp(), de);
    }
}
