package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.CodeDetail;
import com.project.wmsback.master.entity.CodeDetailId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodeDetailRepository extends JpaRepository<CodeDetail, CodeDetailId> {

    List<CodeDetail> findByGroupCdAndUseYnOrderBySortOrd(String groupCd, char useYn);
}