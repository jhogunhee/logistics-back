package com.project.wmsback.strategy.core.repository;

import com.project.wmsback.strategy.core.entity.StgyExecLog;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import com.project.wmsback.strategy.core.entity.TrgrTyp;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface StgyExecLogRepository extends JpaRepository<StgyExecLog, Long> {

    /**
     * 전략별 실행 로그 한 페이지. 예전의 100건 상한을 페이지 크기가 대신한다 —
     * dcsn_trc가 큰 JSON이라 무제한 조회는 여전히 막아야 하고, 상한을 넘는 이력은
     * 잘려나가는 대신 다음 페이지로 넘어간다.
     */
    Page<StgyExecLog> findByStgyTypAndStgyIdAndTrgrTypIn(
            StgyTyp stgyTyp, Long stgyId, Collection<TrgrTyp> trgrTyps, Pageable pageable);

    Page<StgyExecLog> findByStgyTypAndTrgrTypIn(
            StgyTyp stgyTyp, Collection<TrgrTyp> trgrTyps, Pageable pageable);
}
