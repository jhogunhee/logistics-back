package com.project.wmsback.warehouse.service;

import com.project.mdm.prod.entity.TmpZon;
import com.project.wmsback.inventory.repository.LocCapacityQueryRepository;
import com.project.wmsback.inventory.repository.LocRefQueryRepository;
import com.project.wmsback.warehouse.dto.LocResponse;
import com.project.wmsback.warehouse.dto.LocSaveRequest;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Zon;
import com.project.wmsback.warehouse.repository.LocRepository;
import com.project.wmsback.warehouse.repository.ZonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 로케이션 저장의 max_qty 검증 — DB 제약(ck_loc_storage_capacity: STORAGE는 NOT NULL,
 * ck_loc_max_qty: 양수)을 커밋 전에 사용자 메시지로 돌려주는지 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocServiceTest {

    @Mock LocRepository locRepository;
    @Mock ZonRepository zonRepository;
    @Mock LocCapacityQueryRepository locCapacityQueryRepository;
    @Mock LocRefQueryRepository locRefQueryRepository;

    private LocService locService;
    private Zon dry;

    @BeforeEach
    void setUp() {
        locService = new LocService(locRepository, zonRepository, locCapacityQueryRepository, locRefQueryRepository);
        dry = mock(Zon.class);
        when(dry.getZonCd()).thenReturn("DRY");
        when(dry.getTmpZon()).thenReturn(TmpZon.DRY);
        when(zonRepository.findAll()).thenReturn(List.of(dry));
        when(locRepository.existsByLocCd("DRY-A-01-01")).thenReturn(false);
    }

    private LocSaveRequest row(String status, LocTyp locTyp, Long maxQty) {
        LocSaveRequest row = new LocSaveRequest();
        row.setStatus(status);
        row.setLocId(10L);
        row.setLocCd("DRY-A-01-01");
        row.setZonCd("DRY");
        row.setTmpZon(TmpZon.DRY);
        row.setLocTyp(locTyp);
        row.setPikngPrty(1);
        row.setPtawyPrty(1);
        row.setMaxQty(maxQty);
        return row;
    }

    @Test
    @DisplayName("STORAGE 신규 등록에 최대 적재 수량이 없으면 거부한다 (ck_loc_storage_capacity 선반영)")
    void create_storageRequiresMaxQty() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> locService.saveAll(List.of(row("C", LocTyp.STORAGE, null))));
        assertTrue(e.getMessage().contains("최대 적재 수량"));
    }

    @Test
    @DisplayName("최대 적재 수량이 0 이하이면 거부한다 (ck_loc_max_qty 선반영)")
    void create_rejectsNonPositiveMaxQty() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> locService.saveAll(List.of(row("C", LocTyp.STORAGE, 0L))));
        assertTrue(e.getMessage().contains("최대 적재 수량"));
    }

    @Test
    @DisplayName("STORAGE 신규 등록 시 최대 적재 수량이 엔티티에 실린다")
    void create_savesMaxQty() {
        locService.saveAll(List.of(row("C", LocTyp.STORAGE, 100L)));

        ArgumentCaptor<Loc> captor = ArgumentCaptor.forClass(Loc.class);
        verify(locRepository).save(captor.capture());
        assertEquals(100L, captor.getValue().getMaxQty());
    }

    @Test
    @DisplayName("STAGE는 최대 적재 수량 없이 등록할 수 있다 (NULL = 무제한)")
    void create_stageAllowsNullMaxQty() {
        locService.saveAll(List.of(row("C", LocTyp.STAGE, null)));

        ArgumentCaptor<Loc> captor = ArgumentCaptor.forClass(Loc.class);
        verify(locRepository).save(captor.capture());
        assertNull(captor.getValue().getMaxQty());
    }

    @Test
    @DisplayName("수정 시 최대 적재 수량이 반영된다")
    void update_appliesMaxQty() {
        Loc loc = Loc.builder()
                .locCd("DRY-A-01-01").zon(dry).tmpZon(TmpZon.DRY)
                .locTyp(LocTyp.STORAGE).pikngPrty(1).ptawyPrty(1).maxQty(50L)
                .build();
        when(locRepository.findById(10L)).thenReturn(Optional.of(loc));

        locService.saveAll(List.of(row("U", LocTyp.STORAGE, 200L)));

        assertEquals(200L, loc.getMaxQty());
    }

    @Test
    @DisplayName("STORAGE를 최대 적재 수량 없이 수정하는 것도 거부한다")
    void update_storageRequiresMaxQty() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> locService.saveAll(List.of(row("U", LocTyp.STORAGE, null))));
        assertTrue(e.getMessage().contains("최대 적재 수량"));
    }

    @Test
    @DisplayName("응답 DTO에 최대 적재 수량이 포함된다")
    void response_includesMaxQty() {
        Loc loc = Loc.builder()
                .locCd("DRY-A-01-01").zon(dry).tmpZon(TmpZon.DRY)
                .locTyp(LocTyp.STORAGE).pikngPrty(1).ptawyPrty(1).maxQty(80L)
                .build();

        assertEquals(80L, LocResponse.from(loc).getMaxQty());
    }
}