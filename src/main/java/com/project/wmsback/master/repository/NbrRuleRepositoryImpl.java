package com.project.wmsback.master.repository;

import com.project.wmsback.master.dto.NbrRuleSearchCond;
import com.project.wmsback.master.entity.NbrRule;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.wmsback.master.entity.QNbrRule.nbrRule;

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

    private BooleanExpression ruleCdContains(String ruleCd) {
        return StringUtils.hasText(ruleCd) ? nbrRule.ruleCd.containsIgnoreCase(ruleCd) : null;
    }

    private BooleanExpression ruleNmContains(String ruleNm) {
        return StringUtils.hasText(ruleNm) ? nbrRule.ruleNm.containsIgnoreCase(ruleNm) : null;
    }
}
