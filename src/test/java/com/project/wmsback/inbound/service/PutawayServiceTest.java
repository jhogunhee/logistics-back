package com.project.wmsback.inbound.service;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.TmpZon;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.PutawayTask;
import com.project.wmsback.inbound.repository.IbLineRepository;
import com.project.wmsback.inbound.repository.PutawayTaskRepository;
import com.project.wmsback.inventory.service.InvStore;
import com.project.wmsback.inventory.service.LocCapacityService;
import com.project.wmsback.inbound.dto.PutawayLocCandidateResponse;
import com.project.wmsback.warehouse.entity.BizDvsn;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.wmsback.warehouse.entity.Zon;
import com.project.wmsback.warehouse.repository.LocRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 적치 실행의 라인 상한 선검증 — 지시수량이 라인의 미적치(검수 − 적치누계)를 넘으면
 * DB CHECK(ck_ib_line_qty)까지 가기 전에 원인이 읽히는 메시지로 거부한다.
 * 정상 데이터에선 스테이징 예약이 이 상한을 넘을 수 없으므로 정합성 오류 계열이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PutawayServiceTest {

    @Mock PutawayTaskRepository putawayTaskRepository;
    @Mock IbLineRepository ibLineRepository;
    @Mock LocRepository locRepository;
    @Mock ProdRepository prodRepository;
    @Mock InvStore invStore;
    @Mock LocCapacityService locCapacityService;

    private PutawayService service;
    private IbLine ibLine;
    private PutawayTask task;

    @BeforeEach
    void setUp() {
        service = new PutawayService(putawayTaskRepository, ibLineRepository,
                locRepository, prodRepository, invStore, locCapacityService);

        Prod prod = mock(Prod.class);
        when(prod.getId()).thenReturn(7L);
        when(prod.getProdCd()).thenReturn("PROD-0014");
        when(prod.getTmpZon()).thenReturn(TmpZon.DRY);
        ibLine = mock(IbLine.class);
        when(ibLine.getProd()).thenReturn(prod);

        Loc toLoc = mock(Loc.class);
        when(toLoc.getId()).thenReturn(20L);
        when(toLoc.getLocTyp()).thenReturn(LocTyp.STORAGE);
        when(toLoc.getTmpZon()).thenReturn(TmpZon.DRY);
        when(toLoc.getLocCd()).thenReturn("DRY-C-01-01");

        Lot lot = mock(Lot.class);
        when(lot.getId()).thenReturn(3L);
        task = PutawayTask.builder()
                .ibLine(ibLine)
                .lot(lot)
                .toLoc(toLoc)
                .drctQty(38L)
                .build();

        // 락 순서(상품 → 재고 → 지시)를 지나는 선조회 — 지시는 락을 잡은 뒤에 읽는다
        Loc staging = mock(Loc.class);
        when(staging.getId()).thenReturn(99L);
        when(locRepository.findByLocCd("RCV-STAGE")).thenReturn(Optional.of(staging));
        when(putawayTaskRepository.findLockKeysByIdIn(any()))
                .thenReturn(List.of(new PutawayLockKey(1L, 7L, 3L, 20L)));
        when(invStore.lockAll(any())).thenReturn(Map.of());
        when(putawayTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
    }

    @Test
    @DisplayName("라인 미적치(검수 − 적치누계)를 넘는 실행은 정합성 오류 메시지로 거부한다")
    void rejectWhenQtyExceedsLineReceived() {
        when(ibLine.getRcvdQty()).thenReturn(48L);
        when(ibLine.getPtawyQty()).thenReturn(26L); // 미적치 22 < 지시 38

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.execute(1L, 38L));
        assertTrue(e.getMessage().contains("검수"));
        assertTrue(e.getMessage().contains("22"));
        // 락은 읽기보다 먼저라 lockAll은 지나지만, 재고를 움직이는 호출까지는 가지 않는다
        verify(invStore, never()).release(any(), anyLong());
        verify(invStore, never()).move(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("반품존 로케이션은 수동 지시 후보에서 빠진다 — RtngsLocResolver.inRtngsZon")
    void candidateLocs_excludesRtngsZone() {
        when(ibLine.getId()).thenReturn(100L);
        when(ibLineRepository.findById(100L)).thenReturn(Optional.of(ibLine));

        Zon storageZon = mock(Zon.class);
        when(storageZon.getBizDvsn()).thenReturn(BizDvsn.STRG);
        when(storageZon.getZonCd()).thenReturn("DRY-A");
        Loc storageLoc = mock(Loc.class);
        when(storageLoc.getLocCd()).thenReturn("DRY-A-01-01");
        when(storageLoc.getZon()).thenReturn(storageZon);

        Zon rtngsZon = mock(Zon.class);
        when(rtngsZon.getBizDvsn()).thenReturn(BizDvsn.RTNGS);
        Loc rtngsLoc = mock(Loc.class);
        when(rtngsLoc.getZon()).thenReturn(rtngsZon);

        when(locRepository.findAllByTmpZonAndLocTypOrderByPtawyPrtyAsc(TmpZon.DRY, LocTyp.STORAGE))
                .thenReturn(List.of(storageLoc, rtngsLoc));

        List<PutawayLocCandidateResponse> result = service.candidateLocs(100L);

        assertEquals(1, result.size());
        assertEquals("DRY-A-01-01", result.get(0).getLocCd());
    }
}
