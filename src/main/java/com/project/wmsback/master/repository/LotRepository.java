package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.Lot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface LotRepository extends JpaRepository<Lot, Long> {

    /** 같은 배치(상품+입고일자+제조일자) 재사용 조회. 증분 검수 시 Lot 중복 생성 방지 */
    Optional<Lot> findByProdIdAndReceiptDtAndMfgDt(Long prodId, LocalDate receiptDt, LocalDate mfgDt);

    /** 상품+입고일자 기준 다음 채번 번호 계산용 (일자 리셋) */
    long countByProdIdAndReceiptDt(Long prodId, LocalDate receiptDt);
}
