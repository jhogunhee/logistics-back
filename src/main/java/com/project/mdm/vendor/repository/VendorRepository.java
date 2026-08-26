package com.project.mdm.vendor.repository;

import com.project.mdm.vendor.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VendorRepository extends JpaRepository<Vendor, Long>, VendorRepositoryCustom {

    /** 그리드 행이 벤더를 코드로 보내는 화면(상품 거래처 마스터)이 쓴다 */
    Optional<Vendor> findByVndrCd(String vndrCd);
}
