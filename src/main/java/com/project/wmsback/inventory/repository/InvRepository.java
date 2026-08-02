package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.entity.Inv;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InvRepository extends JpaRepository<Inv, Long>, InvRepositoryCustom {

    /** 재고 키(상품+Loc+Lot)로 스냅샷 조회 (uq_inv) */
    Optional<Inv> findByProdIdAndLocIdAndLotId(Long prodId, Long locId, Long lotId);

    /** 예약(이동지시 등록)이 aloc를 올리기 전에 거는 비관적 락 — 할당·이동 예약 증감의 직렬화 지점 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inv i where i.id = :id")
    Optional<Inv> findByIdForUpdate(@Param("id") Long id);

    /** 이동 확정/취소가 예약·현품을 함께 갱신하기 전에 거는 비관적 락 (재고 키 기준) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inv i where i.prod.id = :prodId and i.loc.id = :locId and i.lot.id = :lotId")
    Optional<Inv> findByKeyForUpdate(@Param("prodId") Long prodId, @Param("locId") Long locId, @Param("lotId") Long lotId);

    /** 로케이션의 현재고 총량 — 적재가능수량(max_qty − 현재고 − 미완료 지시 유입 잔량) 계산용 */
    @Query("select coalesce(sum(i.onHandQty), 0) from Inv i where i.loc.id = :locId")
    long sumOnHandQtyByLocId(@Param("locId") Long locId);
}
