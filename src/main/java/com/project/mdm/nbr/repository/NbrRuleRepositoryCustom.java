package com.project.mdm.nbr.repository;

import com.project.mdm.nbr.dto.NbrRuleSearchCond;
import com.project.mdm.nbr.entity.NbrRule;

import java.util.List;

public interface NbrRuleRepositoryCustom {

    List<NbrRule> search(NbrRuleSearchCond cond);
}
