package com.project.mdm.prod.repository;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.ProdVndr;
import com.project.mdm.vendor.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;

public interface ProdVndrRepository extends JpaRepository<ProdVndr, Long>, ProdVndrRepositoryCustom {

    /** 짝 중복 가드 — uq_prod_vndr(상품 하나에 같은 벤더 두 번 금지)를 커밋 전에 사용자 메시지로 돌려준다 */
    Optional<ProdVndr> findByProdAndVendor(Prod prod, Vendor vendor);

    /** 상품 삭제 가드 (ProdVndrRefChecker) */
    boolean existsByProdId(Long prodId);

    /** 벤더 삭제 가드 (ProdVndrRefChecker) */
    boolean existsByVendorId(Long vendorId);

    /** 자동발주 발행 검증 — 이 벤더에 등재된 상품. 스칼라라 엔티티를 올리지 않는다 */
    @Query("select pv.prod.id from ProdVndr pv where pv.vendor.id = :vendorId")
    Set<Long> findProdIdsByVendorId(@Param("vendorId") Long vendorId);
}
