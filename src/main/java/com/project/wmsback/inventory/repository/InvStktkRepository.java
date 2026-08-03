package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.entity.InvStktk;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InvStktkRepository extends JpaRepository<InvStktk, Long>, InvStktkRepositoryCustom {

    /**
     * 확정·취소가 상태를 넘기기 전에 거는 비관적 락 — 같은 조사를 두 번 확정해 조정이 두 번 반영되는 것을 막는다.
     * (재고 행 락은 라인별로 따로 건다 — 락 순서는 재고 키 오름차순으로 고정)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InvStktk s where s.id = :id")
    Optional<InvStktk> findByIdForUpdate(@Param("id") Long id);
}
