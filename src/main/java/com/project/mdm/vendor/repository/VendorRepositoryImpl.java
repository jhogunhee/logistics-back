package com.project.mdm.vendor.repository;

import com.project.mdm.vendor.dto.VendorSearchCond;
import com.project.mdm.vendor.entity.Vendor;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.mdm.vendor.entity.QVendor.vendor;

@RequiredArgsConstructor
public class VendorRepositoryImpl implements VendorRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Vendor> search(VendorSearchCond cond) {
        return queryFactory
                .selectFrom(vendor)
                .where(
                        vndrCdContains(cond.getVndrCd()),
                        vndrNmContains(cond.getVndrNm())
                )
                .orderBy(vendor.vndrCd.asc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression vndrCdContains(String vndrCd) {
        return StringUtils.hasText(vndrCd) ? vendor.vndrCd.containsIgnoreCase(vndrCd) : null;
    }

    private BooleanExpression vndrNmContains(String vndrNm) {
        return StringUtils.hasText(vndrNm) ? vendor.vndrNm.containsIgnoreCase(vndrNm) : null;
    }

}
