package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.CodeDetail;
import com.project.wmsback.master.entity.CodeDetailId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodeDetailRepository extends JpaRepository<CodeDetail, CodeDetailId>, CodeDetailRepositoryCustom {

    List<CodeDetail> findByGrpCdOrderBySrtSeq(String grpCd);

    /** 그룹 삭제 가드용 — 코드가 남아 있는 그룹은 지울 수 없다 */
    boolean existsByGrpCd(String grpCd);
}
