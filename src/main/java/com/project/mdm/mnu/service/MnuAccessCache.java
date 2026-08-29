package com.project.mdm.mnu.service;

import com.project.common.security.MnuAccessSource;
import com.project.mdm.mnu.entity.Mnu;
import com.project.mdm.mnu.entity.MnuRole;
import com.project.mdm.mnu.repository.MnuRepository;
import com.project.mdm.mnu.repository.MnuRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.condition.RequestMethodsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * 메뉴 권한 캐시. 요청마다 DB를 보지 않으려고 통째로 들고 있는다.
 *
 * <p><b>인스턴스 하나를 전제한다</b>(Render 무료 플랜). 인스턴스를 늘리면 저장을 처리하지 않은
 * 쪽이 옛 권한으로 도므로, 그때는 짧은 TTL이나 알림이 필요하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MnuAccessCache implements MnuAccessSource {

    /** 메뉴가 관장하지 않는 것이 정상인 접두 — 로그인·비밀번호는 권한 이전이다 */
    private static final List<String> EXEMPT = List.of("/auth", "/health", "/error");

    private final MnuRepository mnuRepository;
    private final MnuRoleRepository mnuRoleRepository;
    /** 주인 없는 엔드포인트 경고에만 쓴다 — 웹 계층이 뜨기 전에는 없다 */
    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappings;

    /** 접두 → 그 접두를 가진 메뉴들이 켜진 역할을 모두 합친 것. 긴 접두 우선으로 정렬해 둔다 */
    private volatile List<Entry> entries = List.of();

    private record Entry(String prefix, Set<String> roles) {
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reload() {
        List<Mnu> menus = mnuRepository.findAll();
        if (menus.isEmpty()) {
            log.warn("[MNU] 메뉴 카탈로그가 비어 있다 — 시드를 적용했는지 확인할 것. "
                    + "지금은 시스템관리자만 정상 동작한다");
        }
        Map<String, List<String>> byMnu = mnuRoleRepository.findAll().stream()
                .collect(groupingBy(MnuRole::getMnuCd, mapping(role -> role.getRole().name(), toList())));

        // 같은 접두를 가진 메뉴가 여럿일 수 있다(입고검수·입고확정 등) — 역할을 합쳐 한 Entry로 만든다.
        // 합집합이 곧 「하나라도 켜져 있으면 통과」다
        this.entries = menus.stream()
                .filter(m -> m.getApiPrfx() != null)
                .collect(groupingBy(Mnu::getApiPrfx,
                        flatMapping(m -> byMnu.getOrDefault(m.getMnuCd(), List.<String>of()).stream(), toSet())))
                .entrySet().stream()
                .map(e -> new Entry(e.getKey(), e.getValue()))
                .sorted(comparing((Entry e) -> e.prefix().length()).reversed())
                .toList();
        warnUncoveredEndpoints();
    }

    @Override
    public boolean allows(List<String> roles, String path) {
        for (Entry entry : entries) {          // 긴 접두부터 — 첫 매칭이 그 경로의 주인이다(접두당 Entry 하나)
            if (under(entry.prefix(), path)) {
                return roles.stream().anyMatch(entry.roles()::contains);
            }
        }
        return true;                           // 관장하는 메뉴가 없다 — 상한이 이미 봤다
    }

    private static boolean under(String prefix, String path) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    /**
     * 주인 없는 비GET 엔드포인트 — 어느 메뉴의 api_prfx에도 안 걸리는 저장 API다.
     * 같은 검사를 빌드 시에는 {@code MnuSeedCoverageTest}가 시드 파일로 하는데, 라이브 DB는
     * 화면에서 편집되므로 시드와 갈라질 수 있다 — 그 어긋남이 보이는 자리가 여기다.
     * 메뉴 관리 화면이 이 목록을 배너로 띄운다.
     */
    public List<String> uncoveredEndpoints() {
        RequestMappingHandlerMapping mapping = handlerMappings.getIfAvailable();
        if (mapping == null) {
            return List.of();
        }
        Set<String> uncovered = new TreeSet<>();
        for (RequestMappingInfo info : mapping.getHandlerMethods().keySet()) {
            if (!hasWriteMethod(info.getMethodsCondition())) {
                continue;
            }
            for (String pattern : info.getPatternValues()) {
                if (EXEMPT.stream().noneMatch(e -> under(e, pattern))
                        && entries.stream().noneMatch(entry -> under(entry.prefix(), pattern))) {
                    uncovered.add(pattern);
                }
            }
        }
        return List.copyOf(uncovered);
    }

    private void warnUncoveredEndpoints() {
        List<String> uncovered = uncoveredEndpoints();
        if (!uncovered.isEmpty()) {
            log.warn("[MNU] 주인 없는 비GET 엔드포인트 {}건 — 메뉴의 api_prfx가 이 경로를 덮지 않는다: {}",
                    uncovered.size(), uncovered);
        }
    }

    private static boolean hasWriteMethod(RequestMethodsRequestCondition condition) {
        Set<RequestMethod> methods = condition.getMethods();
        return methods.stream().anyMatch(m -> !HttpMethod.GET.matches(m.name()));
    }
}
