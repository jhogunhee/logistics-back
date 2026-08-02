package com.project.wmsback.strategy.core.repository;

import com.project.wmsback.strategy.core.entity.StgyRvsn;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StgyRvsnRepository extends JpaRepository<StgyRvsn, Long> {

    List<StgyRvsn> findAllByStgyTypAndStgyIdOrderByRvsnNoDesc(StgyTyp stgyTyp, Long stgyId);

    Optional<StgyRvsn> findByStgyTypAndStgyIdAndRvsnNo(StgyTyp stgyTyp, Long stgyId, Long rvsnNo);
}
