package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.entity.InvHld;
import com.project.wmsback.inventory.entity.InvHldStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InvHldRepository extends JpaRepository<InvHld, Long>, InvHldRepositoryCustom {

    /** 동일 사유 미해제 중복 차단 — DB 최후 방어는 부분 UNIQUE 인덱스(uq_inv_hld_open_rsn) */
    boolean existsByProdIdAndLocIdAndLotIdAndRsnCdAndStatus(Long prodId, Long locId, Long lotId, String rsnCd, InvHldStatus status);

    /**
     * 해제가 잔량을 검증·차감하기 전에 거는 비관적 락 — 같은 건의 동시 해제 직렬화 지점.
     * 락 순서는 보류 건 → inv 행 (등록은 inv 행만 잡으므로 순서 역전이 없다)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from InvHld h where h.id = :id")
    Optional<InvHld> findByIdForUpdate(@Param("id") Long id);
}
