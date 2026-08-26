package com.project.wmsback.inbound.repository;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.TmpZon;
import com.project.mdm.vendor.entity.Vendor;
import com.project.wmsback.inbound.dto.IbOrderResponse;
import com.project.wmsback.inbound.dto.IbOrderSearchCond;
import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.entity.IbPrgr;
import com.project.wmsback.inbound.entity.PutawayTask;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.Lot;
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
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 헤더 진행단계 = 라인 단계의 집계 — <b>검수까지는 max, 그 위로는 min, 바닥은 검수</b>.
 * <p>
 * 판정이 SQL CASE라 목으로 덮이지 않아 실제 DB에 붙는다 — {@code DB_URL}이 있는 환경에서만 돌고,
 * 만든 데이터는 끝나며 롤백된다.
 * <p>
 * 마지막 케이스는 <b>헤더(SQL)와 라인(Java)이 같은 답을 내는지</b>를 대사한다. 사다리가 두 벌 있는데
 * SQL이 Java를 부를 수 없어, 둘이 갈리는 것을 잡는 안전망이 여기뿐이다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@Transactional
class IbOrderProgressRepositoryTest {

    @Autowired IbOrderRepository ibOrderRepository;
    @Autowired PutawayTaskQueryRepository putawayTaskQueryRepository;

    @PersistenceContext EntityManager em;

    private static final LocalDate EXPCT_DE = LocalDate.of(2026, 8, 26);

    private Vendor vendor;
    private Loc loc;
    private Prod prod;
    private Lot lot;
    private String tag;

    @BeforeEach
    void setUp() {
        vendor = require(anyRow(Vendor.class), "벤더");
        loc = require(anyRow(Loc.class), "로케이션");

        tag = Long.toString(System.nanoTime(), 36);
        prod = Prod.builder()
                .prodCd("PROD-PRGR-" + tag)
                .prodNm("진행단계 테스트 상품")
                .tmpZon(TmpZon.DRY)
                .inbUomCd("EA")
                .outbUomCd("EA")
                .build();
        em.persist(prod);
        lot = Lot.builder()
                .prod(prod)
                .lotNo("LOT-PRGR-" + tag)
                .receiptDt(EXPCT_DE)
                .build();
        em.persist(lot);
    }

    @Test
    @DisplayName("라인이 없는 주문은 입고예정 — left join의 null 행이 「검수」로 새면 안 된다")
    void noLines() {
        IbOrder order = order();
        em.flush();

        assertEquals(IbPrgr.SCHEDULED, prgrOf(order));
    }

    @Test
    @DisplayName("전 라인 미도착이면 입고예정")
    void allLinesUntouched() {
        order(100L, 100L);

        assertEquals(IbPrgr.SCHEDULED, prgrOf());
    }

    @Test
    @DisplayName("A 적치완료 · B 미도착 → 검수 (바닥 고정) — 헤더가 「아무것도 안 왔다」고 말하면 안 된다")
    void doneAndUntouchedFallsToReceiving() {
        IbOrder order = order(100L, 100L);
        receive(order, 0, 100L);
        putaway(order, 0, 100L);
        em.flush();

        assertEquals(IbPrgr.RECEIVING, prgrOf(order));
    }

    @Test
    @DisplayName("A 적치완료 · B 검수 → 검수 (가장 덜 끝난 라인을 따라간다)")
    void doneAndReceivingFallsToReceiving() {
        IbOrder order = order(100L, 100L);
        receive(order, 0, 100L);
        putaway(order, 0, 100L);
        receive(order, 1, 40L);
        em.flush();

        assertEquals(IbPrgr.RECEIVING, prgrOf(order));
    }

    @Test
    @DisplayName("A 적치완료 · B 적치지시 → 적치지시")
    void doneAndDirectedFallsToDirected() {
        IbOrder order = order(100L, 100L);
        receive(order, 0, 100L);
        putaway(order, 0, 100L);
        receive(order, 1, 100L);
        direct(order, 1, 100L);                       // 미완료 지시
        em.flush();

        assertEquals(IbPrgr.PTAWY_DRCT, prgrOf(order));
    }

    @Test
    @DisplayName("전 라인 적치완료면 적치완료 — 부분 검수여도 온 것을 다 옮겼으면 확정 대기다")
    void allDonePartiallyReceived() {
        IbOrder order = order(100L, 100L);
        receive(order, 0, 40L);
        putaway(order, 0, 40L);
        receive(order, 1, 100L);
        putaway(order, 1, 100L);
        em.flush();

        assertEquals(IbPrgr.PTAWY_CMPL, prgrOf(order));
    }

    @Test
    @DisplayName("전량 검수했어도 지시 전이면 검수 — 지시가 없는데 「적치지시」라고 하면 안 된다")
    void receivedButNotDirected() {
        IbOrder order = order(100L);
        receive(order, 0, 100L);
        em.flush();

        assertEquals(IbPrgr.RECEIVING, prgrOf(order));
    }

    @Test
    @DisplayName("헤더가 확정이면 수량과 무관하게 입고확정")
    void confirmedWins() {
        IbOrder order = order(100L);
        receive(order, 0, 80L);
        putaway(order, 0, 80L);
        order.confirm();
        em.flush();

        assertEquals(IbPrgr.CONFIRMED, prgrOf(order));
    }

    @Test
    @DisplayName("헤더(SQL)와 라인(Java)이 같은 사다리를 탄다 — 두 벌 구현이 갈리는 것을 잡는 대사")
    void headerAgreesWithLines() {
        IbOrder order = order(100L, 100L);
        receive(order, 0, 40L);
        direct(order, 0, 40L);                        // 부분 검수 + 미완료 지시 → 적치지시
        receive(order, 1, 100L);
        direct(order, 1, 100L);
        em.flush();

        Set<Long> openIds = putawayTaskQueryRepository.openIbLineIds(
                order.getLines().stream().map(IbLine::getId).toList());
        List<IbPrgr> lineStages = order.getLines().stream()
                .map(line -> line.progressStatus(openIds.contains(line.getId())))
                .toList();

        // 두 라인 모두 적치지시 → 헤더도 적치지시
        assertEquals(List.of(IbPrgr.PTAWY_DRCT, IbPrgr.PTAWY_DRCT), lineStages);
        assertEquals(IbPrgr.PTAWY_DRCT, prgrOf(order));
    }

    // ── 픽스처 ──────────────────────────────────────────────────────────────

    private IbOrder order(long... expctQtys) {
        IbOrder order = IbOrder.builder()
                .ibNo("IB-PRGR-" + tag + "-" + expctQtys.length)
                .omsIbOrderId(0L)
                .vendor(vendor)
                .expctDe(EXPCT_DE)
                .odrDvsn("NRML")
                .build();
        for (long expctQty : expctQtys) {
            order.addLine(IbLine.builder().prod(prod).expctQty(expctQty).build());
        }
        em.persist(order);
        em.flush();
        return order;
    }

    private void receive(IbOrder order, int idx, long qty) {
        if (order.getStatus() == com.project.wmsback.inbound.entity.IbStatus.SCHEDULED) {
            order.startReceiving();
        }
        order.getLines().get(idx).receive(qty);
    }

    private void putaway(IbOrder order, int idx, long qty) {
        order.getLines().get(idx).putaway(qty);
    }

    /** 미완료(DIRECTED) 적치지시 한 건 */
    private void direct(IbOrder order, int idx, long qty) {
        em.persist(PutawayTask.builder()
                .ibLine(order.getLines().get(idx))
                .lot(lot)
                .toLoc(loc)
                .drctQty(qty)
                .build());
    }

    /** 이번 테스트가 만든 주문만 조회한다 — 개발 DB의 기존 입고건과 섞이지 않게 */
    private IbPrgr prgrOf() {
        List<IbOrderResponse> rows = search();
        assertEquals(1, rows.size(), "이번 테스트가 만든 주문이 하나만 잡혀야 한다");
        return rows.get(0).getPrgr();
    }

    private IbPrgr prgrOf(IbOrder order) {
        return search().stream()
                .filter(r -> r.getIbOrderId().equals(order.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("조회 결과에 이 주문이 있어야 한다: " + order.getIbNo()))
                .getPrgr();
    }

    private List<IbOrderResponse> search() {
        IbOrderSearchCond cond = new IbOrderSearchCond();
        cond.setIbNo("IB-PRGR-" + tag);
        return ibOrderRepository.search(cond);
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
