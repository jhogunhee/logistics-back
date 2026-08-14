package com.project.mdm.prod.repository;

import com.project.mdm.prod.dto.ProdSearchCond;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.TmpZon;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

import static com.project.mdm.prod.entity.QProd.prod;
import static com.project.mdm.prod.entity.QProdUom.prodUom;

@RequiredArgsConstructor
public class ProdRepositoryImpl implements ProdRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Prod> findByIdForUpdate(Long id) {
        return Optional.ofNullable(
                queryFactory
                        .selectFrom(prod)
                        .where(prod.id.eq(id))
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                        .fetchOne());
    }

    /**
     * 목록 조회. 포장을 fetch join으로 함께 읽는다 — ProdResponse가 환산계수(inbEaQty·outbEaQty)를
     * 뽑으려고 uoms를 훑으므로 없으면 상품 수만큼 추가 쿼리가 나간다. 조인으로 행이 부풀므로 distinct가 필요하다.
     */
    @Override
    public List<Prod> search(ProdSearchCond cond) {
        return queryFactory
                .selectFrom(prod).distinct()
                .leftJoin(prod.uoms, prodUom).fetchJoin()
                .where(
                        prodCdContains(cond.getProdCd()),
                        prodNmContains(cond.getProdNm()),
                        tmpZonEq(cond.getTmpZon())
                )
                .orderBy(prod.prodCd.asc())
                .fetch();
    }

    // 상품 삭제 가드(누가 이 상품을 참조하는가)는 여기 없다 — mdm은 자기 데이터를 누가 쓰는지
    // 몰라야 하므로 ProdRefChecker 포트로 뒤집었고, 검사는 각 앱이 구현한다.

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression prodCdContains(String prodCd) {
        return StringUtils.hasText(prodCd) ? prod.prodCd.containsIgnoreCase(prodCd) : null;
    }

    private BooleanExpression prodNmContains(String prodNm) {
        return StringUtils.hasText(prodNm) ? prod.prodNm.containsIgnoreCase(prodNm) : null;
    }

    private BooleanExpression tmpZonEq(TmpZon tmpZon) {
        return tmpZon != null ? prod.tmpZon.eq(tmpZon) : null;
    }
}