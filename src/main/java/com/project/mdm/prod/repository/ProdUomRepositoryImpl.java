package com.project.mdm.prod.repository;

import com.project.mdm.prod.dto.ProdUomSearchCond;
import com.project.mdm.prod.entity.ProdUom;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.mdm.prod.entity.QProd.prod;
import static com.project.mdm.prod.entity.QProdUom.prodUom;

@RequiredArgsConstructor
public class ProdUomRepositoryImpl implements ProdUomRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 상품을 fetch join 한다 — 응답이 상품코드·상품명과 입고/출고단위 여부를 모두 읽으므로
     * 없으면 행 수만큼 상품 조회가 더 나간다 (ProdUom.prod 는 LAZY).
     */
    @Override
    public List<ProdUom> search(ProdUomSearchCond cond) {
        return queryFactory
                .selectFrom(prodUom)
                .join(prodUom.prod, prod).fetchJoin()
                .where(
                        prodCdContains(cond.getProdCd()),
                        prodNmContains(cond.getProdNm()),
                        uomCdEq(cond.getUomCd())
                )
                .orderBy(prod.prodCd.asc(), prodUom.eaQty.asc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression prodCdContains(String prodCd) {
        return StringUtils.hasText(prodCd) ? prod.prodCd.containsIgnoreCase(prodCd) : null;
    }

    private BooleanExpression prodNmContains(String prodNm) {
        return StringUtils.hasText(prodNm) ? prod.prodNm.containsIgnoreCase(prodNm) : null;
    }

    /** 단위는 콤보박스로 고르므로 부분일치가 아니라 정확일치다 */
    private BooleanExpression uomCdEq(String uomCd) {
        return StringUtils.hasText(uomCd) ? prodUom.uomCd.eq(uomCd) : null;
    }
}
