package com.project.wmsback.outbound.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * 피킹지시의 상태 기계. 시블링(putaway_task·inv_mov_task)과 같은 DIRECTED → DONE / CANCELLED 이되,
 * 취소는 웨이브 단위 지시취소만 부르고 실적이 있으면 막힌다 (실적 취소는 v1 미지원).
 */
class PikngTaskTest {

    private PikngTask task(long drctQty) {
        OutbWave wave = OutbWave.builder().wavNo("WV-20260820-001").build();
        return PikngTask.builder()
                .wave(wave)
                .outbAlloc(mock(OutbAlloc.class))
                .prod(mock(com.project.mdm.prod.entity.Prod.class))
                .fromLoc(mock(com.project.wmsback.warehouse.entity.Loc.class))
                .lot(mock(com.project.wmsback.warehouse.entity.Lot.class))
                .drctQty(drctQty)
                .srtSeq(1)
                .build();
    }

    @Test
    @DisplayName("부분 실행은 DIRECTED 유지, 전량 도달 시 DONE + 완료시각")
    void executeAccumulatesAndCompletes() {
        PikngTask task = task(30);

        task.execute(10);
        assertEquals(PikngTaskStatus.DIRECTED, task.getStatus());
        assertEquals(20, task.remainingQty());
        assertNull(task.getCmplDt());

        task.execute(20);
        assertEquals(PikngTaskStatus.DONE, task.getStatus());
        assertEquals(0, task.remainingQty());
        assertNotNull(task.getCmplDt());
    }

    @Test
    @DisplayName("실적이 없는 지시만 취소된다 — 행은 남는다 (CANCELLED 전이)")
    void cancelOnlyWithoutResult() {
        PikngTask clean = task(30);
        clean.cancel();
        assertEquals(PikngTaskStatus.CANCELLED, clean.getStatus());

        PikngTask picked = task(30);
        picked.execute(10);
        assertThrows(IllegalStateException.class, picked::cancel);
    }

    @Test
    @DisplayName("완료·취소된 지시는 다시 취소할 수 없다")
    void cancelRejectsNonDirected() {
        PikngTask done = task(10);
        done.execute(10);
        assertThrows(IllegalStateException.class, done::cancel);

        PikngTask cancelled = task(10);
        cancelled.cancel();
        assertThrows(IllegalStateException.class, cancelled::cancel);
    }
}
