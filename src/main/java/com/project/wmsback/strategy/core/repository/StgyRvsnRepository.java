package com.project.wmsback.strategy.core.repository;

import com.project.wmsback.strategy.core.entity.StgyRvsn;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StgyRvsnRepository extends JpaRepository<StgyRvsn, Long> {

    List<StgyRvsn> findAllByStgyTypAndStgyIdOrderByRvsnNoDesc(StgyTyp stgyTyp, Long stgyId);

    Optional<StgyRvsn> findByStgyTypAndStgyIdAndRvsnNo(StgyTyp stgyTyp, Long stgyId, Long rvsnNo);

    /**
     * 유형별로 전략마다 최신 리비전 1행 (stgy_id 내림차순 = 최근 생성 전략 먼저).
     * 원본 전략이 삭제돼도 남는 느슨한 참조라, "삭제된 전략의 이력/복원" 진입점이 된다 (D4).
     */
    @Query("""
            select r from StgyRvsn r
            where r.stgyTyp = :stgyTyp
              and r.rvsnNo = (select max(r2.rvsnNo) from StgyRvsn r2
                              where r2.stgyTyp = r.stgyTyp and r2.stgyId = r.stgyId)
            order by r.stgyId desc
            """)
    List<StgyRvsn> findLatestPerStrategy(@Param("stgyTyp") StgyTyp stgyTyp);
}
