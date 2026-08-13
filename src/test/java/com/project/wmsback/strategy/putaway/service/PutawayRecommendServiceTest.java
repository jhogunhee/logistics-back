package com.project.wmsback.strategy.putaway.service;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.TmpZon;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.mdm.vendor.entity.Vendor;
import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.repository.IbLineRepository;
import com.project.wmsback.inventory.service.LocCapacityService;
import com.project.wmsback.strategy.core.condition.ConditionOperator;
import com.project.wmsback.strategy.core.condition.FieldCondition;
import com.project.wmsback.strategy.core.service.StgyExecLogService;
import com.project.wmsback.strategy.putaway.component.PutawayMethodContext;
import com.project.wmsback.strategy.putaway.dto.PtawyPreviewRequest;
import com.project.wmsback.strategy.putaway.dto.PtawyStgyDefinition;
import com.project.wmsback.strategy.putaway.dto.PutawayBulkRecommendRequest;
import com.project.wmsback.strategy.putaway.dto.PutawayBulkRecommendResponse;
import com.project.wmsback.strategy.putaway.dto.PutawayRecommendResponse;
import com.project.wmsback.strategy.putaway.repository.PtawyStgyRepository;
import com.project.wmsback.strategy.putaway.repository.PutawayQueryRepository;
import com.project.wmsback.warehouse.entity.Loc;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 적치 추천 산정의 동작 명세 — 단계 순차 소진 · 적재가능수량 · 입수 절사 · 게이트 · 수동 폴백.
 * 미리보기 경로(preview)로 산정 본체(compute)를 검증한다 — 실행(recommendBulk)과 같은 함수다 (P4).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PutawayRecommendServiceTest {

    @Mock PtawyStgyRepository ptawyStgyRepository;
    @Mock IbLineRepository ibLineRepository;
    @Mock ProdRepository prodRepository;
    @Mock PutawayQueryRepository putawayQueryRepository;
    @Mock LocCapacityService locCapacityService;
    @Mock StgyExecLogService stgyExecLogService;

    private PutawayRecommendService service;
    private Prod prod;

    @BeforeEach
    void setUp() {
        service = new PutawayRecommendService(ptawyStgyRepository, ibLineRepository, prodRepository,
                putawayQueryRepository, locCapacityService, stgyExecLogService);

        prod = mock(Prod.class);
        when(prod.getId()).thenReturn(10L);
        when(prod.getProdCd()).thenReturn("PROD-0001");
        when(prod.getTmpZon()).thenReturn(TmpZon.DRY);

        when(prodRepository.findById(10L)).thenReturn(Optional.of(prod));
        when(locCapacityService.openInflowQtyByLoc()).thenReturn(Map.of());
    }

    /** 후보 모집단 스텁. stock()이 다른 목을 스터빙하므로 when(...) 바깥에서 인자로 먼저 평가돼야 한다 */
    private void givenStocks(PutawayMethodContext.LocStock... stocks) {
        when(putawayQueryRepository.storageStocks(TmpZon.DRY, 10L)).thenReturn(List.of(stocks));
    }

    /** 보관 로케이션 1개의 재고 현황 픽스처 */
    private PutawayMethodContext.LocStock stock(long locId, String locCd, Long maxQty,
                                                long occupiedQty, boolean hasProd, String bizDvsn) {
        Loc loc = mock(Loc.class);
        when(loc.getId()).thenReturn(locId);
        when(loc.getLocCd()).thenReturn(locCd);
        when(loc.getMaxQty()).thenReturn(maxQty);
        when(loc.getPikngPrty()).thenReturn(0);
        when(loc.getPtawyPrty()).thenReturn(0);
        return new PutawayMethodContext.LocStock(loc, occupiedQty, hasProd, bizDvsn);
    }

    private PtawyStgyDefinition def(boolean untSpltYn, PtawyStgyDefinition.StageDef... stages) {
        return new PtawyStgyDefinition("테스트 전략", null, untSpltYn, List.of(), List.of(stages));
    }

    private PtawyStgyDefinition.StageDef stage(String mthdCd, List<FieldCondition> lineCond,
                                               List<FieldCondition> locCond) {
        return new PtawyStgyDefinition.StageDef(0, mthdCd, Map.of(), lineCond, locCond);
    }

    private PutawayRecommendResponse preview(PtawyStgyDefinition definition, long qty) {
        return service.preview(definition, new PtawyPreviewRequest(null, null, null, 10L, qty));
    }

    @Test
    @DisplayName("단계는 순서대로 잔량을 소진한다 — 적재로케이션에 합치고, 남으면 빈로케이션을 연다")
    void stagesConsumeRemainderInOrder() {
        givenStocks(
                stock(1L, "A", 60L, 50, true, null),   // 같은 상품 보유, 여유 10
                stock(2L, "B", 100L, 0, false, null)); // 빈 로케이션

        PutawayRecommendResponse result = preview(
                def(false, stage("SAME_PROD_LOC", List.of(), List.of()),
                        stage("EMPTY_LOC", List.of(), List.of())), 30);

        assertEquals(30, result.asgnQty());
        assertEquals(0, result.remainQty());
        assertEquals(List.of("A", "B"),
                result.assignments().stream().map(PutawayRecommendResponse.Assignment::locCd).toList());
        assertEquals(10, result.assignments().get(0).qty());
        assertEquals(20, result.assignments().get(1).qty());
        assertEquals("PASS", result.trace().stages().get(0).gate());
    }

    @Test
    @DisplayName("적재가능 = max_qty − 현재고 − 미완료 지시 유입 잔량 — 지시가 잡아둔 자리는 쓸 수 없다")
    void openInflowReducesCapacity() {
        givenStocks(stock(1L, "A", 100L, 0, false, null));
        when(locCapacityService.openInflowQtyByLoc()).thenReturn(Map.of(1L, 95L));

        PutawayRecommendResponse result = preview(def(false, stage("ANY_LOC", List.of(), List.of())), 10);

        assertEquals(5, result.asgnQty());
        assertEquals(5, result.remainQty());
        assertEquals(95L, result.trace().stages().get(0).locs().get(0).inflowQty());
    }

    @Test
    @DisplayName("입수 단위 배수 절사 — 1단위도 안 들어가는 로케이션은 사유를 남기고 건너뛴다")
    void unitSplitSkipsSubUnitCapacity() {
        when(prod.getInbUomCd()).thenReturn("BOX");
        when(prod.eaQtyOf("BOX")).thenReturn(12L);
        givenStocks(
                stock(1L, "A", 10L, 0, false, null),   // 여유 10 < 입수 12
                stock(2L, "B", 100L, 0, false, null));

        PutawayRecommendResponse result = preview(def(true, stage("ANY_LOC", List.of(), List.of())), 24);

        assertEquals(24, result.asgnQty());
        assertEquals(List.of("B"),
                result.assignments().stream().map(PutawayRecommendResponse.Assignment::locCd).toList());
        assertEquals("입수 단위(12) 미만", result.trace().stages().get(0).locs().get(0).skip());
    }

    @Test
    @DisplayName("라인 조건이 맞지 않는 단계는 게이트에서 빠진다 — 시도 자체를 하지 않는다")
    void lineConditionGatesStage() {
        givenStocks(stock(1L, "A", 100L, 0, false, null));

        PutawayRecommendResponse result = preview(def(false,
                stage("ANY_LOC",
                        List.of(new FieldCondition("TMP_ZON", ConditionOperator.EQ, List.of("CHL"))),
                        List.of())), 10);

        assertEquals(0, result.asgnQty());
        assertEquals(10, result.remainQty());
        assertEquals("SKIP — 라인 조건 불일치", result.trace().stages().get(0).gate());
    }

    @Test
    @DisplayName("적치위치 지정(존 업무유형 IN)은 후보를 그 존으로 제한한다 — 존 미등록은 지정 시 제외")
    void locAssignFiltersByBizDvsn() {
        givenStocks(
                stock(1L, "A", 100L, 0, false, "STRG"),
                stock(2L, "B", 100L, 0, false, null)); // 존 미등록 — bizDvsn 없음

        PutawayRecommendResponse result = preview(def(false,
                stage("ANY_LOC", List.of(),
                        List.of(new FieldCondition("BIZ_DVSN", ConditionOperator.IN, List.of("STRG"))))), 10);

        assertEquals(List.of("A"),
                result.assignments().stream().map(PutawayRecommendResponse.Assignment::locCd).toList());
    }

    @Test
    @DisplayName("전략 미설정 배치는 수동 지시 대상으로 표시된다 — 실행 로그도 남기지 않는다")
    void noStrategyFallsBackToManual() {
        IbOrder ibOrder = mock(IbOrder.class);
        when(ibOrder.getOdrDvsn()).thenReturn("NRML");
        Vendor vendor = mock(Vendor.class);
        when(vendor.getVndrCd()).thenReturn("V-0001");
        when(ibOrder.getVendor()).thenReturn(vendor);
        IbLine ibLine = mock(IbLine.class);
        when(ibLine.getProd()).thenReturn(prod);
        when(ibLine.getIbOrder()).thenReturn(ibOrder);
        when(ibLineRepository.findById(5L)).thenReturn(Optional.of(ibLine));
        when(ptawyStgyRepository.findByOdrDvsn("NRML")).thenReturn(Optional.empty());
        when(ptawyStgyRepository.findByOdrDvsnIsNull()).thenReturn(Optional.empty());

        PutawayBulkRecommendRequest request = new PutawayBulkRecommendRequest();
        PutawayBulkRecommendRequest.Item item = new PutawayBulkRecommendRequest.Item();
        item.setIbLineId(5L);
        item.setLotId(7L);
        item.setQty(30L);
        request.setItems(List.of(item));

        PutawayBulkRecommendResponse.Item result = service.recommendBulk(request).getItems().get(0);

        assertFalse(result.isStrategySelected());
        assertEquals(30, result.getRemainQty());
        assertTrue(result.getAssignments().isEmpty());
        verify(stgyExecLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }
}
