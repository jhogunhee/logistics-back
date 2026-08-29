package com.project.mdm.mnu.service;

import com.project.mdm.mnu.entity.Mnu;
import com.project.mdm.mnu.entity.MnuDvsn;
import com.project.mdm.mnu.entity.MnuRole;
import com.project.mdm.mnu.repository.MnuRepository;
import com.project.mdm.mnu.repository.MnuRoleRepository;
import com.project.mdm.usr.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 「어느 메뉴가 이 요청 경로를 관장하는가」의 명세. 접두는 겹칠 수 있고 한 접두를 여러 메뉴가
 * 나눠 쓸 수 있어서, 그 둘을 어떻게 푸는지가 여기 고정된다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MnuPrefixMatcherTest {

    @Mock MnuRepository mnuRepository;
    @Mock MnuRoleRepository mnuRoleRepository;
    @Mock ObjectProvider<RequestMappingHandlerMapping> handlerMappings;

    @Test
    @DisplayName("가장 긴 접두가 이긴다 — 겹침은 정상이다")
    void longestPrefixWins() {
        MnuAccessCache cache = cacheOf(
                Map.of("/master/mnus", List.of(Role.CENT_ADMR),
                       "/master/mnus/roles", List.of()));

        assertTrue(cache.allows(List.of("CENT_ADMR"), "/master/mnus/bulk"));
        // 더 긴 접두의 메뉴는 아무 역할에도 안 켜져 있다
        assertFalse(cache.allows(List.of("CENT_ADMR"), "/master/mnus/roles"));
    }

    @Test
    @DisplayName("같은 접두를 가진 메뉴가 여럿이면 하나라도 켜져 있을 때 통과한다")
    void anyOwnerOpensThePrefix() {
        // 입고검수(IB_PIC만 켜짐)와 입고확정(아무도 안 켜짐)이 /inbound/asns를 나눠 쓴다
        MnuAccessCache cache = cacheOfMenus(
                menu("IB_RECEIVING", "/inbound/asns", List.of(Role.IB_PIC)),
                menu("IB_CONFIRM", "/inbound/asns", List.of()));

        assertTrue(cache.allows(List.of("IB_PIC"), "/inbound/asns/1/receive"));
        assertFalse(cache.allows(List.of("OUTB_PIC"), "/inbound/asns/1/receive"));
    }

    @Test
    @DisplayName("접두는 세그먼트 경계를 지킨다 — /inventory/stock이 /inventory/stocktakes를 먹지 않는다")
    void respectsSegmentBoundary() {
        MnuAccessCache cache = cacheOf(Map.of("/inventory/stock", List.of()));

        assertTrue(cache.allows(List.of("INV_PIC"), "/inventory/stocktakes/1/confirm"));
    }

    @Test
    @DisplayName("관장하는 메뉴가 없는 경로는 통과한다 — 상한이 이미 봤다")
    void unmanagedPathPasses() {
        MnuAccessCache cache = cacheOf(Map.of("/inventory/adjs", List.of()));

        assertTrue(cache.allows(List.of("INQ"), "/auth/pwd"));
    }

    @Test
    @DisplayName("조회 전용 화면(api_prfx 없음)은 아무 경로도 관장하지 않는다")
    void readOnlyMenuOwnsNothing() {
        MnuAccessCache cache = cacheOfMenus(menu("STK_STATUS", null, List.of(Role.INQ)));

        assertTrue(cache.allows(List.of("INQ"), "/inventory/adjs"));
    }

    /** 접두 하나에 메뉴 하나씩 */
    private MnuAccessCache cacheOf(Map<String, List<Role>> byPrefix) {
        List<Menu> menus = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, List<Role>> e : byPrefix.entrySet()) {
            menus.add(menu("M" + i++, e.getKey(), e.getValue()));
        }
        return cacheOfMenus(menus.toArray(Menu[]::new));
    }

    private MnuAccessCache cacheOfMenus(Menu... menus) {
        List<MnuRole> roles = new ArrayList<>();
        List<Mnu> rows = new ArrayList<>();
        for (Menu m : menus) {
            rows.add(Mnu.builder()
                    .mnuCd(m.mnuCd()).mnuNm(m.mnuCd()).dvsn(MnuDvsn.WEB).grpNm("재고")
                    .srtSeq(300).iconNm("Box").scrnPth("/x/" + m.mnuCd()).apiPrfx(m.apiPrfx())
                    .build());
            m.roles().forEach(role -> roles.add(new MnuRole(m.mnuCd(), role)));
        }
        when(mnuRepository.findAll()).thenReturn(rows);
        when(mnuRoleRepository.findAll()).thenReturn(roles);

        MnuAccessCache cache = new MnuAccessCache(mnuRepository, mnuRoleRepository, handlerMappings);
        cache.reload();
        return cache;
    }

    private static Menu menu(String mnuCd, String apiPrfx, List<Role> roles) {
        return new Menu(mnuCd, apiPrfx, roles);
    }

    private record Menu(String mnuCd, String apiPrfx, List<Role> roles) {
    }
}
