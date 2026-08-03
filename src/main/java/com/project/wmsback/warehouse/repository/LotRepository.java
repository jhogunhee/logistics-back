package com.project.wmsback.warehouse.repository;

import com.project.wmsback.warehouse.entity.Lot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LotRepository extends JpaRepository<Lot, Long> {

    /** 같은 배치(상품+입고일자+제조일자) 재사용 조회. 증분 검수 시 Lot 중복 생성 방지 */
    Optional<Lot> findByProdIdAndReceiptDtAndMfgDt(Long prodId, LocalDate receiptDt, LocalDate mfgDt);

    /**
     * 속성 정정이 Lot 행을 갱신하기 전에 거는 비관적 락.
     * 정정은 이 락 앞에 상품 로우 락을 먼저 잡는다 — 검수(findOrCreateLot)와 같은 순서(상품 → Lot)라
     * 교착이 없고, 배치 재사용 키(상품+입고일자+제조일자) 판정이 두 업무 사이에서 직렬화된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lot l where l.id = :id")
    Optional<Lot> findByIdForUpdate(@Param("id") Long id);

    /** 상품+입고일자 기준 다음 채번 번호 계산용 (일자 리셋) */
    long countByProdIdAndReceiptDt(Long prodId, LocalDate receiptDt);

    /** 상품별 Lot 목록 (유통기한 빠른 순 — 미관리 Lot은 뒤로). 재고조사 라인 수동 추가의 Lot 선택용 */
    List<Lot> findByProdIdOrderByExpiryDtAscLotNoAsc(Long prodId);
}
