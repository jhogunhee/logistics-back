package com.project.mdm.nbr.repository;

import com.project.mdm.nbr.dto.NbrRuleSearchCond;
import com.project.mdm.nbr.entity.NbrRule;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.mdm.nbr.entity.QNbrRule.nbrRule;

@RequiredArgsConstructor
public class NbrRuleRepositoryImpl implements NbrRuleRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<NbrRule> search(NbrRuleSearchCond cond) {
        return queryFactory
                .selectFrom(nbrRule)
                .where(
                        ruleCdContains(cond.getRuleCd()),
                        ruleNmContains(cond.getRuleNm())
                )
                .orderBy(nbrRule.ruleCd.asc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression ruleCdContains(String ruleCd) {
        return StringUtils.hasText(ruleCd) ? nbrRule.ruleCd.containsIgnoreCase(ruleCd) : null;
    }

    private BooleanExpression ruleNmContains(String ruleNm) {
        return StringUtils.hasText(ruleNm) ? nbrRule.ruleNm.containsIgnoreCase(ruleNm) : null;
    }
}
