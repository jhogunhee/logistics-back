package com.project.wmsback.strategy.allocation.repository;

import com.project.wmsback.strategy.allocation.field.AlocInvnCandidate;
import com.project.wmsback.warehouse.entity.BizDvsn;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.project.wmsback.inventory.entity.QInv.inv;
import static com.project.wmsback.inventory.repository.InvQueryExpressions.avalQty;
import static com.project.wmsback.warehouse.entity.QLoc.loc;
import static com.project.wmsback.warehouse.entity.QLot.lot;
import static com.project.wmsback.warehouse.entity.QZon.zon;

/**
 * 할당 산정의 재고 조회 포트. 읽기 전용이라 Spring Data 인터페이스 없이 {@code JPAQueryFactory}만
 * 든다 (적치의 {@code PutawayQueryRepository}와 같은 형태).
 *
 * <p>하드 가드(보관 로케이션 · 가용 &gt; 0)가 여기서 강제된다 — 전략이 못 바꾸는 전제라
 * 산정기가 아니라 조회가 갖는 것이 맞다.
 */
@Repository
@RequiredArgsConstructor
public class AlocQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 상품별 후보 재고 스냅샷. <b>미리보기 전용</b>이다 — 락을 걸지 않으므로 실행 시점의
     * 가용재고는 다를 수 있고, 그래서 미리보기 결과는 화면에 「예상」으로 표시된다.
     *
     * <p>실전 경로는 이 메서드를 쓰지 않는다. 후보를 잠그며 읽어야 하는데(낡은 가용수량 방지)
     * 그 락 순서가 할당 서비스의 책임이기 때문이다.
     */
    public Map<Long, List<AlocInvnCandidate>> candidatesByProd(List<Long> prodIds) {
        Map<Long, List<AlocInvnCandidate>> result = new LinkedHashMap<>();
        if (prodIds == null || prodIds.isEmpty()) {
            return result;
        }
        NumberExpression<Long> aval = avalQty();

        List<Tuple> rows = queryFactory
                .select(inv.prod.id, inv.id,
                        loc.id, loc.locCd, loc.pikngPrty, zon.bizDvsn,
                        lot.id, lot.lotNo, lot.mfgDt, lot.expiryDt, lot.receiptDt,
                        aval)
                .from(inv)
                .join(inv.loc, loc)
                .join(inv.lot, lot)
                // 로케이션의 존은 FK가 없어 미등록 존이 있을 수 있다 — 그때 업무유형은 null이고
                // 계층 지정(BIZ_DVSN IN) 조건에서 자연히 빠진다
                .leftJoin(loc.zon, zon)
                .where(
                        inv.prod.id.in(prodIds),
                        // 스테이징 재고는 후보가 아니다 — 피킹이 「보관 → SHIP-STAGE」라
                        // 스테이징을 할당하면 피킹이 성립하지 않는다
                        loc.locTyp.eq(LocTyp.STORAGE),
                        aval.gt(0L),
                        // 반품존 재고는 후보가 아니다 — 보류를 풀자마자 반품 불량이 FEFO 최우선으로 나가면 안 된다.
                        // 양품 재판정은 「보류 해제 → 재고 이동(보관존)」 두 단계다
                        zon.bizDvsn.ne(BizDvsn.RTNGS).or(zon.bizDvsn.isNull())
                )
                .fetch();

        for (Tuple row : rows) {
            AlocInvnCandidate candidate = new AlocInvnCandidate(
                    row.get(inv.id),
                    row.get(loc.id), row.get(loc.locCd),
                    row.get(loc.pikngPrty) != null ? row.get(loc.pikngPrty) : 0,
                    row.get(zon.bizDvsn) != null ? row.get(zon.bizDvsn).name() : null,
                    row.get(lot.id), row.get(lot.lotNo),
                    row.get(lot.mfgDt), row.get(lot.expiryDt), row.get(lot.receiptDt),
                    row.get(aval) != null ? row.get(aval) : 0L);
            result.computeIfAbsent(row.get(inv.prod.id), key -> new ArrayList<>()).add(candidate);
        }
        return result;
    }

    /**
     * 존 id → 업무유형. 존은 마스터라 건수가 작아 통째로 읽고 메모리에서 붙인다 —
     * 실전 경로가 후보를 <b>락을 걸며 한 건씩</b> 읽기 때문에, 재고 조회에 존을 조인해 둘 수 없다.
     */
    public Map<Long, String> bizDvsnByZon() {
        Map<Long, String> map = new HashMap<>();
        for (Tuple row : queryFactory.select(zon.id, zon.bizDvsn).from(zon).fetch()) {
            map.put(row.get(zon.id), row.get(zon.bizDvsn) != null ? row.get(zon.bizDvsn).name() : null);
        }
        return map;
    }
}
