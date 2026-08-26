package com.project.mdm.usr.repository;

import com.project.mdm.usr.dto.UsrSearchCond;
import com.project.mdm.usr.entity.Usr;

import java.util.List;

public interface UsrRepositoryCustom {

    List<Usr> search(UsrSearchCond cond);
}
