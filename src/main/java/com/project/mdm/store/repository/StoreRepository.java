package com.project.mdm.store.repository;

import com.project.mdm.store.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreRepository extends JpaRepository<Store, Long> {

    /** 납품처 선택 팝업용 전체 목록 (점포코드 순) */
    List<Store> findAllByOrderByStoreCdAsc();
}
