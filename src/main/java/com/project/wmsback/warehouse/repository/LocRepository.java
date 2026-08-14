package com.project.wmsback.warehouse.repository;

import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.mdm.prod.entity.TmpZon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocRepository extends JpaRepository<Loc, Long>, LocRepositoryCustom {

    /**
     * 이동지시 등록이 적재가능수량을 검증하기 전에 도착 로케이션에 거는 비관적 락 —
     * 같은 도착지로 향하는 동시 등록의 직렬화 지점. 검증이 락 없는 집계(현재고 합 + 미완료 유입 잔량)
     * 읽기라, 잠그지 않으면 두 등록이 서로의 유입을 못 보고 둘 다 통과해 max_qty를 넘긴다.
     * 다건은 로케이션 id 오름차순으로 잡고, 재고 행보다 먼저 잠근다 (docs/design.md 「락 순서」).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loc l where l.id = :id")
    Optional<Loc> findByIdForUpdate(@Param("id") Long id);

    boolean existsByLocCd(String locCd);

    /** 존 삭제 가드 — 하위 로케이션이 하나라도 있으면 그 존은 지울 수 없다 (FK가 없어 DB가 막지 않는다) */
    boolean existsByZonCd(String zonCd);

    Optional<Loc> findByLocCd(String locCd);

    /** 적치 대상 로케이션 후보 (상품 온도대와 일치하는 STORAGE, 적치 우선순위 오름차순 추천) */
    List<Loc> findAllByTmpZonAndLocTypOrderByPtawyPrtyAsc(TmpZon tmpZon, LocTyp locTyp);
}
