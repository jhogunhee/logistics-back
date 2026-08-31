package com.project.wmsback.warehouse.repository;

import com.project.wmsback.warehouse.entity.BizDvsn;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Zon;
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
    boolean existsByZon(Zon zon);

    /** 존 온도대 수정 가드 — 하위 보관 로케이션이 있으면 온도대 일치 불변식이 깨지므로 바꿀 수 없다 */
    boolean existsByZonAndLocTyp(Zon zon, LocTyp locTyp);

    Optional<Loc> findByLocCd(String locCd);

    /** 수시보충 도착지 2순위 — 같은 상품이 이미 있는 피킹존 보관 로케이션 */
    @Query("select distinct i.loc from Inv i join i.loc l join l.zon z"
            + " where i.prod.id = :prodId and i.onHandQty > 0"
            + " and l.locTyp = :storage and z.bizDvsn = :pikng")
    List<Loc> findPikngLocsHoldingProd(@Param("prodId") Long prodId,
                                       @Param("storage") LocTyp storage, @Param("pikng") BizDvsn pikng);

    /** 수시보충 도착지 3순위 — 재고가 없고 어느 상품의 고정 로케이션도 아닌 피킹존 보관 로케이션 (온도대 일치) */
    @Query("select l from Loc l join l.zon z"
            + " where l.locTyp = :storage and z.bizDvsn = :pikng"
            + " and l.tmpZon = :tmpZon"
            + " and not exists (select 1 from Inv i where i.loc = l and i.onHandQty > 0)"
            + " and not exists (select 1 from FxngLoc f where f.loc = l)"
            + " order by l.pikngPrty asc, l.locCd asc")
    List<Loc> findEmptyPikngLocs(@Param("tmpZon") TmpZon tmpZon,
                                 @Param("storage") LocTyp storage, @Param("pikng") BizDvsn pikng);

    /** 반품 검수의 불량 도착지 후보 — 상품 온도대와 같은 반품존의 보관 로케이션 (적치 우선순위 순) */
    @Query("select l from Loc l join l.zon z"
            + " where l.locTyp = :storage and z.bizDvsn = :rtngs and l.tmpZon = :tmpZon"
            + " order by l.ptawyPrty asc, l.locCd asc")
    List<Loc> findRtngsLocs(@Param("tmpZon") TmpZon tmpZon,
                            @Param("storage") LocTyp storage, @Param("rtngs") BizDvsn rtngs);

    /**
     * 적치 대상 로케이션 후보 (상품 온도대와 일치하는 STORAGE, 적치 우선순위 오름차순 추천).
     * <p>
     * <b>존을 함께 읽는다</b> — 부르는 쪽이 후보마다 존을 본다(반품존 제외 판정 · 응답의 zonCd).
     * {@code Loc.zon}이 LAZY라 fetch join이 없으면 후보 수만큼 쿼리가 더 나가고, 원격 DB에서는
     * 자리 60개짜리 존 하나가 십수 초가 된다.
     */
    @Query("select l from Loc l join fetch l.zon"
            + " where l.tmpZon = :tmpZon and l.locTyp = :locTyp"
            + " order by l.ptawyPrty asc, l.locCd asc")
    List<Loc> findAllByTmpZonAndLocTypOrderByPtawyPrtyAsc(@Param("tmpZon") TmpZon tmpZon,
                                                          @Param("locTyp") LocTyp locTyp);
}
