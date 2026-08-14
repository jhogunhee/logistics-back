package com.project.mdm.store.repository;

import com.project.mdm.store.dto.StoreSearchCond;
import com.project.mdm.store.entity.Store;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.mdm.store.entity.QStore.store;

@RequiredArgsConstructor
public class StoreRepositoryImpl implements StoreRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Store> search(StoreSearchCond cond) {
        return queryFactory
                .selectFrom(store)
                .where(
                        storeCdContains(cond.getStoreCd()),
                        storeNmContains(cond.getStoreNm()),
                        storeGrpEq(cond.getStoreGrp()),
                        storeTypEq(cond.getStoreTyp())
                )
                // id순이 아니라 점포코드순 — 납품처 선택 팝업이 빈 조건으로 이 쿼리를 타는데,
                // 그 목록은 처음부터 점포코드순을 전제로 만들어졌다
                .orderBy(store.storeCd.asc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression storeCdContains(String storeCd) {
        return StringUtils.hasText(storeCd) ? store.storeCd.containsIgnoreCase(storeCd) : null;
    }

    private BooleanExpression storeNmContains(String storeNm) {
        return StringUtils.hasText(storeNm) ? store.storeNm.containsIgnoreCase(storeNm) : null;
    }

    // 그룹·유형은 코드값이라 부분일치가 아니라 등가 비교다 (화면이 콤보로 보낸다)

    private BooleanExpression storeGrpEq(String storeGrp) {
        return StringUtils.hasText(storeGrp) ? store.storeGrp.eq(storeGrp) : null;
    }

    private BooleanExpression storeTypEq(String storeTyp) {
        return StringUtils.hasText(storeTyp) ? store.storeTyp.eq(storeTyp) : null;
    }

}
