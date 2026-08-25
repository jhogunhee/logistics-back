package com.project.wmsback.strategy.inspection.service;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.wmsback.inbound.repository.IbLineRepository;
import com.project.wmsback.strategy.core.service.StgyExecLogService;
import com.project.wmsback.strategy.inspection.dto.InspMinMfgDtRequest;
import com.project.wmsback.strategy.inspection.dto.InspMinMfgDtResponse;
import com.project.wmsback.strategy.inspection.entity.InspPlcy;
import com.project.wmsback.strategy.inspection.entity.InspPlcyRule;
import com.project.wmsback.strategy.inspection.repository.InspPlcyRepository;
import com.project.wmsback.strategy.inspection.repository.InspectionQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 검수 제약의 「입력 전 힌트」 명세 — 상품·입고일자만으로 규칙마다 입고 가능한 가장 이른 제조일자를 내고,
 * 전체 하한은 그중 가장 늦은 날이다(모든 규칙을 동시에 만족해야 하므로). 판정 자체는 규칙이 하고
 * 여기서는 묶기만 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InspectionServiceTest {

    private static final LocalDate RECEIPT_DT = LocalDate.of(2026, 8, 14);

    @Mock InspPlcyRepository inspPlcyRepository;
    @Mock IbLineRepository ibLineRepository;
    @Mock InspectionQueryRepository inspectionQueryRepository;
    @Mock StgyExecLogService stgyExecLogService;
    @Mock ProdRepository prodRepository;

    private InspectionService service;
    private Prod prod;

    @BeforeEach
    void setUp() {
        service = new InspectionService(inspPlcyRepository, ibLineRepository, inspectionQueryRepository,
                stgyExecLogService, prodRepository);
        prod = mock(Prod.class);
        when(prod.getId()).thenReturn(1L);
        when(prod.getShelfLifeDays()).thenReturn(100);
        when(prodRepository.findAllById(List.of(1L))).thenReturn(List.of(prod));
    }

    private void stubPolicy(InspPlcyRule... rules) {
        InspPlcy plcy = mock(InspPlcy.class);
        when(plcy.getRules()).thenReturn(List.of(rules));
        when(inspPlcyRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(plcy));
    }

    private InspPlcyRule rule(String ruleCd, Map<String, Object> para) {
        InspPlcyRule r = mock(InspPlcyRule.class);
        when(r.getRuleCd()).thenReturn(ruleCd);
        when(r.getPara()).thenReturn(para);
        return r;
    }

    private InspMinMfgDtResponse.Item ask() {
        return ask(false);
    }

    private InspMinMfgDtResponse.Item ask(boolean rtngs) {
        return service.minMfgDts(new InspMinMfgDtRequest(
                List.of(new InspMinMfgDtRequest.Item(1L, RECEIPT_DT)), rtngs)).items().get(0);
    }

    @Test
    @DisplayName("규칙마다 하한을 내고 전체 하한은 그중 가장 늦은 날 — 둘 다 만족해야 통과하므로")
    void overallMinIsLatestOfRuleMins() {
        stubPolicy(rule("SHELF_LIFE_PCT", Map.of("minPercent", 80)),     // 경과 허용 20일 → 07-25
                rule("LOT_DATE_REVERSE", Map.of("excludeSameDay", true)));
        when(inspectionQueryRepository.latestMfgDtWithStock(1L, RECEIPT_DT)).thenReturn(LocalDate.of(2026, 8, 1));

        InspMinMfgDtResponse.Item item = ask();

        assertEquals(LocalDate.of(2026, 8, 1), item.minMfgDt());
        assertEquals(2, item.rules().size());
        assertEquals(LocalDate.of(2026, 7, 25), item.rules().get(0).minMfgDt());
        assertEquals(LocalDate.of(2026, 8, 1), item.rules().get(1).minMfgDt());
    }

    @Test
    @DisplayName("정책이 없거나 규칙이 비면 하한 없음 — 검수 자체를 막지 않는 것과 같은 결")
    void noPolicyMeansNoMin() {
        when(inspPlcyRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        InspMinMfgDtResponse.Item item = ask();

        assertNull(item.minMfgDt());
        assertTrue(item.rules().isEmpty());
    }

    @Test
    @DisplayName("규칙이 하한을 못 내는 상품(유통기한 미관리)은 규칙별 하한이 null이고 전체도 null")
    void unmanagedProductHasNoMin() {
        when(prod.getShelfLifeDays()).thenReturn(null);
        stubPolicy(rule("SHELF_LIFE_PCT", Map.of("minPercent", 80)));

        InspMinMfgDtResponse.Item item = ask();

        assertNull(item.minMfgDt());
        assertNull(item.rules().get(0).minMfgDt());
    }

    @Test
    @DisplayName("입고일자를 안 보내면 오늘로 본다 (검수 저장과 같은 기본값)")
    void receiptDtDefaultsToToday() {
        stubPolicy(rule("SHELF_LIFE_PCT", Map.of("minPercent", 80)));

        InspMinMfgDtResponse.Item item = service.minMfgDts(new InspMinMfgDtRequest(
                List.of(new InspMinMfgDtRequest.Item(1L, null)), false)).items().get(0);

        assertEquals(LocalDate.now(), item.receiptDt());
        assertEquals(LocalDate.now().minusDays(20), item.minMfgDt());
    }

    @Test
    @DisplayName("반품이면 역순제한 규칙은 하한을 안 내고, 전체 하한은 유통기한 규칙만으로 정해진다")
    void rtngsSkipsLotDateReverse() {
        stubPolicy(rule("SHELF_LIFE_PCT", Map.of("minPercent", 80)),     // 경과 허용 20일 → 07-25
                rule("LOT_DATE_REVERSE", Map.of("excludeSameDay", true)));

        InspMinMfgDtResponse.Item item = ask(true);

        assertNull(item.rules().get(1).minMfgDt());
        assertEquals(LocalDate.of(2026, 7, 25), item.minMfgDt());
    }
}
