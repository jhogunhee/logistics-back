package com.project.wmsback.inventory.service;

import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.inventory.dto.SpmtIssueRequest;
import com.project.wmsback.inventory.dto.SpmtTargetResponse;
import com.project.wmsback.inventory.dto.SpmtTargetSearchCond;
import com.project.wmsback.inventory.entity.InvMovDvsn;
import com.project.wmsback.inventory.dto.InvMovRegisterRequest;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.inventory.repository.SpmtQueryRepository;
import com.project.wmsback.inventory.repository.SpmtQueryRepository.SourceRow;
import com.project.wmsback.inventory.repository.SpmtQueryRepository.TargetRow;
import com.project.wmsback.warehouse.entity.FxngLoc;
import com.project.wmsback.warehouse.entity.Loc;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.project.mdm.prod.entity.TmpZon.DRY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 보충 산정·발행의 동작 명세.
 * 산정(plan)은 조회 전용 추천(추천≠예약)이고, 발행(issue)이 같은 식으로 재검증한 뒤
 * 이동지시 등록 창구(InvMovService.register)에 SPMT로 위임한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SpmtServiceTest {

    @Mock SpmtQueryRepository spmtQueryRepository;
    @Mock LocCapacityService locCapacityService;
    @Mock InvMovService invMovService;
    @Mock FxngLocRepository fxngLocRepository;
    @Mock LocRepository locRepository;
    @Mock InvRepository invRepository;

    private SpmtService service;

    @BeforeEach
    void setUp() {
        service = new SpmtService(spmtQueryRepository, locCapacityService, invMovService,
                fxngLocRepository, locRepository, invRepository);
        when(locCapacityService.openInflowQtyByProdLoc()).thenReturn(Map.of());
        when(locCapacityService.openInflowQtyByLoc()).thenReturn(Map.of());
        when(spmtQueryRepository.sources(anyCollection())).thenReturn(List.of());
    }

    private ProdLocKey inflowKey(long prodId, long locId) {
        return new ProdLocKey(prodId, locId);
    }

    /** 물리 용량이 넉넉한 대상 (loc.max_qty 1000, 자리엔 지정 상품만 있음) */
    private TargetRow target(long locId, String locCd, long prodId, long min, long max, long onHand) {
        return target(locId, locCd, prodId, min, max, onHand, 1000L, onHand);
    }

    private TargetRow target(long locId, String locCd, long prodId, long min, long max, long onHand,
                             Long locMaxQty, long locOnHandQty) {
        return new TargetRow(locId * 10, locId, locCd, "PIKNG", prodId, "PROD-" + prodId, "상품" + prodId,
                DRY, min, max, onHand, locMaxQty, locOnHandQty);
    }

    private SourceRow source(long prodId, long invId, String locCd, String lotNo, LocalDate expiry, long aval) {
        return new SourceRow(prodId, invId, locCd, lotNo, expiry, aval);
    }

    // ── 산정 (plan) ──────────────────────────────────────────────

    @Test
    @DisplayName("min 미달 판정 — 현재고+유입이 min과 같으면 제외, 미만이면 대상")
    void minShortBoundary() {
        when(spmtQueryRepository.targets(any())).thenReturn(List.of(
                target(1, "P-01", 10, 20, 100, 20),  // == min → 제외
                target(2, "P-02", 10, 20, 100, 19)   // < min → 대상
        ));

        List<SpmtTargetResponse> result = service.plan(new SpmtTargetSearchCond());

        assertEquals(1, result.size());
        assertEquals("P-02", result.get(0).locCd());
        assertEquals(81, result.get(0).shortQty()); // 100 - 19
    }

    @Test
    @DisplayName("미완료 유입 잔량을 현재고에 얹어 판정·부족량 계산 — 이미 지시 건 자리의 중복 발행 방지")
    void inflowCountsTowardShort() {
        when(spmtQueryRepository.targets(any())).thenReturn(List.of(
                target(1, "P-01", 10, 30, 100, 5),
                target(2, "P-02", 10, 30, 100, 5)
        ));
        // P-01엔 이미 95개 유입 예정 → 부족 없음, P-02엔 15개 → 여전히 min 미달, 부족 80
        when(locCapacityService.openInflowQtyByProdLoc())
                .thenReturn(Map.of(inflowKey(10, 1), 95L, inflowKey(10, 2), 15L));

        List<SpmtTargetResponse> result = service.plan(new SpmtTargetSearchCond());

        assertEquals(1, result.size());
        assertEquals("P-02", result.get(0).locCd());
        assertEquals(15, result.get(0).inflowQty());
        assertEquals(80, result.get(0).shortQty()); // 100 - 5 - 15
    }

    @Test
    @DisplayName("다른 상품의 유입은 얹지 않는다 — 전용 자리로 오는 타상품 지시가 부족 판정을 가리지 않게")
    void foreignProdInflowDoesNotCount() {
        when(spmtQueryRepository.targets(any())).thenReturn(List.of(
                target(1, "P-01", 10, 20, 100, 5)
        ));
        when(locCapacityService.openInflowQtyByProdLoc()).thenReturn(Map.of(inflowKey(99, 1), 30L));

        List<SpmtTargetResponse> result = service.plan(new SpmtTargetSearchCond());

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).inflowQty());
        assertEquals(95, result.get(0).shortQty()); // 100 - 5
    }

    @Test
    @DisplayName("FEFO 추천 — 원천 목록 순서(유통기한 임박순)대로 1:N 분할 배정")
    void fefoAssignmentSplits() {
        when(spmtQueryRepository.targets(any())).thenReturn(List.of(
                target(1, "P-01", 10, 20, 100, 10) // 부족 90
        ));
        when(spmtQueryRepository.sources(anyCollection())).thenReturn(List.of(
                source(10, 101, "S-01", "LOT-1", LocalDate.of(2026, 9, 1), 60),
                source(10, 102, "S-02", "LOT-2", LocalDate.of(2026, 10, 1), 60)
        ));

        List<SpmtTargetResponse.Assignment> assignments = service.plan(new SpmtTargetSearchCond())
                .get(0).assignments();

        assertEquals(2, assignments.size());
        assertEquals(101, assignments.get(0).invId());
        assertEquals(60, assignments.get(0).qty());
        assertEquals(102, assignments.get(1).invId());
        assertEquals(30, assignments.get(1).qty());
    }

    @Test
    @DisplayName("원천 가용이 모자라면 부분 배정 — 배정 합이 부족량에 못 미친 채 내려간다")
    void partialAssignmentWhenSourceShort() {
        when(spmtQueryRepository.targets(any())).thenReturn(List.of(
                target(1, "P-01", 10, 20, 100, 10) // 부족 90
        ));
        when(spmtQueryRepository.sources(anyCollection())).thenReturn(List.of(
                source(10, 101, "S-01", "LOT-1", LocalDate.of(2026, 9, 1), 25)
        ));

        SpmtTargetResponse result = service.plan(new SpmtTargetSearchCond()).get(0);

        assertEquals(90, result.shortQty());
        assertEquals(1, result.assignments().size());
        assertEquals(25, result.assignments().get(0).qty());
    }

    @Test
    @DisplayName("원천이 없어도 대상은 내려간다 — 배정만 비어 화면이 「채울 재고 없음」을 보여줄 수 있게")
    void targetWithoutSources() {
        when(spmtQueryRepository.targets(any())).thenReturn(List.of(
                target(1, "P-01", 10, 20, 100, 10)
        ));

        SpmtTargetResponse result = service.plan(new SpmtTargetSearchCond()).get(0);

        assertTrue(result.assignments().isEmpty());
        assertTrue(result.sources().isEmpty());
    }

    @Test
    @DisplayName("같은 상품의 다중 대상 — 원천 가용을 전역 선점해 이중 배정하지 않는다")
    void globalSourceReservationAcrossTargets() {
        when(spmtQueryRepository.targets(any())).thenReturn(List.of(
                target(1, "P-01", 10, 20, 50, 10), // 부족 40
                target(2, "P-02", 10, 20, 50, 10)  // 부족 40
        ));
        when(spmtQueryRepository.sources(anyCollection())).thenReturn(List.of(
                source(10, 101, "S-01", "LOT-1", LocalDate.of(2026, 9, 1), 60)
        ));

        List<SpmtTargetResponse> result = service.plan(new SpmtTargetSearchCond());

        assertEquals(40, result.get(0).assignments().get(0).qty());
        assertEquals(20, result.get(1).assignments().get(0).qty()); // 60 - 40 남은 만큼만
    }

    @Test
    @DisplayName("물리 적재가능(loc.max − 전상품 현재고 − 전상품 유입)이 부족량보다 작으면 배정을 거기까지만 — 발행 창구의 용량 검증에 걸릴 추천을 내지 않는다")
    void assignmentCappedByPhysicalCapacity() {
        // 고정 부족 90 (max 100 − 10), 그러나 자리엔 타상품 60이 섞여 있고(전상품 70) 전상품 유입 10 → 물리 여유 20
        when(spmtQueryRepository.targets(any())).thenReturn(List.of(
                target(1, "P-01", 10, 20, 100, 10, 100L, 70)
        ));
        when(locCapacityService.openInflowQtyByLoc()).thenReturn(Map.of(1L, 10L));
        when(spmtQueryRepository.sources(anyCollection())).thenReturn(List.of(
                source(10, 101, "S-01", "LOT-1", LocalDate.of(2026, 9, 1), 60)
        ));

        SpmtTargetResponse result = service.plan(new SpmtTargetSearchCond()).get(0);

        assertEquals(90, result.shortQty()); // 부족량 표시는 고정 기준 그대로
        assertEquals(1, result.assignments().size());
        assertEquals(20, result.assignments().get(0).qty());
    }

    @Test
    @DisplayName("loc.max_qty가 없는 옛 행은 물리 상한 없음 — 부족량만큼 배정")
    void noPhysicalCapWhenLocMaxQtyNull() {
        when(spmtQueryRepository.targets(any())).thenReturn(List.of(
                target(1, "P-01", 10, 20, 100, 10, null, 10)
        ));
        when(spmtQueryRepository.sources(anyCollection())).thenReturn(List.of(
                source(10, 101, "S-01", "LOT-1", LocalDate.of(2026, 9, 1), 100)
        ));

        SpmtTargetResponse result = service.plan(new SpmtTargetSearchCond()).get(0);

        assertEquals(90, result.assignments().get(0).qty());
    }

    // ── 발행 (issue) ─────────────────────────────────────────────

    private Loc toLoc;
    private FxngLoc fxng;

    private void stubIssueBase(long fxngMax, long onHand, long inflow) {
        Prod prod = mock(Prod.class);
        when(prod.getId()).thenReturn(10L);
        when(prod.getProdCd()).thenReturn("PROD-10");

        toLoc = mock(Loc.class);
        when(toLoc.getId()).thenReturn(1L);
        when(toLoc.getLocCd()).thenReturn("P-01");
        when(locRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(toLoc));

        fxng = mock(FxngLoc.class);
        when(fxng.getProd()).thenReturn(prod);
        when(fxng.getMaxQty()).thenReturn(fxngMax);
        when(fxngLocRepository.findByLoc(toLoc)).thenReturn(Optional.of(fxng));

        // 원천 재고 101 = 상품 10 @ 보관 로케이션 5, Lot 7 — 락 전 선조회는 스칼라 키뿐이다
        when(invRepository.findLockKeysByIdIn(any())).thenReturn(List.of(new InvLockKey(101L, 10L, 5L, 7L)));
        when(fxngLocRepository.findLocIdsByLocIdIn(any())).thenReturn(Set.of());

        when(spmtQueryRepository.prodOnHandQty(10L, 1L)).thenReturn(onHand);
        when(locCapacityService.openInflowQty(10L, 1L)).thenReturn(inflow);
        when(invMovService.register(any(), eq(InvMovDvsn.SPMT)))
                .thenReturn(List.of("SP-20260821-001"));
    }

    private SpmtIssueRequest issueRequest(long qty) {
        SpmtIssueRequest.Item item = new SpmtIssueRequest.Item();
        item.setInvId(101L);
        item.setToLocId(1L);
        item.setQty(qty);
        SpmtIssueRequest request = new SpmtIssueRequest();
        request.setItems(List.of(item));
        return request;
    }

    @Test
    @DisplayName("발행 — 재검증 통과 시 SPMT 유형·SPMT_NO 채번으로 이동지시 등록에 위임한다")
    void issueDelegatesToRegister() {
        stubIssueBase(100, 10, 5); // 부족 85

        List<String> movNos = service.issue(issueRequest(85));

        assertEquals(List.of("SP-20260821-001"), movNos);
        ArgumentCaptor<InvMovRegisterRequest> captor = ArgumentCaptor.forClass(InvMovRegisterRequest.class);
        verify(invMovService).register(captor.capture(), eq(InvMovDvsn.SPMT));
        InvMovRegisterRequest.Item delegated = captor.getValue().getItems().get(0);
        assertEquals(101L, delegated.getInvId());
        assertEquals(1L, delegated.getToLocId());
        assertEquals(85L, delegated.getQty());
    }

    @Test
    @DisplayName("발행 수량 합이 재계산 부족량을 넘으면 거부 — 초과 보충·중복 발행 차단")
    void issueRejectsOverShort() {
        stubIssueBase(100, 10, 5); // 부족 85

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.issue(issueRequest(86)));
        assertTrue(e.getMessage().contains("부족"));
    }

    @Test
    @DisplayName("고정로케이션 마스터에 없는 자리로는 발행할 수 없다")
    void issueRejectsUnregisteredLoc() {
        stubIssueBase(100, 10, 0);
        when(fxngLocRepository.findByLoc(toLoc)).thenReturn(Optional.empty());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.issue(issueRequest(10)));
        assertTrue(e.getMessage().contains("고정"));
    }

    @Test
    @DisplayName("고정 상품과 다른 상품의 재고로는 채울 수 없다")
    void issueRejectsProdMismatch() {
        stubIssueBase(100, 10, 0);
        when(invRepository.findLockKeysByIdIn(any())).thenReturn(List.of(new InvLockKey(101L, 99L, 5L, 7L)));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.issue(issueRequest(10)));
        assertTrue(e.getMessage().contains("상품"));
    }

    @Test
    @DisplayName("고정로케이션에 등재된 자리의 재고는 원천이 될 수 없다 — 다른 피킹면을 헐어 채우면 그 자리가 곧 보충 대상이 된다")
    void issueRejectsSourceOnFxngLoc() {
        stubIssueBase(100, 10, 0);
        when(fxngLocRepository.findLocIdsByLocIdIn(any())).thenReturn(Set.of(5L)); // 원천 로케이션 5가 고정 등재

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.issue(issueRequest(10)));
        assertTrue(e.getMessage().contains("고정"));
        verify(invMovService, never()).register(any(), any());
    }

    @Test
    @DisplayName("발행은 락 전에 원천 Inv 엔티티를 올리지 않는다 — 올리면 등록 창구의 락 재조회가 낡은 인스턴스를 돌려준다")
    void issueDoesNotHydrateInvBeforeLock() {
        stubIssueBase(100, 10, 0);

        service.issue(issueRequest(10));

        verify(invRepository, never()).findAllById(any());
        verify(invRepository, never()).findById(any());
    }

    @Test
    @DisplayName("발행 재검증의 유입도 고정 상품 기준 — 타상품 유입이 부족량을 깎아 정상 발행을 막지 않게")
    void issueRevalidationUsesProdScopedInflow() {
        stubIssueBase(100, 10, 0); // 상품 10의 유입 0 → 부족 90
        when(locCapacityService.openInflowQty(99L, 1L)).thenReturn(50L); // 타상품 유입은 무관해야 한다

        List<String> movNos = service.issue(issueRequest(90));

        assertEquals(1, movNos.size());
    }
}
