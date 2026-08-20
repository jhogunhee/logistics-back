package com.project.wmsback.inbound.service;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.TmpZon;
import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.PutawayTask;
import com.project.wmsback.inbound.entity.PutawayTaskStatus;
import com.project.wmsback.inbound.repository.IbLineRepository;
import com.project.wmsback.inbound.repository.PutawayTaskQueryRepository;
import com.project.wmsback.inbound.repository.PutawayTaskRepository;
import com.project.wmsback.inventory.service.InvStore;
import com.project.wmsback.inventory.service.LocCapacityService;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.wmsback.warehouse.repository.LocRepository;
import com.project.wmsback.warehouse.repository.LotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 적치지시 로케이션 변경의 동작 명세 — 지시 수정이지 실행 override가 아니다.
 * 예약은 스테이징 재고에 걸려 있어 재고는 건드리지 않고, 검증은 지시 생성 때와 같은 식이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PutawayTaskServiceTest {

    @Mock PutawayTaskRepository putawayTaskRepository;
    @Mock PutawayTaskQueryRepository putawayTaskQueryRepository;
    @Mock IbLineRepository ibLineRepository;
    @Mock LotRepository lotRepository;
    @Mock LocRepository locRepository;
    @Mock InvStore invStore;
    @Mock LocCapacityService locCapacityService;

    private PutawayTaskService service;
    private PutawayTask task;
    private Loc currentLoc;
    private Loc newLoc;

    @BeforeEach
    void setUp() {
        service = new PutawayTaskService(putawayTaskRepository, putawayTaskQueryRepository,
                ibLineRepository, lotRepository, locRepository, invStore, locCapacityService);

        Prod prod = mock(Prod.class);
        when(prod.getProdCd()).thenReturn("PROD-0001");
        when(prod.getTmpZon()).thenReturn(TmpZon.DRY);
        IbLine ibLine = mock(IbLine.class);
        when(ibLine.getProd()).thenReturn(prod);

        currentLoc = storageLoc(100L, "A-01-01");
        newLoc = storageLoc(200L, "A-01-02");

        task = PutawayTask.builder()
                .ibLine(ibLine)
                .lot(mock(Lot.class))
                .toLoc(currentLoc)
                .drctQty(50L)
                .build();

        when(putawayTaskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(locRepository.findById(200L)).thenReturn(Optional.of(newLoc));
        when(locCapacityService.availCapacity(newLoc)).thenReturn(100L);
    }

    private Loc storageLoc(Long id, String locCd) {
        Loc loc = mock(Loc.class);
        when(loc.getId()).thenReturn(id);
        when(loc.getLocCd()).thenReturn(locCd);
        when(loc.getLocTyp()).thenReturn(LocTyp.STORAGE);
        when(loc.getTmpZon()).thenReturn(TmpZon.DRY);
        return loc;
    }

    @Test
    @DisplayName("전량 변경(미실행) — 지시의 목적지만 바뀌고 새 지시도 재고 접촉도 없다")
    void changeLocWhole() {
        service.changeLoc(1L, 200L, 50L);

        assertEquals(newLoc, task.getToLoc());
        verify(putawayTaskRepository, never()).save(any());
        verifyNoInteractions(invStore);
    }

    @Test
    @DisplayName("수량 생략(null) = 잔여 전량으로 해석한다")
    void nullQtyMeansAllRemaining() {
        service.changeLoc(1L, 200L, null);

        assertEquals(newLoc, task.getToLoc());
        verify(putawayTaskRepository, never()).save(any());
    }

    @Test
    @DisplayName("일부 수량 변경 = 분할 — 원 지시는 줄고 잔여분이 새 지시로 떨어져 나간다")
    void splitToNewLoc() {
        service.changeLoc(1L, 200L, 20L);

        assertEquals(30L, task.getDrctQty());
        assertEquals(currentLoc, task.getToLoc());
        assertEquals(PutawayTaskStatus.DIRECTED, task.getStatus());

        ArgumentCaptor<PutawayTask> captor = ArgumentCaptor.forClass(PutawayTask.class);
        verify(putawayTaskRepository).save(captor.capture());
        PutawayTask split = captor.getValue();
        assertEquals(20L, split.getDrctQty());
        assertEquals(newLoc, split.getToLoc());
        assertEquals(task.getIbLine(), split.getIbLine());
        assertEquals(task.getLot(), split.getLot());
        verifyNoInteractions(invStore);
    }

    @Test
    @DisplayName("부분 실행 후 잔여 전량 분할 — 원 지시는 실행분만 남아 완료로 전이한다")
    void splitAllRemainingAfterPartialExecution() {
        task.execute(10L);

        service.changeLoc(1L, 200L, 40L);

        assertEquals(10L, task.getDrctQty());
        assertEquals(PutawayTaskStatus.DONE, task.getStatus());

        ArgumentCaptor<PutawayTask> captor = ArgumentCaptor.forClass(PutawayTask.class);
        verify(putawayTaskRepository).save(captor.capture());
        assertEquals(40L, captor.getValue().getDrctQty());
        assertEquals(newLoc, captor.getValue().getToLoc());
    }

    @Test
    @DisplayName("변경 수량이 잔여수량을 넘으면 거부한다")
    void rejectQtyOverRemaining() {
        task.execute(10L);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.changeLoc(1L, 200L, 41L));
        assertTrue(e.getMessage().contains("잔여"));
        assertEquals(currentLoc, task.getToLoc());
    }

    @Test
    @DisplayName("변경 수량은 1 이상이어야 한다")
    void rejectQtyUnderOne() {
        assertThrows(IllegalArgumentException.class, () -> service.changeLoc(1L, 200L, 0L));
    }

    @Test
    @DisplayName("지시 상태가 아니면 변경할 수 없다")
    void rejectWhenNotDirected() {
        task.cancel();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.changeLoc(1L, 200L, 50L));
        assertTrue(e.getMessage().contains("지시 상태"));
    }

    @Test
    @DisplayName("현재 지시 로케이션과 같은 곳으로는 변경할 수 없다")
    void rejectSameLoc() {
        when(locRepository.findById(100L)).thenReturn(Optional.of(currentLoc));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.changeLoc(1L, 100L, 50L));
        assertTrue(e.getMessage().contains("같"));
    }

    @Test
    @DisplayName("보관 로케이션이 아니면 변경할 수 없다")
    void rejectNonStorageLoc() {
        when(newLoc.getLocTyp()).thenReturn(LocTyp.STAGE);

        assertThrows(IllegalArgumentException.class, () -> service.changeLoc(1L, 200L, 50L));
        assertEquals(currentLoc, task.getToLoc());
    }

    @Test
    @DisplayName("상품 온도대와 다른 로케이션으로는 변경할 수 없다")
    void rejectTmpZonMismatch() {
        when(newLoc.getTmpZon()).thenReturn(TmpZon.FRZ);

        assertThrows(IllegalArgumentException.class, () -> service.changeLoc(1L, 200L, 50L));
        assertEquals(currentLoc, task.getToLoc());
    }

    @Test
    @DisplayName("적재가능수량 검증은 옮기는 수량 기준이다 — 지시 전체가 아니라")
    void capacityCheckedAgainstMoveQty() {
        when(locCapacityService.availCapacity(newLoc)).thenReturn(25L);

        service.changeLoc(1L, 200L, 20L);
        assertEquals(30L, task.getDrctQty());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.changeLoc(1L, 200L, 30L));
        assertTrue(e.getMessage().contains("적재가능"));
    }

    @Test
    @DisplayName("적재가능수량 미설정(무제한) 로케이션으로도 변경할 수 있다")
    void changeLocToUnlimitedCapacity() {
        when(locCapacityService.availCapacity(newLoc)).thenReturn(null);

        service.changeLoc(1L, 200L, 50L);

        assertEquals(newLoc, task.getToLoc());
    }

    @Test
    @DisplayName("존재하지 않는 지시·로케이션은 거부한다")
    void rejectUnknownTaskOrLoc() {
        assertThrows(IllegalArgumentException.class, () -> service.changeLoc(99L, 200L, 50L));
        assertThrows(IllegalArgumentException.class, () -> service.changeLoc(1L, 999L, 50L));
    }
}
