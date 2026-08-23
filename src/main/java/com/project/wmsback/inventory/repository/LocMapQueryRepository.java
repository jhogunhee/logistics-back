package com.project.wmsback.inventory.repository;

import com.project.mdm.prod.entity.TmpZon;
import com.project.wmsback.warehouse.entity.BizDvsn;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.project.mdm.prod.entity.QProd.prod;
import static com.project.wmsback.inventory.entity.QInv.inv;
import static com.project.wmsback.warehouse.entity.QFxngLoc.fxngLoc;
import static com.project.wmsback.warehouse.entity.QLoc.loc;
import static com.project.wmsback.warehouse.entity.QZon.zon;

/**
 * 로케이션 점유 맵 조회 — JPAQueryFactory만 드는 읽기 전용 조회 포트
 * (LocCapacityQueryRepository와 같은 형태, 3파일 삼각형 아님). 병합은 InvService가 한다.
 */
@Repository
@RequiredArgsConstructor
public class LocMapQueryRepository {

    private final JPAQueryFactory queryFactory;

    /** 맵 1칸의 기본 정보 — 로케이션·존·고정상품(미지정이면 fxng* null) */
    public record LocRow(Long locId, String locCd, String zonCd, String zonNm,
                         BizDvsn bizDvsn, TmpZon tmpZon, Long maxQty,
                         String fxngProdCd, String fxngProdNm, Long fxngMinQty) {
    }

    /** 로케이션의 전 상품 재고 합 */
    public record QtySums(long onHandQty, long alocQty, long hldQty) {
    }

    /** STORAGE 로케이션 전건 — 스테이징은 격자가 아니라 맵에서 제외한다 (uq_fxng_loc라 join 곱셈 오염 없음) */
    public List<LocRow> locRows() {
        List<Tuple> rows = queryFactory
                .select(loc.id, loc.locCd, zon.zonCd, zon.zonNm, zon.bizDvsn, loc.tmpZon, loc.maxQty,
                        prod.prodCd, prod.prodNm, fxngLoc.minQty)
                .from(loc)
                .join(loc.zon, zon)
                .leftJoin(fxngLoc).on(fxngLoc.loc.eq(loc))
                .leftJoin(fxngLoc.prod, prod)
                .where(loc.locTyp.eq(LocTyp.STORAGE))
                .orderBy(loc.locCd.asc())
                .fetch();

        return rows.stream()
                .map(row -> new LocRow(
                        row.get(loc.id), row.get(loc.locCd), row.get(zon.zonCd), row.get(zon.zonNm),
                        row.get(zon.bizDvsn), row.get(loc.tmpZon), row.get(loc.maxQty),
                        row.get(prod.prodCd), row.get(prod.prodNm), row.get(fxngLoc.minQty)))
                .toList();
    }

    /** 로케이션별 전 상품 재고 합 (재고 행이 있는 로케이션만) */
    public Map<Long, QtySums> qtySumsByLoc() {
        List<Tuple> rows = queryFactory
                .select(inv.loc.id, inv.onHandQty.sum(), inv.alocQty.sum(), inv.hldQty.sum())
                .from(inv)
                .groupBy(inv.loc.id)
                .fetch();

        Map<Long, QtySums> byLoc = new HashMap<>();
        for (Tuple row : rows) {
            byLoc.put(row.get(inv.loc.id), new QtySums(
                    sumOf(row.get(inv.onHandQty.sum())),
                    sumOf(row.get(inv.alocQty.sum())),
                    sumOf(row.get(inv.hldQty.sum()))));
        }
        return byLoc;
    }

    /** 고정 자리의 지정 상품 현재고 합 — 타상품 재고는 빼고 본다 (SpmtQueryRepository.targets와 같은 정의) */
    public Map<Long, Long> fxngOnHandByLoc() {
        List<Tuple> rows = queryFactory
                .select(inv.loc.id, inv.onHandQty.sum())
                .from(inv)
                .join(fxngLoc).on(fxngLoc.loc.eq(inv.loc).and(fxngLoc.prod.eq(inv.prod)))
                .groupBy(inv.loc.id)
                .fetch();

        Map<Long, Long> byLoc = new HashMap<>();
        for (Tuple row : rows) {
            byLoc.put(row.get(inv.loc.id), sumOf(row.get(inv.onHandQty.sum())));
        }
        return byLoc;
    }

    /** 대상 행이 없으면 SUM이 null이다 — 0으로 본다 */
    private long sumOf(Long value) {
        return Objects.requireNonNullElse(value, 0L);
    }
}
