package com.project.mdm.code.repository;

import com.project.mdm.code.dto.CodeSearchCond;
import com.project.mdm.code.entity.CodeDetail;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.mdm.code.entity.QCodeDetail.codeDetail;

@RequiredArgsConstructor
public class CodeDetailRepositoryImpl implements CodeDetailRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<CodeDetail> search(String grpCd, CodeSearchCond cond) {
        return queryFactory
                .selectFrom(codeDetail)
                .where(
                        codeDetail.grpCd.eq(grpCd),
                        codeCdContains(cond.getCodeCd()),
                        codeNmContains(cond.getCodeNm())
                )
                .orderBy(codeDetail.srtSeq.asc(), codeDetail.codeCd.asc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression codeCdContains(String codeCd) {
        return StringUtils.hasText(codeCd) ? codeDetail.codeCd.containsIgnoreCase(codeCd) : null;
    }

    private BooleanExpression codeNmContains(String codeNm) {
        return StringUtils.hasText(codeNm) ? codeDetail.codeNm.containsIgnoreCase(codeNm) : null;
    }
}
