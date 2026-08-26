package com.project.wmsback.strategy.core.repository;

import com.project.common.dto.PageCond;
import com.project.common.dto.PageResponse;
import com.project.wmsback.strategy.core.dto.ExecLogResponse;
import com.project.wmsback.strategy.core.entity.StgyExecLog;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import com.project.wmsback.strategy.core.entity.TrgrTyp;
import com.project.wmsback.strategy.core.service.StgyExecLogService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 전략 실행로그의 서버 페이징 — 여기만 QueryDSL이 아니라 Spring Data {@code Pageable}이라
 * 1부터인 page를 0부터로 옮기는 번역이 실제 SQL에서 맞는지 확인한다. {@code DB_URL}이 있는
 * 환경에서만 돌고, 만든 데이터는 끝나며 롤백된다.
 *
 * <p>고유한 stgyId를 하나 잡아 쓰는 이유는 개발 DB의 기존 로그와 섞이지 않게 하기 위해서다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@Transactional
class StgyExecLogPagingRepositoryTest {

    private static final int ROWS = 7;
    private static final int SIZE = 3;

    @Autowired StgyExecLogService stgyExecLogService;

    @PersistenceContext EntityManager em;

    private Long stgyId;

    @BeforeEach
    void setUp() {
        // 기존 로그와 겹치지 않을 id — 이 테스트가 넣은 행만 조회된다
        Long maxStgyId = em.createQuery("select coalesce(max(e.stgyId), 0) from StgyExecLog e", Long.class)
                .getSingleResult();
        stgyId = maxStgyId + 1_000_000L;

        for (int i = 0; i < ROWS; i++) {
            em.persist(StgyExecLog.builder()
                    .stgyTyp(StgyTyp.INSP)
                    .stgyId(stgyId)
                    .rvsnNo(1L)
                    // 짝수만 실행 기록, 홀수는 미리보기 — 기본 필터가 페이징과 함께 걸리는지 본다
                    .trgrTyp(i % 2 == 0 ? TrgrTyp.MANUAL : TrgrTyp.PREVIEW)
                    .tgtRef("IB-PAGE-" + i)
                    .rsltSmry("라인 " + i + "건")
                    .build());
        }
        em.flush();
    }

    @Test
    @DisplayName("한 페이지는 size만큼만 오고 totCnt는 조건에 걸린 전체 건수다")
    void firstPage() {
        PageResponse<ExecLogResponse> page = list(1, SIZE, List.of(TrgrTyp.MANUAL, TrgrTyp.AUTO, TrgrTyp.PREVIEW));

        assertEquals(SIZE, page.rows().size());
        assertEquals(ROWS, page.totCnt());
        assertEquals(1, page.page());
        assertEquals(SIZE, page.size());
    }

    @Test
    @DisplayName("페이지를 이어 받으면 겹치지 않고 전량이 정확히 한 번씩 나온다")
    void pagesDoNotOverlap() {
        List<Long> collected = new ArrayList<>();
        int pages = (ROWS + SIZE - 1) / SIZE;
        for (int page = 1; page <= pages; page++) {
            List<Long> ids = list(page, SIZE, List.of(TrgrTyp.MANUAL, TrgrTyp.AUTO, TrgrTyp.PREVIEW))
                    .rows().stream().map(ExecLogResponse::id).toList();
            int expected = page < pages ? SIZE : ROWS - SIZE * (pages - 1);
            assertEquals(expected, ids.size(), page + "페이지 행 수");
            collected.addAll(ids);
        }
        assertEquals(ROWS, collected.size());
        assertEquals(ROWS, new HashSet<>(collected).size(), "페이지 사이에 겹치는 행이 있다");
    }

    @Test
    @DisplayName("마지막 페이지를 넘어가면 rows는 비고 totCnt는 그대로다")
    void beyondLastPage() {
        PageResponse<ExecLogResponse> page = list(99, SIZE, List.of(TrgrTyp.MANUAL, TrgrTyp.AUTO, TrgrTyp.PREVIEW));

        assertTrue(page.rows().isEmpty());
        assertEquals(ROWS, page.totCnt());
    }

    @Test
    @DisplayName("trgrTyp 기본값(실행 기록만)이 totCnt에도 걸린다 — 미리보기가 건수에 섞이면 안 된다")
    void defaultTriggerFilterAppliesToCount() {
        PageResponse<ExecLogResponse> page = list(1, SIZE, null);

        // 짝수 인덱스 4건만 MANUAL
        assertEquals(4L, page.totCnt());
        assertEquals(SIZE, page.rows().size());
    }

    private PageResponse<ExecLogResponse> list(int page, int size, List<TrgrTyp> trgrTyps) {
        PageCond cond = new PageCond();
        cond.setPage(page);
        cond.setSize(size);
        return stgyExecLogService.list(StgyTyp.INSP, stgyId, trgrTyps, cond);
    }
}
