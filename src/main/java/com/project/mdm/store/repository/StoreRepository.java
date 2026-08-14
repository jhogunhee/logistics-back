package com.project.mdm.store.repository;

import com.project.mdm.store.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long>, StoreRepositoryCustom {

    /** 공통코드 STORE_GRP 삭제 가드 — 이 그룹을 쓰는 점포가 있으면 코드를 지울 수 없다 */
    boolean existsByStoreGrp(String storeGrp);

    /** 공통코드 STORE_TYP 삭제 가드 — 이 유형을 쓰는 점포가 있으면 코드를 지울 수 없다 */
    boolean existsByStoreTyp(String storeTyp);
}
