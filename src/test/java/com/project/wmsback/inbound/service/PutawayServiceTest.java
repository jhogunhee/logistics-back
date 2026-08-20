package com.project.wmsback.inbound.service;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.TmpZon;
import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.PutawayTask;
import com.project.wmsback.inbound.repository.IbLineRepository;
import com.project.wmsback.inbound.repository.PutawayTaskRepository;
import com.project.wmsback.inventory.service.InvStore;
import com.project.wmsback.inventory.service.LocCapacityService;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.wmsback.warehouse.repository.LocRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock InvStore invStore;
    @Mock LocCapacityService locCapacityService;

    private PutawayService service;
    private IbLine ibLine;
    private PutawayTask task;

    @BeforeEach
    void setUp() {
        service = new PutawayService(putawayTaskRepository, ibLineRepository,
                locRepository, invStore, locCapacityService);

        Prod prod = mock(Prod.class);
        when(prod.getProdCd()).thenReturn("PROD-0014");
        when(prod.getTmpZon()).thenReturn(TmpZon.DRY);
        ibLine = mock(IbLine.class);
        when(ibLine.getProd()).thenReturn(prod);

        Loc toLoc = mock(Loc.class);
        when(toLoc.getLocTyp()).thenReturn(LocTyp.STORAGE);
        when(toLoc.getTmpZon()).thenReturn(TmpZon.DRY);
        when(toLoc.getLocCd()).thenReturn("DRY-C-01-01");

        task = PutawayTask.builder()
                .ibLine(ibLine)
                .lot(mock(Lot.class))
                .toLoc(toLoc)
                .drctQty(38L)
                .build();
        when(putawayTaskRepository.findById(1L)).thenReturn(Optional.of(task));
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
        verifyNoInteractions(invStore);
    }
}
