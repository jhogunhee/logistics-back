package com.project.wmsback.inventory.service;

import com.project.mdm.prod.entity.TmpZon;
import com.project.wmsback.inventory.dto.LocMapResponse;
import com.project.wmsback.inventory.repository.InvAlocRecRepository;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.inventory.repository.LocMapQueryRepository;
import com.project.wmsback.inventory.repository.LocMapQueryRepository.LocRow;
import com.project.wmsback.inventory.repository.LocMapQueryRepository.QtySums;
import com.project.wmsback.warehouse.entity.BizDvsn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 로케이션 점유 맵 병합 명세 — 로케이션 기본행에 재고 합과 고정상품 현재고·유입·미달 판정을 어떻게 얹는지 본다.
 * 점유율은 프론트 파생이지만 보충 미달 판정은 서버가 한다 — 정기보충 산정(SpmtService.plan)과 같은 식이어야 해서.
 */
@ExtendWith(MockitoExtension.class)
class InvServiceTest {

    @Mock InvRepository invRepository;
    @Mock LocMapQueryRepository locMapQueryRepository;
    @Mock InvAlocRecRepository invAlocRecRepository;
    @Mock LocCapacityService locCapacityService;

    private InvService service;

    @BeforeEach
    void setUp() {
        service = new InvService(invRepository, locMapQueryRepository, invAlocRecRepository, locCapacityService);
        when(locCapacityService.openInflowQtyByProdLoc()).thenReturn(Map.of());
    }

    /** 고정 자리는 지정 상품 id 10 */
    private LocRow row(Long locId, String locCd, String fxngProdCd, Long fxngMinQty) {
        return new LocRow(locId, locCd, "DRY", "상온 보관존", BizDvsn.STRG, TmpZon.DRY, 1000L,
                fxngProdCd != null ? 10L : null, fxngProdCd, fxngProdCd != null ? "상품명" : null,
                fxngProdCd != null ? "https://example.supabase.co/storage/v1/object/public/prod-img/x.png" : null,
                fxngMinQty);
    }

    @Test
    @DisplayName("재고 없는 로케이션은 수량 0으로 내려간다 — 빈 자리도 맵의 한 칸이다")
    void locMap_emptyLocGetsZeroQty() {
        when(locMapQueryRepository.locRows()).thenReturn(List.of(row(1L, "DRY-A-01-01", null, null)));
        when(locMapQueryRepository.qtySumsByLoc()).thenReturn(Map.of());
        when(locMapQueryRepository.fxngOnHandByLoc()).thenReturn(Map.of());

        List<LocMapResponse> result = service.locMap();

        assertEquals(1, result.size());
        LocMapResponse res = result.get(0);
        assertEquals("DRY-A-01-01", res.locCd());
        assertEquals(0L, res.onHandQty());
        assertEquals(0L, res.alocQty());
        assertEquals(0L, res.hldQty());
        assertNull(res.fxngProdCd());
        assertNull(res.fxngOnHandQty());
        assertNull(res.fxngInflowQty());
        assertNull(res.fxngShort());
    }

    @Test
    @DisplayName("재고 합과 고정상품 정보가 로케이션 행에 실린다")
    void locMap_mergesQtyAndFxng() {
        when(locMapQueryRepository.locRows()).thenReturn(List.of(row(1L, "PIK-DRY-01-01", "PROD-0001", 50L)));
        when(locMapQueryRepository.qtySumsByLoc()).thenReturn(Map.of(1L, new QtySums(120L, 30L, 10L)));
        when(locMapQueryRepository.fxngOnHandByLoc()).thenReturn(Map.of(1L, 100L));

        LocMapResponse res = service.locMap().get(0);

        assertEquals(120L, res.onHandQty());
        assertEquals(30L, res.alocQty());
        assertEquals(10L, res.hldQty());
        assertEquals("PROD-0001", res.fxngProdCd());
        assertEquals(50L, res.fxngMinQty());
        assertEquals(100L, res.fxngOnHandQty());
        assertFalse(res.fxngShort());
    }

    @Test
    @DisplayName("고정 자리인데 지정 상품 재고가 없으면 고정상품 현재고는 null이 아니라 0 — 미달 판정의 입력이라서")
    void locMap_fxngLocWithoutProdStockGetsZero() {
        when(locMapQueryRepository.locRows()).thenReturn(List.of(row(1L, "PIK-DRY-01-01", "PROD-0001", 50L)));
        // 자리에 타상품 재고만 있는 경우 — 전체 합은 있지만 지정 상품 합은 없다
        when(locMapQueryRepository.qtySumsByLoc()).thenReturn(Map.of(1L, new QtySums(80L, 0L, 0L)));
        when(locMapQueryRepository.fxngOnHandByLoc()).thenReturn(Map.of());

        LocMapResponse res = service.locMap().get(0);

        assertEquals(80L, res.onHandQty());
        assertEquals(0L, res.fxngOnHandQty());
        assertTrue(res.fxngShort());
    }

    @Test
    @DisplayName("미달 판정은 현재고+지정 상품 유입 < min — 보충지시가 이미 떠 있는 자리는 미달로 칠하지 않는다 (산정과 같은 식)")
    void locMap_shortCountsProdInflow() {
        when(locMapQueryRepository.locRows()).thenReturn(List.of(
                row(1L, "PIK-DRY-01-01", "PROD-0001", 50L),
                row(2L, "PIK-DRY-01-02", "PROD-0001", 50L)));
        when(locMapQueryRepository.qtySumsByLoc()).thenReturn(Map.of());
        when(locMapQueryRepository.fxngOnHandByLoc()).thenReturn(Map.of(1L, 20L, 2L, 20L));
        // 1번엔 지정 상품 30 유입 → 20+30 = 50 ≥ min, 2번엔 타상품 유입뿐 → 여전히 미달
        when(locCapacityService.openInflowQtyByProdLoc()).thenReturn(Map.of(
                new ProdLocKey(10L, 1L), 30L,
                new ProdLocKey(99L, 2L), 30L));

        List<LocMapResponse> result = service.locMap();

        assertEquals(30L, result.get(0).fxngInflowQty());
        assertFalse(result.get(0).fxngShort());
        assertEquals(0L, result.get(1).fxngInflowQty());
        assertTrue(result.get(1).fxngShort());
    }
}
