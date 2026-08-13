package com.project.wmsback.warehouse.service;

import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.wmsback.warehouse.repository.LotRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 배치 재사용 키(상품+입고일자+제조일자)의 단일 정의 — 재사용/채번의 경계와,
 * 파생 쿼리 null 버그가 이미 만들어 둔 중복 Lot에 대한 결정적 선택(최초 생성분)을 본다.
 * 쿼리의 null 매치·정렬 자체는 findAllByBatchKey(JPQL)의 몫이라 여기서는 계약(리스트 첫 행 재사용)만 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LotIssuerTest {

    private static final LocalDate RECEIPT_DT = LocalDate.of(2026, 8, 4);

    @Mock LotRepository lotRepository;

    private LotIssuer lotIssuer;
    private Prod prod;

    @BeforeEach
    void setUp() {
        lotIssuer = new LotIssuer(lotRepository);
        prod = mock(Prod.class);
        when(prod.getId()).thenReturn(1L);
    }

    @Test
    @DisplayName("같은 배치 키의 중복 Lot(과거 null 버그 산물)이 있으면 예외 없이 최초 생성분을 재사용한다")
    void find_picksFirstWhenDuplicateBatchLotsExist() {
        Lot first = mock(Lot.class);
        Lot second = mock(Lot.class);
        // findAllByBatchKey는 lot_id 오름차순 — 첫 행이 최초 생성분이다
        when(lotRepository.findAllByBatchKey(1L, RECEIPT_DT, null)).thenReturn(List.of(first, second));

        Optional<Lot> found = lotIssuer.find(prod, RECEIPT_DT, null);

        assertSame(first, found.orElseThrow());
    }

    @Test
    @DisplayName("매치가 없으면 빈 Optional — find는 생성하지 않는다 (로트변경이 §7 검사 후 create를 직접 부르는 전제)")
    void find_returnsEmptyWithoutCreating() {
        when(lotRepository.findAllByBatchKey(anyLong(), any(), any())).thenReturn(List.of());

        assertTrue(lotIssuer.find(prod, RECEIPT_DT, LocalDate.of(2026, 8, 1)).isEmpty());
        verify(lotRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreate: 배치가 있으면 재사용하고 채번하지 않는다")
    void findOrCreate_reusesExistingBatch() {
        Lot existing = mock(Lot.class);
        when(lotRepository.findAllByBatchKey(1L, RECEIPT_DT, null)).thenReturn(List.of(existing));

        Lot result = lotIssuer.findOrCreate(prod, RECEIPT_DT, null);

        assertSame(existing, result);
        verify(lotRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreate: 관리 상품은 유통기한을 제조일자 + shelfLifeDays로 계산해 생성한다")
    void findOrCreate_computesExpiryForShelfLifeProd() {
        when(prod.getShelfLifeDays()).thenReturn(10);
        when(lotRepository.findAllByBatchKey(anyLong(), any(), any())).thenReturn(List.of());
        when(lotRepository.countByProdIdAndReceiptDt(1L, RECEIPT_DT)).thenReturn(0L);
        when(lotRepository.save(any(Lot.class))).thenAnswer(a -> a.getArgument(0));

        Lot created = lotIssuer.findOrCreate(prod, RECEIPT_DT, LocalDate.of(2026, 8, 1));

        assertEquals(LocalDate.of(2026, 8, 1), created.getMfgDt());
        assertEquals(LocalDate.of(2026, 8, 11), created.getExpiryDt());
    }

    @Test
    @DisplayName("findOrCreate: 미관리 상품(제조일자 null)은 두 날짜 null로 생성한다")
    void findOrCreate_createsNullDatesForNonShelfLifeProd() {
        when(lotRepository.findAllByBatchKey(anyLong(), any(), any())).thenReturn(List.of());
        when(lotRepository.countByProdIdAndReceiptDt(1L, RECEIPT_DT)).thenReturn(0L);
        when(lotRepository.save(any(Lot.class))).thenAnswer(a -> a.getArgument(0));

        Lot created = lotIssuer.findOrCreate(prod, RECEIPT_DT, null);

        assertNull(created.getMfgDt());
        assertNull(created.getExpiryDt());
    }

    @Test
    @DisplayName("create: 번호는 상품별·입고일자별 건수+1이고, 유통기한은 계산 없이 받은 값 그대로다 (로트변경의 화면 입력값 경로)")
    void create_issuesSequentialNoAndKeepsGivenExpiry() {
        when(lotRepository.countByProdIdAndReceiptDt(1L, RECEIPT_DT)).thenReturn(2L);
        when(lotRepository.save(any(Lot.class))).thenAnswer(a -> a.getArgument(0));

        lotIssuer.create(prod, RECEIPT_DT, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30));

        ArgumentCaptor<Lot> captor = ArgumentCaptor.forClass(Lot.class);
        verify(lotRepository).save(captor.capture());
        Lot created = captor.getValue();
        assertEquals("LOT-260804-003", created.getLotNo());
        assertEquals(RECEIPT_DT, created.getReceiptDt());
        assertEquals(LocalDate.of(2026, 8, 1), created.getMfgDt());
        assertEquals(LocalDate.of(2026, 9, 30), created.getExpiryDt()); // shelfLifeDays 미참조
    }
}
