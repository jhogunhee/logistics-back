package com.project.wmsback.strategy.core.repository;

import com.project.wmsback.strategy.core.entity.StgyExecLog;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import com.project.wmsback.strategy.core.entity.TrgrTyp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StgyExecLogRepository extends JpaRepository<StgyExecLog, Long> {

    /**
     * 전략별 최근 실행 로그. 상한을 두는 이유 — dcsn_trc가 큰 JSON이라 무제한 조회를 막는다.
     * 트리거로 먼저 거르는 이유도 상한 때문이다 — 미리보기 기록이 섞이면 100건을 그쪽이
     * 다 차지해 정작 실행 이력이 화면에서 밀려난다.
     */
    List<StgyExecLog> findTop100ByStgyTypAndStgyIdAndTrgrTypInOrderByCreatedAtDesc(
            StgyTyp stgyTyp, Long stgyId, Collection<TrgrTyp> trgrTyps);

    List<StgyExecLog> findTop100ByStgyTypAndTrgrTypInOrderByCreatedAtDesc(
            StgyTyp stgyTyp, Collection<TrgrTyp> trgrTyps);
}
