package com.project.mdm.code.repository;

import com.project.mdm.code.entity.CodeGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodeGroupRepository extends JpaRepository<CodeGroup, String> {

    List<CodeGroup> findAllByOrderByGrpCd();
}
