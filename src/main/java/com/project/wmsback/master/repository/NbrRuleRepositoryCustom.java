package com.project.wmsback.master.repository;

import com.project.wmsback.master.dto.NbrRuleSearchCond;
import com.project.wmsback.master.entity.NbrRule;

import java.util.List;

public interface NbrRuleRepositoryCustom {

    List<NbrRule> search(NbrRuleSearchCond cond);
}
