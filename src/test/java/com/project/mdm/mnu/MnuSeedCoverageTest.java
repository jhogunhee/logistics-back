package com.project.mdm.mnu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「모든 비GET 엔드포인트는 적어도 한 메뉴의 api_prfx 아래 있다」를 빌드 시에 지킨다.
 * <p>
 * 메뉴는 DB에 있고 테스트는 DB를 올리지 않으므로, 시드 파일과 컨트롤러 소스를 읽어 대조한다 —
 * 새 컨트롤러를 만들고 시드에 메뉴를 안 넣으면 여기서 걸린다.
 */
class MnuSeedCoverageTest {

    private static final Path SEED = Path.of("docs", "seed-mnu.sql");
    private static final Path SRC = Path.of("src", "main", "java");

    /** api_prfx는 INSERT INTO mnu 행의 8번째 값이다 */
    private static final Pattern ROW = Pattern.compile(
            "^\\('[^']*',\\s*'[^']*',\\s*'[^']*',\\s*'[^']*',\\s*\\d+,\\s*'[^']*',\\s*'[^']*',\\s*(NULL|'([^']*)')");
    private static final Pattern CLASS_MAPPING =
            Pattern.compile("@RequestMapping\\(\\s*(?:value\\s*=\\s*)?\"([^\"]*)\"\\s*\\)");
    private static final Pattern WRITE_MAPPING =
            Pattern.compile("@(?:Post|Put|Delete|Patch)Mapping(?:\\(\\s*(?:value\\s*=\\s*)?\"([^\"]*)\"\\s*\\))?");

    /** 메뉴가 관장하지 않는 것이 정상인 접두 — 로그인·비밀번호는 권한 이전이다 */
    private static final List<String> EXEMPT = List.of("/auth");

    @Test
    @DisplayName("시드의 api_prfx가 모든 비GET 엔드포인트를 덮는다")
    void seedCoversEveryWriteEndpoint() throws IOException {
        Set<String> prefixes = readApiPrefixes();
        List<String> uncovered = writeEndpointPaths().stream()
                .filter(p -> EXEMPT.stream().noneMatch(e -> under(e, p)))
                .filter(p -> prefixes.stream().noneMatch(x -> under(x, p)))
                .toList();

        assertTrue(uncovered.isEmpty(),
                "주인 없는 비GET 엔드포인트가 있다. docs/seed-mnu.sql에 메뉴를 넣거나 접두를 고쳐라: " + uncovered);
    }

    private static boolean under(String prefix, String path) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private Set<String> readApiPrefixes() throws IOException {
        Set<String> found = new LinkedHashSet<>();
        for (String line : Files.readAllLines(SEED, StandardCharsets.UTF_8)) {
            Matcher m = ROW.matcher(line.strip());
            if (m.find() && m.group(2) != null) {
                found.add(m.group(2));
            }
        }
        assertTrue(found.size() > 20, "시드를 못 읽었다. seed-mnu.sql 형식이 바뀌었는지 본다: " + found.size());
        return found;
    }

    /** 소스에서 직접 훑는다 — 스프링 컨텍스트를 올리면 DataSource를 요구한다 */
    private Set<String> writeEndpointPaths() throws IOException {
        Set<String> paths = new LinkedHashSet<>();
        for (Path file : controllerFiles()) {
            String src = Files.readString(file, StandardCharsets.UTF_8);
            Matcher cls = CLASS_MAPPING.matcher(src);
            String prefix = cls.find() ? cls.group(1) : "";

            Matcher method = WRITE_MAPPING.matcher(src);
            while (method.find()) {
                // 스프링은 메서드 값이 "/"로 시작해도 절대경로로 보지 않는다 — 언제나 이어 붙인다.
                // 클래스 매핑이 없는 컨트롤러(OutbAllocController 등)에서만 메서드 값이 곧 전체 경로다
                String path = (prefix + (method.group(1) == null ? "" : method.group(1))).replaceAll("/{2,}", "/");
                paths.add(path.isEmpty() ? "/" : path);
            }
        }
        assertTrue(paths.size() > 50, "컨트롤러를 못 읽었다: " + paths.size());
        return paths;
    }

    private List<Path> controllerFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(SRC)) {
            return new ArrayList<>(walk.filter(p -> p.getFileName().toString().endsWith("Controller.java")).toList());
        }
    }
}
