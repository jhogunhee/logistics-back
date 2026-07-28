package com.project.wmsback.master.repository;

import com.project.wmsback.master.dto.VendorSearchCond;
import com.project.wmsback.master.entity.Vendor;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.wmsback.master.entity.QVendor.vendor;

@RequiredArgsConstructor
public class VendorRepositoryImpl implements VendorRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Vendor> search(VendorSearchCond cond) {
        return queryFactory
                .selectFrom(vendor)
                .where(
                        vndrCdContains(cond.getVndrCd()),
                        vndrNmContains(cond.getVndrNm()),
                        useYnEq(cond.getUseYn())
                )
                .orderBy(vendor.id.asc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression vndrCdContains(String vndrCd) {
        return StringUtils.hasText(vndrCd) ? vendor.vndrCd.containsIgnoreCase(vndrCd) : null;
    }

    private BooleanExpression vndrNmContains(String vndrNm) {
        return StringUtils.hasText(vndrNm) ? vendor.vndrNm.containsIgnoreCase(vndrNm) : null;
    }

    private BooleanExpression useYnEq(String useYn) {
        return StringUtils.hasText(useYn) ? vendor.useYn.eq(useYn) : null;
    }
}
