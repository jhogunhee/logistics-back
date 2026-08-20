package com.project.wmsback.inventory.service;

import com.project.wmsback.inventory.dto.InvMovConfirmRequest;
import com.project.wmsback.inventory.dto.InvMovRegisterRequest;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvHist;
import com.project.wmsback.inventory.entity.InvMovDvsn;
import com.project.wmsback.inventory.entity.InvMovStatus;
import com.project.wmsback.inventory.entity.InvMovTask;
import com.project.wmsback.inventory.entity.RefDocTyp;
import com.project.wmsback.inventory.entity.TxTyp;
import com.project.wmsback.inventory.repository.InvHistRepository;
import com.project.wmsback.inventory.repository.InvMovTaskRepository;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.TmpZon;
import com.project.wmsback.warehouse.repository.LocRepository;
import com.project.mdm.nbr.service.NbrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 이동지시 2단계(지시=예약 → 확정=실물 MOVE)의 검증·재고 반영 규칙.
 * 재고 정합성(aloc 증감, MOVE 2행, 0행 삭제)은 실제 엔티티 상태로 확인하고 저장소만 목으로 둔다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvMovServiceTest {

    @Mock InvRepository invRepository;
    @Mock InvHistRepository invHistRepository;
    @Mock InvMovTaskRepository invMovTaskRepository;
    @Mock LocRepository locRepository;
    @Mock LocCapacityService locCapacityService;
    @Mock NbrService nbrService;

    // 재고 쓰기 포트는 목이 아니라 실물을 쓴다 — aloc 증감·MOVE 2행·0행 삭제가 검증 대상이기 때문
    private InvMovService invMovService;

    private Prod prod;
    private Lot lot;
    private Loc fromLoc;
    private Loc toLoc;
    private Inv fromInv;

    @BeforeEach
    void setUp() {
        invMovService = new InvMovService(invRepository, new InvStore(invRepository, invHistRepository),
                invMovTaskRepository, locRepository, locCapacityService, nbrService);

        prod = mock(Prod.class);
        when(prod.getId()).thenReturn(1L);
        when(prod.getProdCd()).thenReturn("PROD-0001");
        when(prod.getTmpZon()).thenReturn(TmpZon.DRY);

        lot = mock(Lot.class);
        when(lot.getId()).thenReturn(5L);

        fromLoc = mock(Loc.class);
        when(fromLoc.getId()).thenReturn(10L);
        when(fromLoc.getLocCd()).thenReturn("DRY-A-01-01");
        when(fromLoc.getLocTyp()).thenReturn(LocTyp.STORAGE);

        toLoc = mock(Loc.class);
        when(toLoc.getId()).thenReturn(20L);
        when(toLoc.getLocCd()).thenReturn("DRY-B-01-01");
        when(toLoc.getLocTyp()).thenReturn(LocTyp.STORAGE);
        when(toLoc.getTmpZon()).thenReturn(TmpZon.DRY);
        when(toLoc.getMaxQty()).thenReturn(100L);

        // 보유 10 / 예약 0 인 FROM 재고
        fromInv = Inv.builder().prod(prod).loc(fromLoc).lot(lot).build();
        fromInv.increaseOnHand(10L);

        // 등록의 선락 경로: id → 키 선조회 → 키 락 (InvStore.lockAllByIds)
        when(invRepository.findLockKeysByIdIn(any()))
                .thenReturn(List.of(new InvLockKey(100L, 1L, 10L, 5L)));
        when(invRepository.findByKeyForUpdate(1L, 10L, 5L)).thenReturn(Optional.of(fromInv));
        // 도착 로케이션은 선락한다 — 동시 등록이 적재가능수량을 함께 넘는 것을 막는 지점
        when(locRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(toLoc));
        // 적재가능수량 계산은 LocCapacityService가 단일 정의를 갖는다 (적치지시 유입분도 그쪽에서 합산)
        when(locCapacityService.availCapacity(toLoc)).thenReturn(100L);
        when(nbrService.issue(anyString(), any(LocalDate.class))).thenReturn("MV-20260803-001");

        // 확정의 선락 경로: 지시 id → FROM·TO 키 선조회 → 키 락 (InvStore.lockAll)
        when(invMovTaskRepository.findLockKeysByIdIn(any()))
                .thenReturn(List.of(new InvMovLockKey(1L, 1L, 5L, 10L, 20L)));

        when(invMovTaskRepository.save(any(InvMovTask.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invRepository.save(any(Inv.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private InvMovRegisterRequest request(Long invId, Long toLocId, Long qty) {
        InvMovRegisterRequest.Item item = new InvMovRegisterRequest.Item();
        item.setInvId(invId);
        item.setToLocId(toLocId);
        item.setQty(qty);
        InvMovRegisterRequest req = new InvMovRegisterRequest();
        req.setItems(List.of(item));
        return req;
    }

    private InvMovConfirmRequest.Item confirmItem(Long taskId, Long qty) {
        InvMovConfirmRequest.Item item = new InvMovConfirmRequest.Item();
        item.setTaskId(taskId);
        item.setQty(qty);
        return item;
    }

    private InvMovConfirmRequest confirmRequest(Long taskId, Long qty) {
        InvMovConfirmRequest req = new InvMovConfirmRequest();
        req.setItems(List.of(confirmItem(taskId, qty)));
        return req;
    }

    private InvMovTask task(long drctQty) {
        return task(drctQty, InvMovDvsn.INV_MOV);
    }

    private InvMovTask task(long drctQty, InvMovDvsn movDvsn) {
        return InvMovTask.builder()
                .invMovNo("MV-20260803-001")
                .movDvsn(movDvsn)
                .prod(prod).lot(lot).fromLoc(fromLoc).toLoc(toLoc)
                .drctQty(drctQty)
                .build();
    }

    // ---------- 등록 (예약) ----------

    @Test
    @DisplayName("등록: 이동수량이 0 이하이면 거부")
    void register_rejectsNonPositiveQty() {
        assertThrows(IllegalArgumentException.class, () -> invMovService.register(request(100L, 20L, 0L)));
    }

    @Test
    @DisplayName("등록: 스테이징 재고는 이동 대상이 아니다 (보관 로케이션만)")
    void register_rejectsNonStorageFrom() {
        when(fromLoc.getLocTyp()).thenReturn(LocTyp.STAGE);
        assertThrows(IllegalArgumentException.class, () -> invMovService.register(request(100L, 20L, 5L)));
    }

    @Test
    @DisplayName("등록: 출발지 = 도착지 거부")
    void register_rejectsSameLoc() {
        when(toLoc.getId()).thenReturn(10L);
        assertThrows(IllegalArgumentException.class, () -> invMovService.register(request(100L, 20L, 5L)));
    }

    @Test
    @DisplayName("등록: 도착지가 보관 로케이션이 아니면 거부")
    void register_rejectsNonStorageTo() {
        when(toLoc.getLocTyp()).thenReturn(LocTyp.STAGE);
        assertThrows(IllegalArgumentException.class, () -> invMovService.register(request(100L, 20L, 5L)));
    }

    @Test
    @DisplayName("등록: 상품 온도대와 도착지 온도대가 다르면 거부")
    void register_rejectsTempZoneMismatch() {
        when(toLoc.getTmpZon()).thenReturn(TmpZon.FRZ);
        assertThrows(IllegalArgumentException.class, () -> invMovService.register(request(100L, 20L, 5L)));
    }

    @Test
    @DisplayName("등록: 가용재고(onHand - aloc) 초과 거부")
    void register_rejectsOverAvailable() {
        fromInv.reserve(4L); // 가용 6
        assertThrows(IllegalArgumentException.class, () -> invMovService.register(request(100L, 20L, 7L)));
    }

    @Test
    @DisplayName("등록: 도착지 적재가능수량(max_qty - 현재고 - 미완료 유입 잔량) 초과 거부")
    void register_rejectsOverCapacity() {
        when(locCapacityService.availCapacity(toLoc)).thenReturn(5L);
        assertThrows(IllegalArgumentException.class, () -> invMovService.register(request(100L, 20L, 6L)));
    }

    @Test
    @DisplayName("등록: 최대 적재 수량 미설정(null)이면 용량 제한 없이 통과")
    void register_allowsWhenCapacityUnset() {
        when(locCapacityService.availCapacity(toLoc)).thenReturn(null);
        assertEquals(1, invMovService.register(request(100L, 20L, 6L)).size());
    }

    @Test
    @DisplayName("등록 성공: FROM 예약(aloc) 증가 + DIRECTED 지시 저장 + 번호 반환. 이력은 남기지 않는다")
    void register_reservesAndSavesTask() {
        List<String> movNos = invMovService.register(request(100L, 20L, 6L));

        assertEquals(List.of("MV-20260803-001"), movNos);
        assertEquals(6L, fromInv.getAlocQty());
        assertEquals(10L, fromInv.getOnHandQty()); // 물리 이동 없음
        ArgumentCaptor<InvMovTask> captor = ArgumentCaptor.forClass(InvMovTask.class);
        verify(invMovTaskRepository).save(captor.capture());
        assertEquals(InvMovStatus.DIRECTED, captor.getValue().getStatus());
        assertEquals(6L, captor.getValue().getDrctQty());
        assertEquals(0L, captor.getValue().getCmplQty());
        verify(invHistRepository, never()).save(any()); // 예약은 물리 이동이 아니다
    }

    // ---------- 확정 (실물 MOVE) ----------

    @Test
    @DisplayName("확정: 재고이동 유형이 아닌 지시(적치)는 이 경로에서 거부")
    void confirm_rejectsNonInvMovDvsn() {
        when(invMovTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(task(6L, InvMovDvsn.PTAWY)));
        assertThrows(IllegalArgumentException.class, () -> invMovService.confirm(confirmRequest(1L, 1L)));
        verify(invHistRepository, never()).save(any());
    }

    @Test
    @DisplayName("취소: 재고이동 유형이 아닌 지시(적치)는 이 경로에서 거부")
    void cancel_rejectsNonInvMovDvsn() {
        when(invMovTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(task(6L, InvMovDvsn.PTAWY)));
        assertThrows(IllegalArgumentException.class, () -> invMovService.cancel(1L));
    }

    @Test
    @DisplayName("등록: 이 화면 경로의 이동구분은 재고이동(INV_MOV) 고정")
    void register_fixesMovDvsnToInvMov() {
        invMovService.register(request(100L, 20L, 6L));
        ArgumentCaptor<InvMovTask> captor = ArgumentCaptor.forClass(InvMovTask.class);
        verify(invMovTaskRepository).save(captor.capture());
        assertEquals(InvMovDvsn.INV_MOV, captor.getValue().getMovDvsn());
    }

    @Test
    @DisplayName("확정: DIRECTED가 아닌 지시는 거부")
    void confirm_rejectsNonDirected() {
        InvMovTask done = task(4L);
        done.confirm(4L); // DONE
        when(invMovTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(done));
        assertThrows(IllegalArgumentException.class, () -> invMovService.confirm(confirmRequest(1L, 1L)));
    }

    @Test
    @DisplayName("확정: 잔여수량 초과 거부 (서버 재검증)")
    void confirm_rejectsOverRemaining() {
        when(invMovTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(task(6L)));
        assertThrows(IllegalArgumentException.class, () -> invMovService.confirm(confirmRequest(1L, 7L)));
    }

    @Test
    @DisplayName("부분확정: MOVE 2행(-/+, 같은 from/to, 지시번호 참조) + onHand·aloc 소진 + DIRECTED 유지")
    void confirm_partial() {
        fromInv.reserve(6L);
        InvMovTask directed = task(6L);
        when(invMovTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(directed));
        when(invRepository.findByKeyForUpdate(1L, 10L, 5L)).thenReturn(Optional.of(fromInv));

        invMovService.confirm(confirmRequest(1L, 4L));

        assertEquals(6L, fromInv.getOnHandQty());
        assertEquals(2L, fromInv.getAlocQty()); // 예약 소진
        assertEquals(4L, directed.getCmplQty());
        assertEquals(InvMovStatus.DIRECTED, directed.getStatus());
        assertNull(directed.getCmplDt());

        ArgumentCaptor<InvHist> captor = ArgumentCaptor.forClass(InvHist.class);
        verify(invHistRepository, times(2)).save(captor.capture());
        List<InvHist> hists = captor.getAllValues();
        assertEquals(-4L, hists.get(0).getQty());
        assertEquals(4L, hists.get(1).getQty());
        for (InvHist h : hists) {
            assertEquals(TxTyp.MOVE, h.getTxTyp());
            assertEquals(RefDocTyp.INV_MOV, h.getRfnDocTyp());
            assertEquals("MV-20260803-001", h.getRfnDocNo());
            assertEquals(10L, h.getFromLocId());
            assertEquals(20L, h.getToLocId());
        }
        verify(invRepository, never()).delete(any(Inv.class));
    }

    @Test
    @DisplayName("전량확정: DONE 전이 + 완료시각 기록, FROM이 0/0이면 스냅샷 행 삭제")
    void confirm_fullMovesAllAndDeletesEmptyRow() {
        fromInv.reserve(10L);
        InvMovTask directed = task(10L);
        when(invMovTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(directed));
        when(invRepository.findByKeyForUpdate(1L, 10L, 5L)).thenReturn(Optional.of(fromInv));

        invMovService.confirm(confirmRequest(1L, 10L));

        assertEquals(InvMovStatus.DONE, directed.getStatus());
        assertNotNull(directed.getCmplDt());
        assertEquals(0L, fromInv.getOnHandQty());
        assertEquals(0L, fromInv.getAlocQty());
        verify(invRepository).delete(fromInv);
    }

    @Test
    @DisplayName("확정: 예약된 재고 행이 없으면 정합성 오류")
    void confirm_missingReservedInvIsIllegalState() {
        when(invMovTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(task(6L)));
        when(invRepository.findByKeyForUpdate(anyLong(), anyLong(), anyLong())).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> invMovService.confirm(confirmRequest(1L, 1L)));
    }

    @Test
    @DisplayName("확정: 같은 지시가 두 번 실린 요청은 거부 (잔여를 두 번 깎는 요청)")
    void confirm_rejectsDuplicateTask() {
        InvMovConfirmRequest req = new InvMovConfirmRequest();
        req.setItems(List.of(confirmItem(1L, 1L), confirmItem(1L, 2L)));

        assertThrows(IllegalArgumentException.class, () -> invMovService.confirm(req));
        verify(invHistRepository, never()).save(any());
    }

    @Test
    @DisplayName("다건확정: 같은 FROM 행을 공유하는 두 지시를 한 트랜잭션에서 각각 소진")
    void confirm_multipleTasksSharingFromRow() {
        fromInv.reserve(9L); // 두 지시의 예약 합 (6 + 3)
        InvMovTask first = task(6L);
        InvMovTask second = task(3L);
        when(invMovTaskRepository.findLockKeysByIdIn(any())).thenReturn(List.of(
                new InvMovLockKey(1L, 1L, 5L, 10L, 20L),
                new InvMovLockKey(2L, 1L, 5L, 10L, 20L)));
        when(invMovTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(first));
        when(invMovTaskRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(second));
        when(invRepository.findByKeyForUpdate(1L, 10L, 5L)).thenReturn(Optional.of(fromInv));

        InvMovConfirmRequest req = new InvMovConfirmRequest();
        req.setItems(List.of(confirmItem(1L, 6L), confirmItem(2L, 3L)));
        invMovService.confirm(req);

        assertEquals(InvMovStatus.DONE, first.getStatus());
        assertEquals(InvMovStatus.DONE, second.getStatus());
        assertEquals(1L, fromInv.getOnHandQty()); // 10 - 9
        assertEquals(0L, fromInv.getAlocQty());
        verify(invHistRepository, times(4)).save(any()); // 지시마다 MOVE 2행
        verify(invRepository, never()).delete(any(Inv.class)); // 보유 1이 남아 행은 유지된다
        // 선락이 잡은 키 — 두 지시가 같은 FROM 행을 가리키므로 락은 한 번이다 (선조회 인자 순서도 여기서 굳는다)
        verify(invRepository).findByKeyForUpdate(1L, 10L, 5L);
        verify(invRepository, atLeastOnce()).findByKeyForUpdate(1L, 20L, 5L);
    }

    // ---------- 취소 (예약 해제) ----------

    @Test
    @DisplayName("전량취소(실적 없음): CANCELLED 전이(행 보존) + 예약 전량 해제. 이력은 남기지 않는다")
    void cancel_wholeBecomesCancelled() {
        fromInv.reserve(6L);
        InvMovTask directed = task(6L);
        when(invMovTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(directed));
        when(invRepository.findByKeyForUpdate(1L, 10L, 5L)).thenReturn(Optional.of(fromInv));

        invMovService.cancel(1L);

        assertEquals(InvMovStatus.CANCELLED, directed.getStatus());
        assertEquals(0L, fromInv.getAlocQty());
        assertEquals(10L, fromInv.getOnHandQty());
        verify(invHistRepository, never()).save(any());
        verify(invMovTaskRepository, never()).delete(any(InvMovTask.class));
    }

    @Test
    @DisplayName("부분확정 후 잔량취소: 지시수량을 완료수량으로 차감하고 DONE 전이")
    void cancel_afterPartialConfirmShrinksAndCompletes() {
        fromInv.reserve(6L);
        InvMovTask directed = task(6L);
        directed.confirm(2L); // 부분확정 상태 (잔여 4)
        fromInv.decreaseOnHand(2L);
        fromInv.release(2L);  // 확정분 소진 반영 (보유 8 / 예약 4)
        when(invMovTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(directed));
        when(invRepository.findByKeyForUpdate(1L, 10L, 5L)).thenReturn(Optional.of(fromInv));

        invMovService.cancel(1L);

        assertEquals(InvMovStatus.DONE, directed.getStatus());
        assertEquals(2L, directed.getDrctQty());
        assertEquals(2L, directed.getCmplQty());
        assertNotNull(directed.getCmplDt());
        assertEquals(0L, fromInv.getAlocQty()); // 잔여 4 해제
        assertEquals(8L, fromInv.getOnHandQty());
    }

    @Test
    @DisplayName("취소: DIRECTED가 아닌 지시는 거부")
    void cancel_rejectsNonDirected() {
        InvMovTask cancelled = task(6L);
        cancelled.cancelRemainder(); // CANCELLED
        when(invMovTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(cancelled));
        assertThrows(IllegalArgumentException.class, () -> invMovService.cancel(1L));
        assertTrue(cancelled.getStatus() == InvMovStatus.CANCELLED);
    }
}
