package com.project.wmsback.master.service;

import com.project.wmsback.master.entity.DyncKyTyp;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 채번 패턴 문자열 파싱. 지원 토큰: {SEQ:n}(n=1~9, 패턴당 정확히 1개) + 날짜 토큰.
 * 날짜 토큰 이름({yyyyMMdd}/{yyyy}/{MM}/{dd})은 그 자체로 java.time.format.DateTimeFormatter
 * 패턴 문자열이라 별도 매핑 없이 그대로 재사용한다.
 * render()는 validate()를 통과한 패턴에만 쓴다 — 검증되지 않은 알 수 없는 토큰이 오면
 * 날짜 포맷 시도로 넘어가 DateTimeFormatter가 던지는 예외가 그대로 새어나간다.
 */
final class NbrPattern {

    private static final Pattern TOKEN = Pattern.compile("\\{([^}]*)}");
    private static final Pattern SEQ_TOKEN = Pattern.compile("SEQ:([1-9])");
    private static final Set<String> DATE_TOKENS = Set.of("yyyyMMdd", "yyyy", "MM", "dd");

    private NbrPattern() {
    }

    static void validate(String ptrn, DyncKyTyp dyncKyTyp) {
        Matcher matcher = TOKEN.matcher(ptrn);
        int seqCount = 0;
        boolean hasDateToken = false;
        while (matcher.find()) {
            String token = matcher.group(1);
            if (SEQ_TOKEN.matcher(token).matches()) {
                seqCount++;
            } else if (DATE_TOKENS.contains(token)) {
                hasDateToken = true;
            } else {
                throw new IllegalArgumentException("지원하지 않는 채번 패턴 토큰입니다: {" + token + "}");
            }
        }
        if (seqCount != 1) {
            throw new IllegalArgumentException("채번 패턴은 {SEQ:n} 토큰을 정확히 1개 포함해야 합니다: " + ptrn);
        }
        if (dyncKyTyp == DyncKyTyp.DATE && !hasDateToken) {
            throw new IllegalArgumentException(
                    "동적키유형이 DATE이면 날짜 토큰({yyyyMMdd} 등)이 패턴에 1개 이상 있어야 합니다: " + ptrn);
        }
    }

    static String render(String ptrn, long seq, LocalDate de) {
        Matcher matcher = TOKEN.matcher(ptrn);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            Matcher seqMatcher = SEQ_TOKEN.matcher(token);
            String replacement = seqMatcher.matches()
                    ? String.format("%0" + seqMatcher.group(1) + "d", seq)
                    : de.format(DateTimeFormatter.ofPattern(token));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
