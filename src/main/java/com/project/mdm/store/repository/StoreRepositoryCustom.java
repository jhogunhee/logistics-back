package com.project.mdm.store.repository;

import com.project.mdm.store.dto.StoreSearchCond;
import com.project.mdm.store.entity.Store;

import java.util.List;

public interface StoreRepositoryCustom {

    List<Store> search(StoreSearchCond cond);
}
