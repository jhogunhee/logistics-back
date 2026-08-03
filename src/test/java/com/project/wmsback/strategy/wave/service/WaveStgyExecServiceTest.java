package com.project.wmsback.strategy.wave.service;

import com.project.mdm.nbr.service.NbrService;
import com.project.mdm.store.entity.Store;
import com.project.wmsback.outbound.dto.OutbOrderSearchCond;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.entity.WavRegTyp;
import com.project.wmsback.outbound.repository.OutbOrderRepository;
import com.project.wmsback.outbound.repository.OutbWaveRepository;
import com.project.wmsback.strategy.core.condition.ConditionOperator;
import com.project.wmsback.strategy.core.condition.FieldCondition;
import com.project.wmsback.strategy.core.service.StgyExecLogService;
import com.project.wmsback.strategy.wave.dto.WavPreviewRequest;
import com.project.wmsback.strategy.wave.dto.WavStgyDefinition;
import com.project.wmsback.strategy.wave.dto.WaveStgyExecRequest;
import com.project.wmsback.strategy.wave.dto.WaveStgyExecResponse;
import com.project.wmsback.strategy.wave.entity.WavStgy;
import com.project.wmsback.strategy.wave.repository.WavStgyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 웨이브 전략 실행 — 조건 판정(그룹 OR / 조건 AND) · 선점 · 편입 0건 시 웨이브 미생성 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WaveStgyExecServiceTest {

    @Mock private WavStgyRepository wavStgyRepository;
    @Mock private OutbOrderRepository outbOrderRepository;
    @Mock private OutbWaveRepository outbWaveRepository;
    @Mock private NbrService nbrService;
    @Mock private StgyExecLogService stgyExecLogService;

    @InjectMocks private WaveStgyExecService service;

    private static final LocalDate ODR_DE = LocalDate.of(2026, 8, 3);

    private OutbOrder order(String outbNo, String outbTyp, String vhclFltno) {
        Store store = Store.builder().storeCd("ST-0001").storeNm("강남점").build();
        return OutbOrder.builder().outbNo(outbNo).store(store).odrDe(ODR_DE)
                .outbTyp(outbTyp).vhclFltno(vhclFltno).build();
    }

    private WavStgy stgy(String nm, int prty, List<List<FieldCondition>> condGrp) {
        return WavStgy.builder().stgyNm(nm).prty(prty).condGrp(condGrp).build();
    }

    private FieldCondition cond(String fld, ConditionOperator op, String... vals) {
        return new FieldCondition(fld, op, List.of(vals));
    }

    private void givenTargets(OutbOrder... orders) {
        when(outbOrderRepository.search(any(OutbOrderSearchCond.class))).thenReturn(List.of(orders));
        when(nbrService.issue(anyString(), any(LocalDate.class))).thenReturn("WV-20260803-001", "WV-20260803-002");
        when(outbWaveRepository.save(any(OutbWave.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("그룹 안 조건은 AND — 「일반출고 AND 1편」에 편수가 다르면 편입되지 않는다")
    void conditionsInGroupAreAnd() {
        OutbOrder hit = order("OB-1", "NRML", "1");
        OutbOrder miss = order("OB-2", "NRML", "2");
        givenTargets(hit, miss);
        when(wavStgyRepository.findAllByOrderByPrtyAscIdAsc()).thenReturn(List.of(
                stgy("일반출고 1편 웨이브", 0, List.of(List.of(
                        cond("OUTB_TYP", ConditionOperator.EQ, "NRML"),
                        cond("VHCL_FLTNO", ConditionOperator.EQ, "1"))))));

        WaveStgyExecResponse res = service.execute(new WaveStgyExecRequest(null, null, null));

        assertEquals(2, res.tgtCount());
        assertEquals(1, res.assignedCount());
        assertNotNull(hit.getWave());
        assertEquals(WavRegTyp.STGY, hit.getWavRegTyp());
        assertNull(miss.getWave());
    }

    @Test
    @DisplayName("그룹끼리는 OR — 「(일반출고 AND 2편) OR 반품출고」")
    void groupsAreOr() {
        OutbOrder byFleet = order("OB-1", "NRML", "2");
        OutbOrder byType = order("OB-2", "RTNGS", "3");
        OutbOrder neither = order("OB-3", "NRML", "1");
        givenTargets(byFleet, byType, neither);
        when(wavStgyRepository.findAllByOrderByPrtyAscIdAsc()).thenReturn(List.of(
                stgy("2편 또는 반품", 0, List.of(
                        List.of(cond("OUTB_TYP", ConditionOperator.EQ, "NRML"),
                                cond("VHCL_FLTNO", ConditionOperator.EQ, "2")),
                        List.of(cond("OUTB_TYP", ConditionOperator.EQ, "RTNGS"))))));

        WaveStgyExecResponse res = service.execute(new WaveStgyExecRequest(null, null, null));

        assertEquals(2, res.assignedCount());
        assertNotNull(byFleet.getWave());
        assertNotNull(byType.getWave());
        assertNull(neither.getWave());
    }

    @Test
    @DisplayName("배차 미정(차량편수 NULL)은 등가 조건에 걸리지 않고 부정 조건에만 걸린다")
    void nullFleetOnlyMatchesNegation() {
        OutbOrder unassigned = order("OB-1", "NRML", null);
        givenTargets(unassigned);
        when(wavStgyRepository.findAllByOrderByPrtyAscIdAsc()).thenReturn(List.of(
                stgy("1편", 0, List.of(List.of(cond("VHCL_FLTNO", ConditionOperator.EQ, "1")))),
                stgy("1편 아님", 1, List.of(List.of(cond("VHCL_FLTNO", ConditionOperator.NE, "1"))))));

        WaveStgyExecResponse res = service.execute(new WaveStgyExecRequest(null, null, null));

        assertEquals(0, res.results().get(0).assignedCount());
        assertEquals(1, res.results().get(1).assignedCount());
    }

    @Test
    @DisplayName("주문은 먼저 실행된 전략이 선점한다 — 뒤 전략의 후보에서 빠진다")
    void earlierStrategyClaimsOrder() {
        OutbOrder shared = order("OB-1", "NRML", "1");
        givenTargets(shared);
        // 두 전략 모두 이 주문에 매칭되지만 prty가 낮은 쪽이 가져간다
        when(wavStgyRepository.findAllByOrderByPrtyAscIdAsc()).thenReturn(List.of(
                stgy("먼저", 0, List.of(List.of(cond("OUTB_TYP", ConditionOperator.EQ, "NRML")))),
                stgy("나중", 1, List.of(List.of(cond("VHCL_FLTNO", ConditionOperator.IN, "1", "2"))))));

        WaveStgyExecResponse res = service.execute(new WaveStgyExecRequest(null, null, null));

        assertEquals(1, res.assignedCount());
        assertEquals(1, res.results().get(0).assignedCount());
        assertEquals("먼저", res.results().get(0).stgyNm());
        // 나중 전략은 후보가 비어 웨이브를 만들지 않는다
        assertEquals(0, res.results().get(1).assignedCount());
        assertNull(res.results().get(1).outbWaveId());
        verify(outbWaveRepository).save(any(OutbWave.class));
    }

    @Test
    @DisplayName("편입 0건이면 웨이브를 만들지 않는다 — 재실행해도 빈 웨이브가 쌓이지 않는다")
    void noWaveWhenNothingMatches() {
        givenTargets(order("OB-1", "NRML", "1"));
        when(wavStgyRepository.findAllByOrderByPrtyAscIdAsc()).thenReturn(List.of(
                stgy("반품출고 웨이브", 0, List.of(List.of(
                        cond("OUTB_TYP", ConditionOperator.EQ, "RTNGS"))))));

        WaveStgyExecResponse res = service.execute(new WaveStgyExecRequest(null, null, null));

        assertEquals(0, res.assignedCount());
        assertNull(res.results().get(0).outbWaveId());
        assertTrue(res.results().get(0).skipRsn().contains("웨이브를 만들지 않았습니다"));
        verify(outbWaveRepository, never()).save(any(OutbWave.class));
    }

    @Test
    @DisplayName("미리보기는 판정만 하고 편성하지 않는다")
    void previewDoesNotAssign() {
        OutbOrder hit = order("OB-1", "NRML", "1");
        when(outbOrderRepository.search(any(OutbOrderSearchCond.class))).thenReturn(List.of(hit));

        var res = service.preview(
                new WavStgyDefinition("미저장", 0,
                        List.of(List.of(cond("OUTB_TYP", ConditionOperator.EQ, "NRML")))),
                new WavPreviewRequest(null, null, null));

        assertEquals(1, res.tgtCount());
        assertEquals(1, res.matchedCount());
        assertTrue(res.orders().get(0).matched());
        assertNull(hit.getWave());
        verify(outbWaveRepository, never()).save(any(OutbWave.class));
    }
}
