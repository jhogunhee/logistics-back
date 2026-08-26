package com.project.mdm.usr.repository;

import com.project.mdm.usr.dto.UsrSearchCond;
import com.project.mdm.usr.entity.Usr;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.mdm.usr.entity.QUsr.usr;

@RequiredArgsConstructor
public class UsrRepositoryImpl implements UsrRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Usr> search(UsrSearchCond cond) {
        return queryFactory
                .selectFrom(usr)
                .where(keywordContains(cond.getKeyword()))
                .orderBy(usr.loginId.asc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) return null;
        return usr.loginId.containsIgnoreCase(keyword)
                .or(usr.usrNm.containsIgnoreCase(keyword));
    }
}
