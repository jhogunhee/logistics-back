package com.project.mdm.nbr.service;

import com.project.mdm.nbr.entity.DyncKyTyp;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 채번 규칙의 접두어/구분자/SEQ 자릿수/리셋단위로 실제 발급 번호 문자열을 조립한다.
 * validate()는 저장 전 입력값 검증, render()는 조립을 담당한다.
 * NONE은 경계가 하나(접두어→SEQ)뿐이라 prfxDlmt만 쓰고, 날짜 기반 유형은
 * 접두어-날짜 사이에 prfxDlmt, 날짜-SEQ 사이에 deDlmt를 각각 쓴다.
 */
final class NbrPattern {

    private static final int MIN_SEQ_DGT = 1;
    private static final int MAX_SEQ_DGT = 9;

    private NbrPattern() {
    }

    static void validate(String prfx, String prfxDlmt, String deDlmt, Integer seqDgt, DyncKyTyp dyncKyTyp) {
        if (prfx == null || prfx.isBlank()) {
            throw new IllegalArgumentException("접두어는 필수입니다.");
        }
        if (seqDgt == null || seqDgt < MIN_SEQ_DGT || seqDgt > MAX_SEQ_DGT) {
            throw new IllegalArgumentException("SEQ 자릿수는 1~9 사이여야 합니다: " + seqDgt);
        }
    }

    static String render(String prfx, String prfxDlmt, String deDlmt, Integer seqDgt, long seq,
                          DyncKyTyp dyncKyTyp, LocalDate de) {
        StringBuilder result = new StringBuilder(prfx).append(prfxDlmt);
        if (dyncKyTyp.isDateBased()) {
            result.append(de.format(DateTimeFormatter.ofPattern(dyncKyTyp.getDyncKyPattern()))).append(deDlmt);
        }
        result.append(String.format("%0" + seqDgt + "d", seq));
        return result.toString();
    }
}
