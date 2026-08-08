package com.project.wmsback.inventory.service;

import com.project.mdm.code.entity.CodeDetailId;
import com.project.mdm.code.repository.CodeDetailRepository;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.wmsback.inventory.dto.LotAttrChngRequest;
import com.project.wmsback.inventory.entity.LotAttrChng;
import com.project.wmsback.inventory.repository.LotAttrChngRepository;
import com.project.wmsback.inventory.repository.LotAttrQueryRepository;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.wmsback.warehouse.repository.LotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot 속성 정정의 검증 규칙.
 *
 * 이 서비스는 InvRepository·InvHistRepository를 아예 주입받지 않는다 —
 * 「재고를 한 톨도 움직이지 않는다」가 구조로 보장되므로 재고 무변동은 테스트 대상이 아니다.
 * 확인할 것은 배치 재사용 키 충돌(DB가 안 막아주는 유일한 지점)과 날짜 규칙,
 * 그리고 다건 정정이 잡는 **락 순서**다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LotAttrChngServiceTest {

    private static final long LOT_ID = 5L;
    private static final long PROD_ID = 1L;
    private static final LocalDate RECEIPT_DT = LocalDate.of(2026, 7, 22);

    @Mock LotRepository lotRepository;
    @Mock ProdRepository prodRepository;
    @Mock LotAttrChngRepository lotAttrChngRepository;
    @Mock LotAttrQueryRepository lotAttrQueryRepository;
    @Mock CodeDetailRepository codeDetailRepository;

    @InjectMocks LotAttrChngService lotAttrChngService;

    private Prod prod;
    private Lot lot;

    @BeforeEach
    void setUp() {
        prod = newProd(PROD_ID, "PROD-0001");
        // 제조 2026-07-20 / 입고 2026-07-22 / 유통기한 2026-08-19 인 Lot
        lot = newLot(LOT_ID, prod, "LOT-260722-001");

        when(lotRepository.findByProdIdAndReceiptDtAndMfgDt(any(), any(), any())).thenReturn(Optional.empty());
        when(codeDetailRepository.existsById(any(CodeDetailId.class))).thenReturn(true);
        when(lotAttrChngRepository.save(any(LotAttrChng.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Prod newProd(long prodId, String prodCd) {
        Prod p = mock(Prod.class);
        when(p.getId()).thenReturn(prodId);
        when(p.getProdCd()).thenReturn(prodCd);
        when(p.getShelfLifeDays()).thenReturn(30);
        return p;
    }

    /** Lot 하나를 만들고 조회·락 스텁까지 걸어둔다 (다건 테스트에서 Lot을 더 붙이려고 뺐다) */
    private Lot newLot(long lotId, Prod ownerProd, String lotNo) {
        Lot l = Lot.builder()
                .prod(ownerProd)
                .lotNo(lotNo)
                .receiptDt(RECEIPT_DT)
                .mfgDt(LocalDate.of(2026, 7, 20))
                .expiryDt(LocalDate.of(2026, 8, 19))
                .build();
        ReflectionTestUtils.setField(l, "id", lotId);
        when(lotRepository.findById(lotId)).thenReturn(Optional.of(l));
        when(lotRepository.findByIdForUpdate(lotId)).thenReturn(Optional.of(l));
        return l;
    }

    private LotAttrChngRequest.Item item(long lotId, LocalDate mfgDt, LocalDate expiryDt, String rsnCd, String rsnDscr) {
        LotAttrChngRequest.Item it = new LotAttrChngRequest.Item();
        it.setLotId(lotId);
        it.setMfgDt(mfgDt);
        it.setExpiryDt(expiryDt);
        it.setRsnCd(rsnCd);
        it.setRsnDscr(rsnDscr);
        return it;
    }

    private LotAttrChngRequest.Item item(long lotId, LocalDate mfgDt, LocalDate expiryDt) {
        return item(lotId, mfgDt, expiryDt, "ERR_REG", null);
    }

    private LotAttrChngRequest request(LotAttrChngRequest.Item... items) {
        LotAttrChngRequest req = new LotAttrChngRequest();
        req.setItems(Arrays.asList(items));
        return req;
    }

    /** 이 테스트 대부분은 기본 Lot 1건만 정정한다 */
    private LotAttrChngRequest request(LocalDate mfgDt, LocalDate expiryDt, String rsnCd, String rsnDscr) {
        return request(item(LOT_ID, mfgDt, expiryDt, rsnCd, rsnDscr));
    }

    private LotAttrChngRequest request(LocalDate mfgDt, LocalDate expiryDt) {
        return request(item(LOT_ID, mfgDt, expiryDt));
    }

    @Test
    @DisplayName("정정하면 Lot의 두 날짜가 바뀌고 변경 전/후가 이력 1행에 남는다")
    void changeRecordsBeforeAndAfter() {
        lotAttrChngService.change(request(LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 17)));

        assertEquals(LocalDate.of(2026, 7, 18), lot.getMfgDt());
        assertEquals(LocalDate.of(2026, 8, 17), lot.getExpiryDt());

        ArgumentCaptor<LotAttrChng> captor = ArgumentCaptor.forClass(LotAttrChng.class);
        verify(lotAttrChngRepository).save(captor.capture());
        LotAttrChng chng = captor.getValue();
        assertEquals(LocalDate.of(2026, 7, 20), chng.getBfrMfgDt());
        assertEquals(LocalDate.of(2026, 7, 18), chng.getAftMfgDt());
        assertEquals(LocalDate.of(2026, 8, 19), chng.getBfrExpiryDt());
        assertEquals(LocalDate.of(2026, 8, 17), chng.getAftExpiryDt());
        // Lot 번호는 스냅샷으로 함께 담긴다 (로그 자기완결)
        assertEquals("LOT-260722-001", chng.getLotNo());
    }

    @Test
    @DisplayName("상품 락을 Lot 락보다 먼저 잡는다 — 검수(findOrCreateLot)와 같은 순서라 교착이 없다")
    void locksProdBeforeLot() {
        lotAttrChngService.change(request(LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 17)));

        InOrder order = org.mockito.Mockito.inOrder(prodRepository, lotRepository);
        order.verify(prodRepository).findByIdForUpdate(PROD_ID);
        order.verify(lotRepository).findByIdForUpdate(LOT_ID);
    }

    @Test
    @DisplayName("유통기한 미관리 상품의 Lot은 정정 대상이 아니다 — 두 날짜가 비어 있는 것이 그 상품의 정의다")
    void rejectsLotOfProdWithoutShelfLife() {
        when(prod.getShelfLifeDays()).thenReturn(null);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> lotAttrChngService.change(request(LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 17))));
        assertTrue(e.getMessage().contains("미관리"));
        verify(lotAttrChngRepository, never()).save(any());
    }

    @Test
    @DisplayName("관리 상품의 Lot에서 날짜를 비울 수 없다")
    void rejectsNullDates() {
        assertThrows(IllegalArgumentException.class,
                () -> lotAttrChngService.change(request(null, LocalDate.of(2026, 8, 17))));
        assertThrows(IllegalArgumentException.class,
                () -> lotAttrChngService.change(request(LocalDate.of(2026, 7, 18), null)));
        verify(lotAttrChngRepository, never()).save(any());
    }

    @Test
    @DisplayName("제조일자가 입고일자보다 미래일 수 없다 — 검수와 같은 규칙")
    void rejectsMfgAfterReceipt() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> lotAttrChngService.change(request(RECEIPT_DT.plusDays(1), LocalDate.of(2026, 8, 25))));
        assertTrue(e.getMessage().contains("입고일자"));
    }

    @Test
    @DisplayName("유통기한이 제조일자보다 이전일 수 없다")
    void rejectsExpiryBeforeMfg() {
        assertThrows(IllegalArgumentException.class,
                () -> lotAttrChngService.change(request(LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 17))));
    }

    @Test
    @DisplayName("변경 전후가 완전히 같으면 거부한다 — 바꿀 게 없는 저장은 로그만 늘린다")
    void rejectsNoOpChange() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> lotAttrChngService.change(request(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 19))));
        assertTrue(e.getMessage().contains("같습니다"));
        verify(lotAttrChngRepository, never()).save(any());
    }

    @Test
    @DisplayName("배치 재사용 키(상품+입고일자+제조일자)가 다른 Lot과 겹치면 거부한다 — DB가 막아주지 않는 지점")
    void rejectsBatchKeyConflict() {
        Lot other = Lot.builder().prod(prod).lotNo("LOT-260722-002").receiptDt(RECEIPT_DT)
                .mfgDt(LocalDate.of(2026, 7, 18)).expiryDt(LocalDate.of(2026, 8, 17)).build();
        ReflectionTestUtils.setField(other, "id", 9L);
        when(lotRepository.findByProdIdAndReceiptDtAndMfgDt(PROD_ID, RECEIPT_DT, LocalDate.of(2026, 7, 18)))
                .thenReturn(Optional.of(other));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> lotAttrChngService.change(request(LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 17))));
        assertTrue(e.getMessage().contains("같은 배치"));
        // 원래 값이 그대로 남아야 한다 (트랜잭션 롤백 전에 엔티티가 더럽혀지지 않는다)
        assertEquals(LocalDate.of(2026, 7, 20), lot.getMfgDt());
        verify(lotAttrChngRepository, never()).save(any());
    }

    @Test
    @DisplayName("자기 자신이 배치 키로 조회돼도 충돌이 아니다")
    void allowsSelfAsBatchKeyMatch() {
        when(lotRepository.findByProdIdAndReceiptDtAndMfgDt(PROD_ID, RECEIPT_DT, LocalDate.of(2026, 7, 18)))
                .thenReturn(Optional.of(lot));

        lotAttrChngService.change(request(LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 17)));

        assertEquals(LocalDate.of(2026, 7, 18), lot.getMfgDt());
    }

    @Test
    @DisplayName("제조일자를 그대로 두고 유통기한만 고치면 배치 키 조회 자체를 하지 않는다")
    void skipsBatchKeyCheckWhenMfgUnchanged() {
        lotAttrChngService.change(request(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 25)));

        assertEquals(LocalDate.of(2026, 8, 25), lot.getExpiryDt());
        verify(lotRepository, never()).findByProdIdAndReceiptDtAndMfgDt(any(), any(), any());
    }

    @Test
    @DisplayName("사유코드는 필수이고 그룹에 있어야 한다")
    void requiresValidRsnCd() {
        assertThrows(IllegalArgumentException.class,
                () -> lotAttrChngService.change(request(LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 17), "  ", null)));

        when(codeDetailRepository.existsById(any(CodeDetailId.class))).thenReturn(false);
        assertThrows(IllegalArgumentException.class,
                () -> lotAttrChngService.change(request(LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 17), "NOPE", null)));
    }

    @Test
    @DisplayName("기타(ETC)일 때만 사유 텍스트를 받는다 — 그 외 코드의 텍스트는 무시하고 null로 저장")
    void keepsRsnDscrOnlyForEtc() {
        assertThrows(IllegalArgumentException.class,
                () -> lotAttrChngService.change(request(LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 17), "ETC", " ")));

        lotAttrChngService.change(request(LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 17), "ERR_REG", "무시될 텍스트"));
        ArgumentCaptor<LotAttrChng> captor = ArgumentCaptor.forClass(LotAttrChng.class);
        verify(lotAttrChngRepository).save(captor.capture());
        assertNull(captor.getValue().getRsnDscr());
    }

    // ── 다건 정정 ────────────────────────────────────────────

    @Test
    @DisplayName("여러 Lot을 한 번에 정정하면 건마다 이력 1행이 남고 사유도 건별로 따로다")
    void changesEachLotWithItsOwnRsn() {
        Lot lot2 = newLot(7L, newProd(2L, "PROD-0002"), "LOT-260722-002");

        lotAttrChngService.change(request(
                item(LOT_ID, LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 17), "ERR_REG", null),
                item(7L, LocalDate.of(2026, 7, 19), LocalDate.of(2026, 8, 18), "ETC", "벤더 인쇄값 상이")));

        assertEquals(LocalDate.of(2026, 7, 18), lot.getMfgDt());
        assertEquals(LocalDate.of(2026, 7, 19), lot2.getMfgDt());

        ArgumentCaptor<LotAttrChng> captor = ArgumentCaptor.forClass(LotAttrChng.class);
        verify(lotAttrChngRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<LotAttrChng> saved = captor.getAllValues();
        assertEquals("ERR_REG", saved.get(0).getRsnCd());
        assertNull(saved.get(0).getRsnDscr());
        assertEquals("ETC", saved.get(1).getRsnCd());
        assertEquals("벤더 인쇄값 상이", saved.get(1).getRsnDscr());
    }

    @Test
    @DisplayName("다건이면 상품·Lot 오름차순으로 락을 잡는다 — 겹치는 Lot을 반대 순서로 보낸 두 요청이 교착되지 않도록")
    void locksInProdThenLotOrder() {
        Prod prod2 = newProd(2L, "PROD-0002");
        newLot(6L, prod, "LOT-260722-006");
        newLot(7L, prod2, "LOT-260722-007");

        // 일부러 역순으로 실어 보낸다 (상품 2 → 상품 1의 큰 Lot → 상품 1의 작은 Lot)
        lotAttrChngService.change(request(
                item(7L, LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 17)),
                item(6L, LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 17)),
                item(LOT_ID, LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 17))));

        InOrder order = org.mockito.Mockito.inOrder(prodRepository, lotRepository);
        order.verify(lotRepository).findByIdForUpdate(LOT_ID); // 상품 1 · Lot 5
        order.verify(lotRepository).findByIdForUpdate(6L);     // 상품 1 · Lot 6
        order.verify(prodRepository).findByIdForUpdate(2L);
        order.verify(lotRepository).findByIdForUpdate(7L);      // 상품 2 · Lot 7
    }

    @Test
    @DisplayName("한 건이라도 검증에 걸리면 요청 전체가 실패한다 — 절반만 정정된 상태를 만들지 않는다")
    void rejectsWholeRequestWhenAnyItemInvalid() {
        newLot(7L, newProd(2L, "PROD-0002"), "LOT-260722-002");

        // 걸리는 쪽(Lot 5 · 제조일자 > 입고일자)이 정렬 순서상 먼저 처리돼 뒤 건은 시작도 못 한다
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> lotAttrChngService.change(request(
                        item(7L, LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 17)),
                        item(LOT_ID, RECEIPT_DT.plusDays(1), LocalDate.of(2026, 8, 25)))));
        assertTrue(e.getMessage().contains("입고일자"));
        verify(lotAttrChngRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 Lot을 두 번 실으면 거부한다 — 뒤엣것이 앞엣것을 덮어쓰며 이력만 두 줄 남는다")
    void rejectsDuplicateLot() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> lotAttrChngService.change(request(
                        item(LOT_ID, LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 17)),
                        item(LOT_ID, LocalDate.of(2026, 7, 19), LocalDate.of(2026, 8, 18)))));
        assertTrue(e.getMessage().contains("두 번"));
        verify(lotAttrChngRepository, never()).save(any());
    }

    @Test
    @DisplayName("대상이 비어 있거나 없는 Lot이면 락을 잡기 전에 거부한다")
    void rejectsEmptyOrUnknownTarget() {
        assertThrows(IllegalArgumentException.class, () -> lotAttrChngService.change(new LotAttrChngRequest()));
        assertThrows(IllegalArgumentException.class, () -> lotAttrChngService.change(request()));

        when(lotRepository.findById(99L)).thenReturn(Optional.empty());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> lotAttrChngService.change(request(
                        item(LOT_ID, LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 17)),
                        item(99L, LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 17)))));
        assertTrue(e.getMessage().contains("존재하지 않는 Lot"));
        verify(prodRepository, never()).findByIdForUpdate(any());
    }
}
