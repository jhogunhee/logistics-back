package com.project.wmsback.strategy.core.repository;

import com.project.wmsback.strategy.core.entity.StgyExecLog;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StgyExecLogRepository extends JpaRepository<StgyExecLog, Long> {

    /** 전략별 최근 실행 로그. 상한을 두는 이유 — dcsn_trc가 큰 JSON이라 무제한 조회를 막는다 */
    List<StgyExecLog> findTop100ByStgyTypAndStgyIdOrderByCreatedAtDesc(StgyTyp stgyTyp, Long stgyId);

    List<StgyExecLog> findTop100ByStgyTypOrderByCreatedAtDesc(StgyTyp stgyTyp);
}
