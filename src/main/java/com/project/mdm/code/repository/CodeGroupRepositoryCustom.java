package com.project.mdm.code.repository;

import com.project.mdm.code.dto.CodeGroupSearchCond;
import com.project.mdm.code.entity.CodeGroup;

import java.util.List;

public interface CodeGroupRepositoryCustom {

    /** 관리 화면용 그룹 검색 (그룹코드/그룹명 부분일치) */
    List<CodeGroup> search(CodeGroupSearchCond cond);
}
