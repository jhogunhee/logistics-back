package com.project.wmsback.outbound.repository;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.store.entity.Store;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.outbound.dto.AllocTargetSearchCond;
import com.project.wmsback.outbound.dto.AllocWaveResponse;
import com.project.wmsback.outbound.entity.OutbAlloc;
import com.project.wmsback.outbound.entity.OutbLine;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.entity.WavRegTyp;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 할당 대상 웨이브 조회 — <b>주문수량 합계와 할당수량 합계가 같은 모수를 보는지</b>를 본다.
 * 출고확정은 주문 단위라 한 웨이브에 확정된 주문과 남은 주문이 섞이는데, 확정된 주문의 할당 행은
 * 확정 후에도 남는다(삭제하는 곳은 할당해제뿐). 한쪽 합계에서만 확정분을 빼면 잔량이 실제보다
 * 작게 나와 아직 채울 게 남은 웨이브가 목록에서 사라진다.
 *
 * <p>검증 대상이 쿼리의 술어라 목으로는 덮이지 않아 실제 DB에 붙는다 — {@code WmsBackApplicationTests}와
 * 같이 {@code DB_URL}이 있는 환경에서만 돌고, 만든 데이터는 테스트가 끝나며 롤백된다.
 * 마스터(점포·상품·재고)는 만들지 않고 있는 행을 빌려 쓴다 — 검증에 쓰이지 않는 값이라
 * 로케이션·Lot까지 세우는 값이 없다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@Transactional
class OutbAllocRepositoryTest {

    @Autowired OutbAllocRepository outbAllocRepository;
    @Autowired OutbWaveRepository outbWaveRepository;
    @Autowired OutbOrderRepository outbOrderRepository;

    @PersistenceContext EntityManager em;

    private static final LocalDate EXPCT_DE = LocalDate.of(2026, 8, 20);

    private Store store;
    private Prod prod;
    private Inv inv;
    private String tag;
    private OutbWave wave;

    @BeforeEach
    void setUp() {
        store = require(anyRow(Store.class), "점포");
        prod = require(anyRow(Prod.class), "상품");
        inv = require(anyRow(Inv.class), "재고");

        tag = Long.toString(System.nanoTime(), 36);
        wave = outbWaveRepository.save(OutbWave.builder().wavNo("WV-TEST-" + tag).build());
    }

    @Test
    @DisplayName("확정 주문이 섞인 웨이브 — 확정분은 주문수량에서도 할당수량에서도 빠져 잔량이 남는다")
    void mixedWaveKeepsRemainOfOpenOrder() {
        // 확정된 주문 — 전량 집품하고 나갔다. 할당 행 10개는 확정 뒤에도 남아 있다
        OutbOrder shipped = newOrder("A", 10L);
        alloc(shipped, 10L, 10L);
        shipped.ship();

        // 남은 주문 — 할당이 한 건도 없다. 이 웨이브는 아직 채울 게 있다
        newOrder("B", 10L);
        em.flush();

        AllocWaveResponse row = onlyRow();
        assertEquals(1L, row.orderCount(), "확정된 주문은 세지 않는다");
        assertEquals(10L, row.odrQty());
        assertEquals(0L, row.alocQty(), "확정된 주문의 할당은 세지 않는다");
        assertEquals(10L, row.remainQty(), "확정분을 할당수량에만 세면 잔량이 0이 되어 웨이브가 사라진다");
    }

    @Test
    @DisplayName("확정 주문이 섞인 웨이브 — 남은 주문의 부분할당만 할당수량에 잡힌다")
    void mixedWaveCountsOnlyOpenOrderAlloc() {
        OutbOrder shipped = newOrder("A", 10L);
        alloc(shipped, 10L, 10L);
        shipped.ship();

        // 남은 주문 — 10 중 3만 할당됐다. 확정분까지 세면 할당 13 > 주문 10이라 잔량이 음수가 된다
        OutbOrder open = newOrder("B", 10L);
        alloc(open, 3L, 0L);
        em.flush();

        AllocWaveResponse row = onlyRow();
        assertEquals(10L, row.odrQty());
        assertEquals(3L, row.alocQty());
        assertEquals(7L, row.remainQty());
    }

    // ── 픽스처 ──────────────────────────────────────────────────────────────

    private OutbOrder newOrder(String suffix, Long odrQty) {
        OutbOrder order = OutbOrder.builder()
                .outbNo("OB-TEST-" + tag + "-" + suffix)
                .omsOutbOrderId(0L)
                .store(store)
                .odrDe(EXPCT_DE)
                .expctDe(EXPCT_DE)
                .build();
        order.addLine(OutbLine.builder().prod(prod).odrQty(odrQty).build());
        order.assignWave(wave, WavRegTyp.MANUAL);
        return outbOrderRepository.save(order);
    }

    private void alloc(OutbOrder order, Long alocQty, long pikngQty) {
        OutbAlloc alloc = OutbAlloc.builder()
                .outbLine(order.getLines().get(0))
                .inv(inv)
                .alocQty(alocQty)
                .build();
        if (pikngQty > 0) {
            alloc.addPikngQty(pikngQty);
        }
        outbAllocRepository.save(alloc);
    }

    /** 이 테스트가 만든 웨이브만 골라 조회한다 — 개발 DB의 다른 웨이브와 섞이지 않게 */
    private AllocWaveResponse onlyRow() {
        AllocTargetSearchCond cond = new AllocTargetSearchCond();
        cond.setWavNo(wave.getWavNo());
        List<AllocWaveResponse> waves = outbAllocRepository.searchTargetWaves(cond);
        assertEquals(1, waves.size(), "잔량이 남은 웨이브는 목록에 있어야 한다");
        return waves.get(0);
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
