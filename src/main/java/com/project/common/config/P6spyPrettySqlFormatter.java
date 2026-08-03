package com.project.common.config;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import org.hibernate.engine.jdbc.internal.FormatStyle;

import java.util.Locale;

/**
 * p6spy 로그 한 건의 출력 형식. 기본 포맷은 SQL이 한 덩어리 문자열이라 조인 몇 개만 붙어도
 * 읽을 수 없어서, 하이버네이트 포매터로 절 단위 줄바꿈·들여쓰기를 건다.
 * 등록은 {@link P6spyConfig}가 한다.
 */
public class P6spyPrettySqlFormatter implements MessageFormattingStrategy {

    @Override
    public String formatMessage(int connectionId, String now, long elapsed, String category,
                                String prepared, String sql, String url) {
        // commit/rollback처럼 SQL이 없는 건은 한 줄로 끝낸다 — 트랜잭션 경계 확인용
        if (sql == null || sql.isBlank()) {
            return String.format("[%s] conn %d | %dms", category, connectionId, elapsed);
        }
        return String.format("[%s] conn %d | %dms%s", category, connectionId, elapsed, pretty(sql));
    }

    private String pretty(String sql) {
        String head = sql.trim().toLowerCase(Locale.ROOT);
        boolean ddl = head.startsWith("create") || head.startsWith("alter")
                || head.startsWith("drop") || head.startsWith("comment");
        return (ddl ? FormatStyle.DDL : FormatStyle.BASIC).getFormatter().format(sql);
    }
}
