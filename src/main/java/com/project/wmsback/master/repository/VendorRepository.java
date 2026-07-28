package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VendorRepository extends JpaRepository<Vendor, Long>, VendorRepositoryCustom {

    /**
     * 벤더 코드 채번값 발급. 시퀀스는 DB가 원자적으로 증가시키므로 동시 등록에도 중복이 없다.
     * QueryDSL은 JPA 엔티티 기반이라 시퀀스 NEXTVAL 같은 스칼라 조회는 표현할 대상이 없어 네이티브로 남긴다.
     */
    @Query(value = "SELECT nextval('vndr_cd_seq')", nativeQuery = true)
    Long nextVndrCdSeq();
}
