package com.project.mdm.prod.repository;

import com.project.mdm.prod.dto.ProdVndrSearchCond;
import com.project.mdm.prod.entity.ProdVndr;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.mdm.prod.entity.QProd.prod;
import static com.project.mdm.prod.entity.QProdVndr.prodVndr;
import static com.project.mdm.vendor.entity.QVendor.vendor;

@RequiredArgsConstructor
public class ProdVndrRepositoryImpl implements ProdVndrRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ProdVndr> search(ProdVndrSearchCond cond) {
        // 응답이 상품 코드·명·입고단위와 벤더 코드·명을 싣는다 — 행마다 지연 로딩하지 않게 함께 가져온다.
        // 정렬 (상품, prty, id)는 산정의 대표 벤더 선택이 기대는 계약이다 (ProdVndrRepositoryCustom 참고)
        return queryFactory
                .selectFrom(prodVndr)
                .join(prodVndr.prod, prod).fetchJoin()
                .join(prodVndr.vendor, vendor).fetchJoin()
                .where(
                        prodCdContains(cond.getProdCd()),
                        prodNmContains(cond.getProdNm()),
                        vndrCdEq(cond.getVndrCd())
                )
                .orderBy(prod.prodCd.asc(), prodVndr.prty.asc(), prodVndr.id.asc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression prodCdContains(String prodCd) {
        return StringUtils.hasText(prodCd) ? prod.prodCd.containsIgnoreCase(prodCd) : null;
    }

    private BooleanExpression prodNmContains(String prodNm) {
        return StringUtils.hasText(prodNm) ? prod.prodNm.containsIgnoreCase(prodNm) : null;
    }

    private BooleanExpression vndrCdEq(String vndrCd) {
        return StringUtils.hasText(vndrCd) ? vendor.vndrCd.eq(vndrCd) : null;
    }
}
