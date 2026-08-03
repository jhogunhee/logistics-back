package com.project.mdm.prod.repository;

import com.project.mdm.prod.entity.Prod;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdRepository extends JpaRepository<Prod, Long>, ProdRepositoryCustom {

    /** 단위 삭제 가드용. FK가 없어 DB가 막아주지 않으므로 CodeService가 이걸로 직접 확인한다 */
    boolean existsByInbUomCdOrOutbUomCd(String inbUomCd, String outbUomCd);

    /** 단위 삭제 가드용 — 포장으로만 등록된 단위도 있어서 위 메서드만으로는 부족하다 */
    boolean existsByUomsUomCd(String uomCd);

    // 상품 코드 채번(nextProdCdSeq)은 여기서 사라졌다 — prod_cd_seq 시퀀스가 nbr 모듈로
    // 대체되면서 ProdService가 nbrService.issue("PROD_CD")를 쓴다.
}
