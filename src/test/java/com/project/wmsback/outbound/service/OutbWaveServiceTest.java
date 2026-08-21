package com.project.wmsback.outbound.service;

import com.project.mdm.nbr.service.NbrService;
import com.project.mdm.store.entity.Store;
import com.project.wmsback.outbound.dto.OutbWaveOrdersRequest;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.entity.WavRegTyp;
import com.project.wmsback.outbound.entity.WaveStatus;
import com.project.wmsback.outbound.repository.OutbOrderRepository;
import com.project.wmsback.outbound.repository.OutbWaveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 웨이브 편성의 가드. 저장소는 목으로 두고 <b>엔티티의 실제 상태 전이</b>(주문의 wave 참조,
 * 웨이브 PLANNED/ISSUED)로 검증한다 — {@code PikngTaskServiceTest}와 같은 방식.
 *
 * <p>여기서 지키는 것은 셋이다: ① 발행된 웨이브는 편성을 못 바꾼다 ② 할당이 시작된 주문이
 * 있으면 담기·해체가 막힌다 ③ 한 웨이브의 주문은 출고예정일이 같다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutbWaveServiceTest {

    @Mock
    private OutbWaveRepository outbWaveRepository;
    @Mock
    private OutbOrderRepository outbOrderRepository;
    @Mock
    private NbrService nbrService;

    @InjectMocks
    private OutbWaveService outbWaveService;

    private static final LocalDate EXPCT_DE = LocalDate.of(2026, 8, 21);

    private Store store;
    private OutbWave wave;
    private long seq = 0;

    @BeforeEach
    void setUp() {
        store = mock(Store.class);
        wave = OutbWave.builder().wavNo("WV-20260821-001").build();
        setId(wave, 100L);
        when(outbWaveRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(wave));
        when(outbOrderRepository.findByWaveId(100L)).thenReturn(List.of());
    }

    // ── 생성 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("주문 없이도 웨이브를 만든다 — 빈 웨이브에 나중에 담는다")
    void createAllowsEmptyWave() {
        when(nbrService.issue("OUTB_WAV_NO", LocalDate.now())).thenReturn("WV-20260821-002");
        when(outbWaveRepository.save(any(OutbWave.class))).thenAnswer(i -> {
            OutbWave saved = i.getArgument(0);
            setId(saved, 200L);
            return saved;
        });

        Long wavId = outbWaveService.create(new OutbWaveOrdersRequest());

        assertEquals(200L, wavId);
        verify(outbOrderRepository, never()).findByIdForUpdate(any());
    }

    // ── 주문 추가 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("발행된 웨이브에는 주문을 담을 수 없다")
    void addOrdersRejectsIssuedWave() {
        wave.issue();

        assertThrows(IllegalStateException.class,
                () -> outbWaveService.addOrders(100L, orders(1L)));

        verify(outbOrderRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("할당이 시작된 주문이 있으면 담기를 막는다 — 담긴 주문 전체가 판정 대상이다")
    void addOrdersRejectsWhenAllocationStarted() {
        OutbOrder allocated = order("OB-001", EXPCT_DE);
        allocated.assignWave(wave, WavRegTyp.MANUAL);
        allocated.recalcStatus(1, 1, 0);
        when(outbOrderRepository.findByWaveId(100L)).thenReturn(List.of(allocated));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> outbWaveService.addOrders(100L, orders(9L)));

        assertTrue(e.getMessage().contains("할당이 시작된 주문"));
        verify(outbOrderRepository, never()).findByIdForUpdate(any());
    }

    /**
     * 웨이브는 정의상 <b>같은 날 나갈 주문</b>을 묶는 단위다. 이 가드가 없으면 한 웨이브의
     * 피킹지시가 서로 다른 날짜의 물량을 한 동선에 섞는다.
     */
    @Test
    @DisplayName("출고예정일이 다른 주문은 같은 웨이브에 담기지 않는다")
    void addOrdersRejectsDifferentExpctDe() {
        OutbOrder mounted = order("OB-001", EXPCT_DE);
        mounted.assignWave(wave, WavRegTyp.MANUAL);
        when(outbOrderRepository.findByWaveId(100L)).thenReturn(List.of(mounted));

        OutbOrder other = order("OB-002", EXPCT_DE.plusDays(1));
        when(outbOrderRepository.findByIdForUpdate(other.getId())).thenReturn(Optional.of(other));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> outbWaveService.addOrders(100L, orders(other.getId())));

        assertTrue(e.getMessage().contains("출고예정일이 다른 주문"));
        assertNull(other.getWave());
    }

    @Test
    @DisplayName("빈 웨이브는 첫 주문이 출고예정일을 정하고, 같은 날짜면 함께 담긴다")
    void addOrdersLetsFirstOrderFixTheDate() {
        OutbOrder first = order("OB-001", EXPCT_DE);
        OutbOrder second = order("OB-002", EXPCT_DE);
        when(outbOrderRepository.findByIdForUpdate(first.getId())).thenReturn(Optional.of(first));
        when(outbOrderRepository.findByIdForUpdate(second.getId())).thenReturn(Optional.of(second));

        outbWaveService.addOrders(100L, orders(first.getId(), second.getId()));

        assertEquals(wave, first.getWave());
        assertEquals(wave, second.getWave());
        assertEquals(WavRegTyp.MANUAL, first.getWavRegTyp());
    }

    /**
     * 주문 행을 id 오름차순으로 잠근다 — 전략 실행의 잠금 순서와 같아야 교착이 없다.
     * 요청 순서가 아니라 <b>정렬된 순서</b>로 잠그는 것이 핵심이다.
     */
    @Test
    @DisplayName("주문 행 락을 id 오름차순으로 잡는다 — 요청 순서와 무관하게")
    void addOrdersLocksOrdersInAscendingId() {
        OutbOrder a = order("OB-001", EXPCT_DE);
        OutbOrder b = order("OB-002", EXPCT_DE);
        OutbOrder c = order("OB-003", EXPCT_DE);
        when(outbOrderRepository.findByIdForUpdate(a.getId())).thenReturn(Optional.of(a));
        when(outbOrderRepository.findByIdForUpdate(b.getId())).thenReturn(Optional.of(b));
        when(outbOrderRepository.findByIdForUpdate(c.getId())).thenReturn(Optional.of(c));

        outbWaveService.addOrders(100L, orders(c.getId(), a.getId(), b.getId()));

        InOrder locking = inOrder(outbOrderRepository);
        locking.verify(outbOrderRepository).findByIdForUpdate(a.getId());
        locking.verify(outbOrderRepository).findByIdForUpdate(b.getId());
        locking.verify(outbOrderRepository).findByIdForUpdate(c.getId());
    }

    // ── 편성 해제 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("다른 웨이브의 주문은 편성 해제 대상이 아니다")
    void unassignRejectsForeignOrder() {
        OutbWave other = OutbWave.builder().wavNo("WV-20260821-999").build();
        setId(other, 101L);
        OutbOrder foreign = order("OB-001", EXPCT_DE);
        foreign.assignWave(other, WavRegTyp.MANUAL);
        when(outbOrderRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> outbWaveService.unassignOrders(100L, orders(foreign.getId())));

        assertTrue(e.getMessage().contains("이 웨이브에 편성된 주문이 아닙니다"));
        assertEquals(other, foreign.getWave());
    }

    @Test
    @DisplayName("해제할 주문을 고르지 않으면 거부한다")
    void unassignRejectsEmptySelection() {
        assertThrows(IllegalArgumentException.class,
                () -> outbWaveService.unassignOrders(100L, new OutbWaveOrdersRequest()));
    }

    @Test
    @DisplayName("편성 해제하면 주문은 지워지지 않고 미편성으로 돌아간다")
    void unassignDetachesOrderWithoutDeleting() {
        OutbOrder mounted = order("OB-001", EXPCT_DE);
        mounted.assignWave(wave, WavRegTyp.MANUAL);
        when(outbOrderRepository.findById(mounted.getId())).thenReturn(Optional.of(mounted));

        outbWaveService.unassignOrders(100L, orders(mounted.getId()));

        assertNull(mounted.getWave());
        assertNull(mounted.getWavRegTyp());
        verify(outbOrderRepository, never()).delete(any());
    }

    @Test
    @DisplayName("발행된 웨이브는 편성 해제도 막힌다")
    void unassignRejectsIssuedWave() {
        wave.issue();
        assertThrows(IllegalStateException.class,
                () -> outbWaveService.unassignOrders(100L, orders(1L)));
    }

    // ── 해체 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("해체하면 소속 주문이 전부 미편성으로 돌아가고 웨이브가 지워진다")
    void disbandDetachesAllOrdersAndDeletesWave() {
        OutbOrder a = order("OB-001", EXPCT_DE);
        OutbOrder b = order("OB-002", EXPCT_DE);
        a.assignWave(wave, WavRegTyp.MANUAL);
        b.assignWave(wave, WavRegTyp.MANUAL);
        when(outbOrderRepository.findByWaveId(100L)).thenReturn(List.of(a, b));

        outbWaveService.disband(100L);

        assertNull(a.getWave());
        assertNull(b.getWave());
        verify(outbWaveRepository).delete(wave);
    }

    @Test
    @DisplayName("할당이 시작된 주문이 있으면 해체를 막는다 — 웨이브는 남는다")
    void disbandRejectsWhenAllocationStarted() {
        OutbOrder allocated = order("OB-001", EXPCT_DE);
        allocated.assignWave(wave, WavRegTyp.MANUAL);
        allocated.recalcStatus(1, 1, 0);
        when(outbOrderRepository.findByWaveId(100L)).thenReturn(List.of(allocated));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> outbWaveService.disband(100L));

        assertTrue(e.getMessage().contains("할당이 시작된 주문"));
        assertEquals(wave, allocated.getWave());
        verify(outbWaveRepository, never()).delete(any());
    }

    @Test
    @DisplayName("발행된 웨이브는 해체할 수 없다")
    void disbandRejectsIssuedWave() {
        wave.issue();

        assertThrows(IllegalStateException.class, () -> outbWaveService.disband(100L));

        assertEquals(WaveStatus.ISSUED, wave.getStatus());
        verify(outbWaveRepository, never()).delete(any());
    }

    @Test
    @DisplayName("존재하지 않는 웨이브는 거부한다")
    void rejectsUnknownWave() {
        when(outbWaveRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> outbWaveService.disband(999L));
    }

    // ── 픽스처 ───────────────────────────────────────────────────────────────

    private OutbOrder order(String outbNo, LocalDate expctDe) {
        OutbOrder created = OutbOrder.builder()
                .outbNo(outbNo).omsOutbOrderId(++seq).store(store)
                .odrDe(EXPCT_DE.minusDays(1)).expctDe(expctDe).outbTyp("NRML")
                .build();
        setId(created, seq);
        return created;
    }

    private OutbWaveOrdersRequest orders(Long... orderIds) {
        OutbWaveOrdersRequest request = new OutbWaveOrdersRequest();
        request.setOrderIds(List.of(orderIds));
        return request;
    }

    /** 엔티티 id는 DB가 채우므로 테스트에서는 리플렉션으로 넣는다 (기존 테스트들과 같은 방식) */
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
