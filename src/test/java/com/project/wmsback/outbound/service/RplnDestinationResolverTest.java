package com.project.wmsback.outbound.service;

import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.service.LocCapacityService;
import com.project.wmsback.outbound.entity.OutbAlloc;
import com.project.wmsback.outbound.entity.OutbLine;
import com.project.wmsback.warehouse.entity.BizDvsn;
import com.project.wmsback.warehouse.entity.FxngLoc;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.wmsback.warehouse.entity.Zon;
import com.project.wmsback.warehouse.repository.FxngLocRepository;
import com.project.wmsback.warehouse.repository.LocRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 수시보충 도착지 — 세 단계 순서 · 여유 누적 · 후보 로케이션 락 순서 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RplnDestinationResolverTest {

    @Mock LocRepository locRepository;
    @Mock FxngLocRepository fxngLocRepository;
    @Mock LocCapacityService locCapacityService;

    private RplnDestinationResolver resolver;
    private Prod prod;
    private Loc fixed;
    private Loc holding;
    private Loc empty;

    @BeforeEach
    void setUp() {
        resolver = new RplnDestinationResolver(locRepository, fxngLocRepository, locCapacityService);
        prod = mock(Prod.class);
        when(prod.getId()).thenReturn(1L);

        fixed = loc(20L, 50L);
        holding = loc(10L, 100L);
        empty = loc(30L, 100L);
        FxngLoc fxng = mock(FxngLoc.class);
        when(fxng.getLoc()).thenReturn(fixed);
        when(fxng.getMaxQty()).thenReturn(40L);   // 고정 상한 40 < loc 50

        when(fxngLocRepository.findAllWithLocByProdId(1L)).thenReturn(List.of(fxng));
        when(locRepository.findPikngLocsHoldingProd(1L, LocTyp.STORAGE, BizDvsn.PIKNG)).thenReturn(List.of(holding));
        when(locRepository.findEmptyPikngLocs(any(), any(), any())).thenReturn(List.of(empty));
        when(locRepository.findByIdForUpdate(anyLong())).thenAnswer(i -> Optional.of(loc(i.getArgument(0), 0L)));

        // loc 기준 여유: 고정 30 (→ 고정 상한 기준 20), 같은 상품 100, 빈 자리 100
        when(locCapacityService.availCapacity(fixed)).thenReturn(30L);
        when(locCapacityService.availCapacity(holding)).thenReturn(100L);
        when(locCapacityService.availCapacity(empty)).thenReturn(100L);
    }

    @Test
    @DisplayName("고정 로케이션이 먼저고, 여유가 다 차면 같은 상품이 있는 피킹존으로 내려간다 — 한 할당은 쪼개지 않는다")
    void prefersFixedThenHoldingAccumulatingCapacity() {
        OutbAlloc first = alloc(1L, 15);
        OutbAlloc second = alloc(2L, 10);   // 고정 잔여 5 < 10 → 다음 단계

        RplnDestinationResolver.Destinations result = resolver.resolve(List.of(second, first));

        assertEquals(fixed, result.replenishTo(first));
        assertEquals(holding, result.replenishTo(second));
        assertEquals(fixed, result.pickLocOf(first));
    }

    @Test
    @DisplayName("고정·같은 상품 자리가 없으면 빈 피킹존, 그것도 없으면 도착지 없음(null)")
    void fallsBackToEmptyThenNull() {
        when(fxngLocRepository.findAllWithLocByProdId(1L)).thenReturn(List.of());
        when(locRepository.findPikngLocsHoldingProd(1L, LocTyp.STORAGE, BizDvsn.PIKNG)).thenReturn(List.of());

        OutbAlloc alloc = alloc(1L, 10);
        assertEquals(empty, resolver.resolve(List.of(alloc)).replenishTo(alloc));

        when(locRepository.findEmptyPikngLocs(any(), any(), any())).thenReturn(List.of());
        RplnDestinationResolver.Destinations none = resolver.resolve(List.of(alloc));
        assertTrue(none.unresolved(alloc));
        assertNull(none.replenishTo(alloc));
    }

    @Test
    @DisplayName("후보 로케이션은 여유를 읽기 전에 id 오름차순으로 잠근다 — 같은 도착지로 향하는 두 발행의 직렬화 지점")
    void locksCandidatesAscendingBeforeReadingCapacity() {
        resolver.resolve(List.of(alloc(1L, 10)));

        InOrder order = inOrder(locRepository, locCapacityService);
        order.verify(locRepository).findByIdForUpdate(10L);
        order.verify(locRepository).findByIdForUpdate(20L);
        order.verify(locRepository).findByIdForUpdate(30L);
        order.verify(locCapacityService, org.mockito.Mockito.atLeastOnce()).availCapacity(any());
    }

    @Test
    @DisplayName("피킹존 할당은 판정 대상이 아니다 — 그 자리에서 집는다")
    void pikngZonAllocIsLeftAlone() {
        OutbAlloc inPikng = alloc(5L, 10);
        Zon pikngZon = holding.getZon();
        when(inPikng.getInv().getLoc().getZon()).thenReturn(pikngZon);

        RplnDestinationResolver.Destinations result = resolver.resolve(List.of(inPikng));

        assertFalse(result.unresolved(inPikng));
        assertNull(result.replenishTo(inPikng));
        assertEquals(inPikng.getInv().getLoc(), result.pickLocOf(inPikng));
        assertFalse(RplnDestinationResolver.inPikngZon(mock(Loc.class)));
    }

    private OutbAlloc alloc(long id, long qty) {
        Inv inv = Inv.builder().prod(prod).loc(mock(Loc.class)).lot(mock(Lot.class)).build();
        OutbAlloc created = OutbAlloc.builder()
                .outbLine(OutbLine.builder().prod(prod).odrQty(qty).build())
                .inv(inv).alocQty(qty).build();
        setId(created, id);
        return created;
    }

    private static Loc loc(long id, Long maxQty) {
        Loc created = mock(Loc.class);
        when(created.getId()).thenReturn(id);
        when(created.getMaxQty()).thenReturn(maxQty);
        Zon zon = mock(Zon.class);
        when(zon.getBizDvsn()).thenReturn(BizDvsn.PIKNG);
        when(created.getZon()).thenReturn(zon);
        return created;
    }

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
