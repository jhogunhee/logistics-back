package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.Lot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface LotRepository extends JpaRepository<Lot, Long> {

    /** 같은 배치(SKU+입고일자+제조일자) 재사용 조회. 증분 검수 시 Lot 중복 생성 방지 */
    Optional<Lot> findBySkuIdAndReceiptDtAndMfgDt(Long skuId, LocalDate receiptDt, LocalDate mfgDt);

    /** SKU+입고일자 기준 다음 채번 번호 계산용 (일자 리셋) */
    long countBySkuIdAndReceiptDt(Long skuId, LocalDate receiptDt);
}
