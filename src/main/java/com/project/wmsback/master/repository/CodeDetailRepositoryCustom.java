package com.project.wmsback.master.repository;

import com.project.wmsback.master.dto.CodeSearchCond;
import com.project.wmsback.master.entity.CodeDetail;

import java.util.List;

public interface CodeDetailRepositoryCustom {

    /** 관리 화면용 그룹 내 검색 (코드/코드명 부분일치) */
    List<CodeDetail> search(String grpCd, CodeSearchCond cond);
}
