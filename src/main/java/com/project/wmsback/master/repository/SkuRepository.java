package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.Sku;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SkuRepository extends JpaRepository<Sku, Long>, SkuRepositoryCustom {

    /**
     * SKU 코드 채번값 발급. 시퀀스는 DB가 원자적으로 증가시키므로
     * 동시에 INSERT가 몰려도 중복 없이 발급된다 (MAX+1 조회 방식의 레이스 컨디션 회피).
     * QueryDSL은 JPA 엔티티 기반 쿼리 빌더라 "시퀀스.NEXTVAL"처럼 테이블/엔티티가 없는
     * 스칼라 조회는 표현할 대상이 없다 — 네이티브 쿼리로 남긴다.
     */
    @Query(value = "SELECT nextval('sku_cd_seq')", nativeQuery = true)
    Long nextSkuCdSeq();
}