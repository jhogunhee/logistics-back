package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.CodeGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodeGroupRepository extends JpaRepository<CodeGroup, String> {

    List<CodeGroup> findAllByOrderByGrpCd();
}
