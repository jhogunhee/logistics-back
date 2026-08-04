package com.project.wmsback.outbound.service;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.TmpZon;
import com.project.mdm.store.entity.Store;
import com.project.wmsback.outbound.dto.OutbLineResponse;
import com.project.wmsback.outbound.dto.OutbOrderResponse;
import com.project.wmsback.outbound.dto.OutbOrderSearchCond;
import com.project.wmsback.outbound.entity.OutbLine;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.repository.OutbAllocRepository;
import com.project.wmsback.outbound.repository.OutbLineRepository;
import com.project.wmsback.outbound.repository.OutbOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 출고예정 조회의 <b>할당 수량 집계</b>. 출고는 할당 수량을 라인 컬럼으로 두지 않아
 * ({@link OutbLine} 참고) 응답을 만들 때 {@code outb_alloc} 집계를 합쳐야 하는데,
 * 그 합치는 규칙이 여기 검증 대상이다 — 입고({@code ib_line.rcvd_qty}가 컬럼)와 갈리는 지점.
 *
 * <p>특히 <b>할당이 없는 라인은 집계 맵에 키 자체가 없다</b>(GROUP BY 결과라서).
 * 0으로 읽지 않으면 NPE가 나거나 라인이 통째로 빠진다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutbOrderServiceTest {

    @Mock OutbOrderRepository outbOrderRepository;
    @Mock OutbLineRepository outbLineRepository;
    @Mock OutbAllocRepository outbAllocRepository;

    @InjectMocks OutbOrderService outbOrderService;

    private Prod prod;
    private Store store;

    @BeforeEach
    void setUp() {
        prod = mock(Prod.class);
        when(prod.getId()).thenReturn(1L);
        when(prod.getProdCd()).thenReturn("PROD-0001");
        when(prod.getProdNm()).thenReturn("테스트 상품");
        when(prod.getTmpZon()).thenReturn(TmpZon.DRY);

        store = mock(Store.class);
        when(store.getId()).thenReturn(10L);
        when(store.getStoreCd()).thenReturn("ST-001");
        when(store.getStoreNm()).thenReturn("강남점");
    }

    @Test
    @DisplayName("목록 — 할당 없는 라인은 0으로 읽고, 할당된 라인 수만 진행도에 센다")
    void listCountsOnlyAllocatedLines() {
        OutbOrder order = order(1L, line(11L, 30), line(12L, 20));
        when(outbOrderRepository.search(any(OutbOrderSearchCond.class))).thenReturn(List.of(order));
        // 12번 라인은 할당이 없어 맵에 키가 없다 — 실제 GROUP BY 결과와 같은 모양
        when(outbAllocRepository.sumAlocQtyByLineIds(anyList())).thenReturn(Map.of(11L, 12L));

        OutbOrderResponse res = outbOrderService.list(new OutbOrderSearchCond()).get(0);

        assertEquals(2, res.getLineCount());
        assertEquals(1, res.getAlocLineCount());
        assertEquals(50, res.getTotalOrderQty());
        assertEquals(12, res.getTotalAlocQty());
    }

    @Test
    @DisplayName("목록 — 할당이 한 건도 없으면 빈 맵이 와도 전부 0이다")
    void listHandlesEmptyAllocMap() {
        OutbOrder order = order(1L, line(11L, 30));
        when(outbOrderRepository.search(any(OutbOrderSearchCond.class))).thenReturn(List.of(order));
        when(outbAllocRepository.sumAlocQtyByLineIds(anyList())).thenReturn(Map.of());

        OutbOrderResponse res = outbOrderService.list(new OutbOrderSearchCond()).get(0);

        assertEquals(0, res.getAlocLineCount());
        assertEquals(0, res.getTotalAlocQty());
    }

    @Test
    @DisplayName("목록 — 여러 주문의 라인을 한 번에 집계해도 주문별로 갈라 담는다 (N+1 방지의 대가)")
    void listSplitsAggregatePerOrder() {
        OutbOrder first = order(1L, line(11L, 30));
        OutbOrder second = order(2L, line(21L, 40));
        when(outbOrderRepository.search(any(OutbOrderSearchCond.class))).thenReturn(List.of(first, second));
        when(outbAllocRepository.sumAlocQtyByLineIds(anyList())).thenReturn(Map.of(11L, 30L, 21L, 15L));

        List<OutbOrderResponse> res = outbOrderService.list(new OutbOrderSearchCond());

        assertEquals(30, res.get(0).getTotalAlocQty());
        assertEquals(15, res.get(1).getTotalAlocQty());
    }

    @Test
    @DisplayName("라인 — 할당이 없는 라인의 할당수량은 null이 아니라 0이다 (화면이 잔량을 뺄셈한다)")
    void linesDefaultAlocQtyToZero() {
        OutbOrder order = order(1L, line(11L, 30), line(12L, 20));
        when(outbOrderRepository.existsById(1L)).thenReturn(true);
        when(outbLineRepository.findAllByOutbOrderIdWithProd(1L)).thenReturn(order.getLines());
        when(outbAllocRepository.sumAlocQtyByLineIds(anyList())).thenReturn(Map.of(11L, 12L));

        List<OutbLineResponse> res = outbOrderService.lines(1L);

        assertEquals(12L, res.get(0).getAlocQty());
        assertEquals(0L, res.get(1).getAlocQty());
    }

    private OutbOrder order(long id, OutbLine... lines) {
        OutbOrder created = OutbOrder.builder()
                .outbNo("OB-20260803-00" + id).omsOutbOrderId(id).store(store)
                .odrDe(LocalDate.of(2026, 8, 3)).expctDe(LocalDate.of(2026, 8, 10))
                .outbTyp("NRML")
                .build();
        setId(created, id);
        for (OutbLine line : lines) {
            created.addLine(line);
        }
        return created;
    }

    private OutbLine line(long id, long odrQty) {
        OutbLine created = OutbLine.builder().prod(prod).odrQty(odrQty).build();
        setId(created, id);
        return created;
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
