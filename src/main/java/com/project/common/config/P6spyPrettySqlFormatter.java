package com.project.common.config;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import org.hibernate.engine.jdbc.internal.FormatStyle;

import java.util.Locale;
import java.util.Optional;

/**
 * p6spy 로그 한 건의 출력 형식. 기본 포맷은 SQL이 한 덩어리 문자열이라 조인 몇 개만 붙어도
 * 읽을 수 없어서, 하이버네이트 포매터로 절 단위 줄바꿈·들여쓰기를 건다.
 * 등록은 {@link P6spyConfig}가 한다.
 */
public class P6spyPrettySqlFormatter implements MessageFormattingStrategy {

    private static final String APP_PACKAGE = "com.project.";
    /** 포매터 자신 — 호출자를 찾을 때 건너뛴다. 같은 패키지의 다른 설정은 막지 않는다 */
    private static final String SELF = P6spyPrettySqlFormatter.class.getName();

    @Override
    public String formatMessage(int connectionId, String now, long elapsed, String category,
                                String prepared, String sql, String url) {
        String head = String.format("[%s] conn %d | %dms", category, connectionId, elapsed);
        Optional<String> caller = caller();

        // commit/rollback처럼 SQL이 없는 건은 한 줄로 끝낸다 — 트랜잭션 경계 확인용
        if (sql == null || sql.isBlank()) {
            return head + caller.map(c -> " | " + c).orElse("");
        }
        // SQL이 있으면 호출자를 SQL 바로 윗줄에 둔다 — 붙여 읽는 편이 눈이 덜 튄다
        return head + caller.map(c -> System.lineSeparator() + c).orElse("") + pretty(sql);
    }

    /**
     * 이 SQL을 낸 우리 코드의 {@code 클래스.메서드}. 못 찾으면 비어 있다.
     * <p>
     * 스택 안쪽부터 훑어 처음 만나는 우리 코드를 쓴다 — 리포지토리 조회면 그 조회 메서드가,
     * 지연로딩이면 컬렉션을 건드린 지점({@code Prod.eaQtyOf} 등)이 잡힌다.
     * <p>
     * 쿼리마다 손으로 주석을 다는 대신 스택에서 뽑는 이유는, 손으로 단 이름은 메서드를 개명해도
     * 따라오지 않아 조용히 썩기 때문이다. SQL 실행은 호출자와 같은 스레드에서 동기로 일어나므로
     * 이 포매터 위쪽 스택에 그 프레임이 그대로 남아 있다.
     * <p>
     * 프레임 수십 개를 훑는 비용은 수 마이크로초인데 DB가 원격(Supabase)이라 쿼리 한 번이
     * 20~50ms다 — 운영에서 로깅을 켜둬도 무시할 수 있는 몫이라 별도 스위치를 두지 않았다.
     */
    private Optional<String> caller() {
        return StackWalker.getInstance().walk(frames -> frames
                .filter(this::isAppCode)
                .findFirst()
                .map(this::label));
    }

    /** 프록시($$SpringCGLIB$$ 등)는 이름이 우리 클래스처럼 보여도 우리가 쓴 코드가 아니다 */
    private boolean isAppCode(StackWalker.StackFrame frame) {
        String name = frame.getClassName();
        return name.startsWith(APP_PACKAGE) && !name.equals(SELF) && !name.contains("$$");
    }

    /** 로그 한 줄이 길어지지 않게 패키지를 뗀다 */
    private String label(StackWalker.StackFrame frame) {
        String name = frame.getClassName();
        return name.substring(name.lastIndexOf('.') + 1) + "." + frame.getMethodName();
    }

    private String pretty(String sql) {
        String head = sql.trim().toLowerCase(Locale.ROOT);
        boolean ddl = head.startsWith("create") || head.startsWith("alter")
                || head.startsWith("drop") || head.startsWith("comment");
        return (ddl ? FormatStyle.DDL : FormatStyle.BASIC).getFormatter().format(sql);
    }
}
