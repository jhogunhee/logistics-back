package com.project.wmsback.inventory.repository;

import com.project.common.dto.PageCond;
import com.project.common.dto.PageResponse;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.TmpZon;
import com.project.wmsback.inventory.dto.InvHistResponse;
import com.project.wmsback.inventory.dto.InvHistSearchCond;
import com.project.wmsback.inventory.dto.InvHldAcrstResponse;
import com.project.wmsback.inventory.dto.InvHldAcrstSearchCond;
import com.project.wmsback.inventory.entity.InvHist;
import com.project.wmsback.inventory.entity.InvHldAcrst;
import com.project.wmsback.inventory.entity.InvHldRlzAcrst;
import com.project.wmsback.inventory.entity.TxTyp;
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
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * append-only 목록 셋의 서버 페이징 — offset/limit이 행을 맞게 자르는지, 셈이 조건을 같이 보는지.
 * 자르기와 셈은 SQL이 하는 일이라 목으로 덮이지 않아 실제 DB에 붙는다 — {@code DB_URL}이 있는
 * 환경에서만 돌고, 만든 데이터는 끝나며 롤백된다.
 *
 * <p>상품·Lot을 새로 만들고 보류번호에 고유 꼬리표를 붙이는 이유는 개발 DB의 기존 행과 섞이지
 * 않게 하기 위해서다 — 빌려 쓰면 totCnt가 과거 데이터에 따라 달라져 단정할 수 없다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@Transactional
class InvPagingRepositoryTest {

    /** 3+3+1로 갈리는 수 — 마지막 페이지가 덜 차는 경우까지 한 번에 본다 */
    private static final int ROWS = 7;
    private static final int SIZE = 3;

    @Autowired InvHistRepository invHistRepository;
    @Autowired InvHldAcrstRepository invHldAcrstRepository;
    @Autowired InvHldRlzAcrstRepository invHldRlzAcrstRepository;

    @PersistenceContext EntityManager em;

    private Prod prod;
    private Loc loc;
    private Lot lot;
    private String tag;

    @BeforeEach
    void setUp() {
        loc = require(anyRow(Loc.class), "로케이션");

        tag = Long.toString(System.nanoTime(), 36);
        prod = Prod.builder()
                .prodCd("PROD-PAGE-" + tag)
                .prodNm("페이징 조회 테스트 상품")
                .tmpZon(TmpZon.DRY)
                .inbUomCd("EA")
                .outbUomCd("EA")
                .build();
        em.persist(prod);
        lot = Lot.builder()
                .prod(prod)
                .lotNo("LOT-PAGE-" + tag)
                .receiptDt(LocalDate.of(2026, 8, 26))
                .build();
        em.persist(lot);
    }

    // ── 재고이력 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("재고이력 — 한 페이지는 size만큼만 오고 totCnt는 전체 건수다")
    void invHistFirstPage() {
        givenInvHist();

        PageResponse<InvHistResponse> page = invHist(1, SIZE);

        assertEquals(SIZE, page.rows().size());
        assertEquals(ROWS, page.totCnt());
        assertEquals(1, page.page());
        assertEquals(SIZE, page.size());
    }

    @Test
    @DisplayName("재고이력 — 페이지를 이어 받으면 겹치지 않고 전량이 정확히 한 번씩 나온다")
    void invHistPagesDoNotOverlap() {
        givenInvHist();

        assertPagesCoverAll(ROWS, SIZE,
                (page, size) -> invHist(page, size).rows().stream().map(InvHistResponse::getInvHistId).toList());
    }

    @Test
    @DisplayName("재고이력 — 마지막 페이지를 넘어가면 rows는 비고 totCnt는 그대로다")
    void invHistBeyondLastPage() {
        givenInvHist();

        PageResponse<InvHistResponse> page = invHist(99, SIZE);

        assertTrue(page.rows().isEmpty());
        assertEquals(ROWS, page.totCnt());
    }

    @Test
    @DisplayName("재고이력 — 조건은 목록과 셈에 같이 걸린다 (totCnt도 함께 줄어야 한다)")
    void invHistCountHonoursConditions() {
        givenInvHist();

        InvHistSearchCond cond = invHistCond();
        cond.setTxTyp(TxTyp.ADJUST);
        PageResponse<InvHistResponse> page = invHistRepository.search(cond, pageCond(1, SIZE));

        // givenInvHist가 짝수 번째만 ADJUST로 넣는다
        assertEquals(3, page.totCnt());
        assertEquals(3, page.rows().size());
    }

    // ── 보류 실적 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("보류 실적 — 한 페이지는 size만큼, totCnt는 전체 건수")
    void invHldAcrstFirstPage() {
        givenHldAcrst();

        PageResponse<InvHldAcrstResponse> page = hldAcrst(1, SIZE);

        assertEquals(SIZE, page.rows().size());
        assertEquals(ROWS, page.totCnt());
    }

    @Test
    @DisplayName("보류 실적 — 페이지를 이어 받으면 겹치지 않고 전량이 정확히 한 번씩 나온다")
    void invHldAcrstPagesDoNotOverlap() {
        givenHldAcrst();

        assertPagesCoverAll(ROWS, SIZE,
                (page, size) -> hldAcrst(page, size).rows().stream().map(InvHldAcrstResponse::getAcrstId).toList());
    }

    // ── 해제 실적 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("해제 실적 — 한 페이지는 size만큼, totCnt는 전체 건수")
    void invHldRlzAcrstFirstPage() {
        givenHldRlzAcrst();

        PageResponse<InvHldAcrstResponse> page = hldRlzAcrst(1, SIZE);

        assertEquals(SIZE, page.rows().size());
        assertEquals(ROWS, page.totCnt());
    }

    @Test
    @DisplayName("해제 실적 — 페이지를 이어 받으면 겹치지 않고 전량이 정확히 한 번씩 나온다")
    void invHldRlzAcrstPagesDoNotOverlap() {
        givenHldRlzAcrst();

        assertPagesCoverAll(ROWS, SIZE,
                (page, size) -> hldRlzAcrst(page, size).rows().stream().map(InvHldAcrstResponse::getAcrstId).toList());
    }

    // ── 상한 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("size 상한을 넘겨도 MAX_SIZE로 깎여 나간다 — 페이징을 우회할 수 없다")
    void sizeIsCappedAtQueryLevel() {
        givenInvHist();

        PageResponse<InvHistResponse> page = invHist(1, 999_999);

        assertEquals(PageCond.MAX_SIZE, page.size());
        assertEquals(ROWS, page.rows().size());
    }

    // ── 픽스처 ──────────────────────────────────────────────────────────────

    private void givenInvHist() {
        for (int i = 0; i < ROWS; i++) {
            em.persist(InvHist.builder()
                    .txTyp(i % 2 == 0 ? TxTyp.ADJUST : TxTyp.RECEIVE)
                    .prod(prod)
                    .loc(loc)
                    .lot(lot)
                    .qty((long) (i + 1))
                    .build());
        }
        em.flush();
    }

    private void givenHldAcrst() {
        for (int i = 0; i < ROWS; i++) {
            em.persist(InvHldAcrst.builder()
                    .hldNo(hldNo(i))
                    .prod(prod)
                    .loc(loc)
                    .lot(lot)
                    .hldQty((long) (i + 1))
                    .rsnCd("DMG")
                    .build());
        }
        em.flush();
    }

    private void givenHldRlzAcrst() {
        for (int i = 0; i < ROWS; i++) {
            em.persist(InvHldRlzAcrst.builder()
                    .hldNo(hldNo(i))
                    .prod(prod)
                    .loc(loc)
                    .lot(lot)
                    .rlzQty((long) (i + 1))
                    .rsnCd("DMG")
                    .build());
        }
        em.flush();
    }

    private String hldNo(int i) {
        return "HLD-" + tag + "-" + i;
    }

    private PageResponse<InvHistResponse> invHist(int page, int size) {
        return invHistRepository.search(invHistCond(), pageCond(page, size));
    }

    private PageResponse<InvHldAcrstResponse> hldAcrst(int page, int size) {
        return invHldAcrstRepository.search(hldCond(), pageCond(page, size));
    }

    private PageResponse<InvHldAcrstResponse> hldRlzAcrst(int page, int size) {
        return invHldRlzAcrstRepository.search(hldCond(), pageCond(page, size));
    }

    /** 이번 테스트가 만든 행만 보도록 좁힌다 */
    private InvHistSearchCond invHistCond() {
        InvHistSearchCond cond = new InvHistSearchCond();
        cond.setProdCd("PROD-PAGE-" + tag);
        return cond;
    }

    private InvHldAcrstSearchCond hldCond() {
        InvHldAcrstSearchCond cond = new InvHldAcrstSearchCond();
        cond.setHldNo("HLD-" + tag + "-");
        return cond;
    }

    private PageCond pageCond(int page, int size) {
        PageCond cond = new PageCond();
        cond.setPage(page);
        cond.setSize(size);
        return cond;
    }

    /** 페이지를 끝까지 훑어 id를 모으고, 전량이 겹침·누락 없이 정확히 한 번씩 나오는지 본다 */
    private void assertPagesCoverAll(int total, int size, BiFunction<Integer, Integer, List<Long>> pageOf) {
        List<Long> collected = new java.util.ArrayList<>();
        int pages = (total + size - 1) / size;
        for (int page = 1; page <= pages; page++) {
            List<Long> ids = pageOf.apply(page, size);
            int expected = page < pages ? size : total - size * (pages - 1);
            assertEquals(expected, ids.size(), page + "페이지 행 수");
            collected.addAll(ids);
        }
        assertEquals(total, collected.size());
        assertEquals(total, new java.util.HashSet<>(collected).size(), "페이지 사이에 겹치는 행이 있다");
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
