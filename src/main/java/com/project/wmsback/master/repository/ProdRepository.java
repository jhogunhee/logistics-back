package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.Prod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProdRepository extends JpaRepository<Prod, Long>, ProdRepositoryCustom {

    /** 단위 삭제 가드용. FK가 없어 DB가 막아주지 않으므로 CodeService가 이걸로 직접 확인한다 */
    boolean existsByInbUomCdOrOutbUomCd(String inbUomCd, String outbUomCd);

    /** 단위 삭제 가드용 — 포장으로만 등록된 단위도 있어서 위 메서드만으로는 부족하다 */
    boolean existsByUomsUomCd(String uomCd);

    /**
     * 상품 코드 채번값 발급. 시퀀스는 DB가 원자적으로 증가시키므로
     * 동시에 INSERT가 몰려도 중복 없이 발급된다 (MAX+1 조회 방식의 레이스 컨디션 회피).
     * QueryDSL은 JPA 엔티티 기반 쿼리 빌더라 "시퀀스.NEXTVAL"처럼 테이블/엔티티가 없는
     * 스칼라 조회는 표현할 대상이 없다 — 네이티브 쿼리로 남긴다.
     */
    @Query(value = "SELECT nextval('prod_cd_seq')", nativeQuery = true)
    Long nextProdCdSeq();
}