package com.project.wmsback.warehouse.service;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.TmpZon;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.wmsback.warehouse.dto.FxngLocSaveRequest;
import com.project.wmsback.warehouse.entity.FxngLoc;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.repository.FxngLocRepository;
import com.project.wmsback.warehouse.repository.LocRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 고정 로케이션 저장의 검증 명세 — STORAGE·온도대 일치·min/max 관계(ck_fxng_loc_qty 선반영)와
 * 로케이션 전용 규칙(uq_fxng_loc 선반영)을 커밋 전에 사용자 메시지로 돌려주는지 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FxngLocServiceTest {

    @Mock FxngLocRepository fxngLocRepository;
    @Mock ProdRepository prodRepository;
    @Mock LocRepository locRepository;

    private FxngLocService service;
    private Prod prod;
    private Loc storage;

    @BeforeEach
    void setUp() {
        service = new FxngLocService(fxngLocRepository, prodRepository, locRepository);

        prod = mock(Prod.class);
        when(prod.getProdCd()).thenReturn("PROD-0001");
        when(prod.getTmpZon()).thenReturn(TmpZon.DRY);
        when(prodRepository.findByProdCd("PROD-0001")).thenReturn(Optional.of(prod));

        storage = mock(Loc.class);
        when(storage.getLocCd()).thenReturn("PIK-DRY-01-01");
        when(storage.getLocTyp()).thenReturn(LocTyp.STORAGE);
        when(storage.getTmpZon()).thenReturn(TmpZon.DRY);
        when(storage.getMaxQty()).thenReturn(200L);
        when(locRepository.findByLocCd("PIK-DRY-01-01")).thenReturn(Optional.of(storage));

        when(fxngLocRepository.findByLoc(any())).thenReturn(Optional.empty());
    }

    private FxngLocSaveRequest row(String status, Long minQty, Long maxQty) {
        FxngLocSaveRequest row = new FxngLocSaveRequest();
        row.setStatus(status);
        row.setFxngLocId(10L);
        row.setProdCd("PROD-0001");
        row.setLocCd("PIK-DRY-01-01");
        row.setMinQty(minQty);
        row.setMaxQty(maxQty);
        return row;
    }

    @Test
    @DisplayName("정상 신규 등록 — 상품·로케이션·min/max가 엔티티에 실린다")
    void create_savesEntity() {
        service.saveAll(List.of(row("C", 50L, 200L)));

        ArgumentCaptor<FxngLoc> captor = ArgumentCaptor.forClass(FxngLoc.class);
        verify(fxngLocRepository).save(captor.capture());
        assertEquals(50L, captor.getValue().getMinQty());
        assertEquals(200L, captor.getValue().getMaxQty());
    }

    @Test
    @DisplayName("보관(STORAGE)이 아닌 로케이션은 고정할 수 없다 — 적치·할당 후보 모집단 밖이라서")
    void create_rejectsStageLoc() {
        when(storage.getLocTyp()).thenReturn(LocTyp.STAGE);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("C", 50L, 200L))));
        assertTrue(e.getMessage().contains("보관 로케이션"));
    }

    @Test
    @DisplayName("상품과 로케이션의 온도대가 다르면 거부한다")
    void create_rejectsTmpZonMismatch() {
        when(prod.getTmpZon()).thenReturn(TmpZon.FRZ);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("C", 50L, 200L))));
        assertTrue(e.getMessage().contains("온도대"));
    }

    @Test
    @DisplayName("재보충점이 보충 상한을 넘으면 거부한다 (ck_fxng_loc_qty 선반영)")
    void create_rejectsMinOverMax() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("C", 300L, 200L))));
        assertTrue(e.getMessage().contains("재보충점"));
    }

    @Test
    @DisplayName("보충 상한이 로케이션 최대 적재 수량을 넘으면 거부한다 — 영영 도달 못 하는 목표라서")
    void create_rejectsMaxOverLocCapacity() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("C", 50L, 500L))));
        assertTrue(e.getMessage().contains("최대 적재 수량"));
    }

    @Test
    @DisplayName("이미 다른 상품이 고정된 로케이션은 거부한다 (uq_fxng_loc 선반영)")
    void create_rejectsOccupiedLoc() {
        FxngLoc existing = mock(FxngLoc.class);
        when(existing.getId()).thenReturn(99L);
        Prod other = mock(Prod.class);
        when(other.getProdCd()).thenReturn("PROD-0002");
        when(existing.getProd()).thenReturn(other);
        when(fxngLocRepository.findByLoc(storage)).thenReturn(Optional.of(existing));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("C", 50L, 200L))));
        assertTrue(e.getMessage().contains("이미 다른 상품"));
    }

    @Test
    @DisplayName("수정 — 자기 자신의 로케이션은 중복이 아니다")
    void update_allowsOwnLoc() {
        FxngLoc self = mock(FxngLoc.class);
        when(self.getId()).thenReturn(10L);
        when(fxngLocRepository.findById(10L)).thenReturn(Optional.of(self));
        when(fxngLocRepository.findByLoc(storage)).thenReturn(Optional.of(self));

        service.saveAll(List.of(row("U", 30L, 150L)));

        verify(self).update(prod, storage, 30L, 150L);
    }

    @Test
    @DisplayName("존재하지 않는 상품 코드는 그 이름으로 거부한다")
    void create_rejectsUnknownProd() {
        FxngLocSaveRequest row = row("C", 50L, 200L);
        row.setProdCd("NOPE");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row)));
        assertTrue(e.getMessage().contains("존재하지 않는 상품"));
    }

    @Test
    @DisplayName("존재하지 않는 로케이션 코드는 그 이름으로 거부한다")
    void create_rejectsUnknownLoc() {
        FxngLocSaveRequest row = row("C", 50L, 200L);
        row.setLocCd("NOPE");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row)));
        assertTrue(e.getMessage().contains("존재하지 않는 로케이션"));
    }

    @Test
    @DisplayName("삭제는 가드 없이 지운다 — 어떤 문서도 고정 로케이션을 참조하지 않는다")
    void delete_removesWithoutGuard() {
        FxngLoc fxng = FxngLoc.builder().prod(prod).loc(storage).minQty(50L).maxQty(200L).build();
        when(fxngLocRepository.findById(10L)).thenReturn(Optional.of(fxng));

        service.saveAll(List.of(row("D", null, null)));

        verify(fxngLocRepository).delete(fxng);
    }
}
