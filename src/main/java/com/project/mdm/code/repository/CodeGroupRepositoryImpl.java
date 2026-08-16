package com.project.mdm.code.repository;

import com.project.mdm.code.dto.CodeGroupSearchCond;
import com.project.mdm.code.entity.CodeGroup;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.mdm.code.entity.QCodeGroup.codeGroup;

@RequiredArgsConstructor
public class CodeGroupRepositoryImpl implements CodeGroupRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<CodeGroup> search(CodeGroupSearchCond cond) {
        return queryFactory
                .selectFrom(codeGroup)
                .where(
                        grpCdContains(cond.getGrpCd()),
                        grpNmContains(cond.getGrpNm())
                )
                .orderBy(codeGroup.grpCd.asc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression grpCdContains(String grpCd) {
        return StringUtils.hasText(grpCd) ? codeGroup.grpCd.containsIgnoreCase(grpCd) : null;
    }

    private BooleanExpression grpNmContains(String grpNm) {
        return StringUtils.hasText(grpNm) ? codeGroup.grpNm.containsIgnoreCase(grpNm) : null;
    }
}
