package com.project.wmsback.inventory.repository;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.TmpZon;
import com.project.mdm.vendor.entity.Vendor;
import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inventory.service.ProdStockPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 상품별 재고 현황 — 자동발주가 「이 상품이 창고에 · 오는 중에 얼마나 있나」를 세는 쿼리.
 * 검증 대상이 쿼리의 술어(입고확정 제외 · 반품 제외 · 예정 − 검수 − 불량)라 목으로는 덮이지 않아
 * 실제 DB에 붙는다 — {@code DB_URL}이 있는 환경에서만 돌고, 만든 데이터는 끝나며 롤백된다.
 *
 * <p>상품을 새로 만들어 쓰는 이유는 개발 DB의 기존 재고·입고예정과 섞이지 않게 하기 위해서다 —
 * 빌려 쓰면 합계가 그 상품의 과거 데이터에 따라 달라져 단정할 수 없다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@Transactional
class ProdStockQueryRepositoryTest {

    @Autowired ProdStockQueryRepository prodStockQueryRepository;

    @PersistenceContext EntityManager em;

    private static final LocalDate EXPCT_DE = LocalDate.of(2026, 8, 27);

    private Vendor vendor;
    private Prod prod;
    private String tag;

    @BeforeEach
    void setUp() {
        vendor = require(anyRow(Vendor.class), "벤더");

        tag = Long.toString(System.nanoTime(), 36);
        prod = Prod.builder()
                .prodCd("PROD-TEST-" + tag)
                .prodNm("자동발주 조회 테스트 상품")
                .tmpZon(TmpZon.DRY)
                .inbUomCd("EA")
                .outbUomCd("EA")
                .build();
        em.persist(prod);
    }

    @Test
    @DisplayName("입고확정 전 입고예정의 잔량만 센다 — 예정 − 검수 − 불량")
    void countsOpenAsnRemainder() {
        asn("NRML", 100L, 30L, 0L);

        assertEquals(70L, stock().openAsnQty());
    }

    @Test
    @DisplayName("불량으로 받은 만큼도 잔량에서 뺀다 — 그 물건은 반품존에 있지 오는 중이 아니다")
    void rejectedQtyLeavesRemainder() {
        asn("NRML", 100L, 30L, 20L);

        assertEquals(50L, stock().openAsnQty());
    }

    @Test
    @DisplayName("입고확정된 건은 세지 않는다 — 결품이 못박혀 잔량이 「오고 있는 것」이 아니다")
    void ignoresConfirmedAsn() {
        IbOrder order = asn("NRML", 100L, 100L, 0L);
        order.getLines().get(0).putaway(100L);
        order.confirm();
        em.flush();

        assertTrue(stockOrNull() == null || stockOrNull().openAsnQty() == 0L);
    }

    @Test
    @DisplayName("반품 입고예정은 세지 않는다 — 벤더에게 시킨 물건이 아니다")
    void ignoresReturnsAsn() {
        asn(IbOrder.RTNGS, 100L, 0L, 0L);

        assertTrue(stockOrNull() == null || stockOrNull().openAsnQty() == 0L);
    }

    @Test
    @DisplayName("재고가 없는 상품도 예정이 있으면 가용 0으로 함께 돌아온다")
    void productWithoutInventoryStillReported() {
        asn("NRML", 40L, 0L, 0L);

        ProdStockPort.ProdStock stock = stock();
        assertEquals(0L, stock.avalQty());
        assertEquals(40L, stock.openAsnQty());
        assertEquals(40L, stock.total());
    }

    @Test
    @DisplayName("재고도 예정도 없는 상품은 맵에 없다 — 호출측이 0으로 본다")
    void absentWhenNothing() {
        assertTrue(prodStockQueryRepository.stockByProd(List.of(prod.getId())).isEmpty());
    }

    // ── 픽스처 ──────────────────────────────────────────────────────────────

    private IbOrder asn(String odrDvsn, long expct, long rcvd, long rjct) {
        boolean rtngs = IbOrder.RTNGS.equals(odrDvsn);
        IbOrder order = IbOrder.builder()
                .ibNo("IB-TEST-" + tag + "-" + odrDvsn)
                .omsIbOrderId(0L)
                .vendor(rtngs ? null : vendor)
                .store(rtngs ? require(anyRow(com.project.mdm.store.entity.Store.class), "점포") : null)
                .expctDe(EXPCT_DE)
                .odrDvsn(odrDvsn)
                .build();
        IbLine line = IbLine.builder().prod(prod).expctQty(expct).build();
        order.addLine(line);
        if (rcvd > 0) {
            line.receive(rcvd);
        }
        if (rjct > 0) {
            line.reject(rjct);
        }
        em.persist(order);
        em.flush();
        return order;
    }

    private ProdStockPort.ProdStock stock() {
        ProdStockPort.ProdStock row = stockOrNull();
        assertTrue(row != null, "조회 결과에 이 상품이 있어야 한다");
        return row;
    }

    private ProdStockPort.ProdStock stockOrNull() {
        Map<Long, ProdStockPort.ProdStock> stocks = prodStockQueryRepository.stockByProd(List.of(prod.getId()));
        return stocks.get(prod.getId());
    }

    private <T> Optional<T> anyRow(Class<T> type) {
        return em.createQuery("select e from " + type.getSimpleName() + " e", type)
                .setMaxResults(1).getResultList().stream().findFirst();
    }

    /** 개발 DB에 마스터가 없으면 실패가 아니라 건너뛴다 — 접속 가능 여부와 같은 성격의 전제다 */
    private static <T> T require(Optional<T> row, String label) {
        Assumptions.assumeTrue(row.isPresent(), label + " 마스터가 없는 DB입니다 — seed-dev.sql 적용 후 돌립니다.");
        return row.get();
    }
}
