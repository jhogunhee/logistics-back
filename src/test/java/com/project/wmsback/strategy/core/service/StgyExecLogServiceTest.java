package com.project.wmsback.strategy.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.dto.PageCond;
import com.project.common.dto.PageResponse;
import com.project.wmsback.strategy.core.dto.ExecLogResponse;
import com.project.wmsback.strategy.core.entity.StgyExecLog;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import com.project.wmsback.strategy.core.entity.TrgrTyp;
import com.project.wmsback.strategy.core.repository.StgyExecLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 실행 로그 조회의 페이징 — 화면의 1부터인 page를 0부터인 Pageable로 옮기는 곳이라 어긋나기 쉽다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StgyExecLogServiceTest {

    @Mock StgyExecLogRepository stgyExecLogRepository;
    @Mock PlatformTransactionManager transactionManager;

    private StgyExecLogService service;

    @BeforeEach
    void setUp() {
        service = new StgyExecLogService(stgyExecLogRepository, new ObjectMapper(), transactionManager);
    }

    private StgyExecLog log(TrgrTyp trgrTyp) {
        return StgyExecLog.builder()
                .stgyTyp(StgyTyp.INSP)
                .stgyId(1L)
                .rvsnNo(1L)
                .trgrTyp(trgrTyp)
                .tgtRef("IB-20260826-0001")
                .rsltSmry("라인 3건 중 위반 1건")
                .build();
    }

    private PageCond pageCond(int page, int size) {
        PageCond cond = new PageCond();
        cond.setPage(page);
        cond.setSize(size);
        return cond;
    }

    @Test
    @DisplayName("화면의 3페이지는 Pageable 2페이지 — 1부터와 0부터를 여기서 맞춘다")
    void convertsOneBasedPageToZeroBased() {
        when(stgyExecLogRepository.findByStgyTypAndTrgrTypIn(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(StgyTyp.INSP, null, null, pageCond(3, 30));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(stgyExecLogRepository).findByStgyTypAndTrgrTypIn(any(), any(), captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(30, captor.getValue().getPageSize());
    }

    @Test
    @DisplayName("정렬은 createdAt 다음에 id — 같은 시각 행이 페이지 경계에서 겹치거나 빠지지 않게")
    void sortsByCreatedAtThenId() {
        when(stgyExecLogRepository.findByStgyTypAndTrgrTypIn(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(StgyTyp.INSP, null, null, pageCond(1, 30));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(stgyExecLogRepository).findByStgyTypAndTrgrTypIn(any(), any(), captor.capture());
        Sort sort = captor.getValue().getSort();
        assertEquals(List.of(Sort.Order.desc("createdAt"), Sort.Order.desc("id")), sort.toList());
    }

    @Test
    @DisplayName("totCnt는 전체 건수 — 한 페이지만 받아도 화면이 페이지 수를 셀 수 있다")
    void totCntIsTotalNotPageSize() {
        when(stgyExecLogRepository.findByStgyTypAndTrgrTypIn(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(log(TrgrTyp.MANUAL)),
                        org.springframework.data.domain.PageRequest.of(0, 1), 250));

        PageResponse<ExecLogResponse> response = service.list(StgyTyp.INSP, null, null, pageCond(1, 1));

        assertEquals(1, response.rows().size());
        assertEquals(250L, response.totCnt());
        assertEquals(1, response.page());
        assertEquals(1, response.size());
        assertNotNull(response.rows().get(0).tgtRef());
    }

    @Test
    @DisplayName("trgrTyp를 주지 않으면 실행 기록만(MANUAL·AUTO) — 페이징을 붙여도 이 기본은 그대로다")
    void defaultsToExecutedTriggersOnly() {
        when(stgyExecLogRepository.findByStgyTypAndTrgrTypIn(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(StgyTyp.INSP, null, null, pageCond(1, 30));

        ArgumentCaptor<Collection<TrgrTyp>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(stgyExecLogRepository).findByStgyTypAndTrgrTypIn(eq(StgyTyp.INSP), captor.capture(), any());
        assertEquals(2, captor.getValue().size());
        assertEquals(true, captor.getValue().containsAll(List.of(TrgrTyp.MANUAL, TrgrTyp.AUTO)));
    }

    @Test
    @DisplayName("stgyId를 주면 전략별 조회로 간다")
    void usesStgyIdQueryWhenGiven() {
        when(stgyExecLogRepository.findByStgyTypAndStgyIdAndTrgrTypIn(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(StgyTyp.PTAWY, 7L, List.of(TrgrTyp.PREVIEW), pageCond(1, 30));

        verify(stgyExecLogRepository).findByStgyTypAndStgyIdAndTrgrTypIn(eq(StgyTyp.PTAWY), eq(7L), any(), any());
    }
}
