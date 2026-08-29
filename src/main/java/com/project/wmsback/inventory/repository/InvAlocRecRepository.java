package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvAlocRecResponse;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * 예약 대사 — {@code inv.aloc_qty}의 원장은 없다(물리 이동이 아니라 이력에 안 남긴다). 그래서 어긋남은
 * 원천별 미소진 잔량을 재고 키로 다시 합산해 장부와 견줘야만 드러난다. 그 쿼리가 여기 하나뿐이다.
 *
 * <p>원천 넷을 재고 키 (prod, loc, lot)로 모은다.
 * <ul>
 *   <li>출고 할당 — {@code outb_alloc.aloc_qty − pikng_qty}, 키는 할당이 가리키는 {@code inv} 행.
 *       미소진분이 남은 할당은 보관 행이 살아 있다(예약이 행을 지키므로 join으로 충분).</li>
 *   <li>이동지시 — {@code inv_mov_task.drct_qty − cmpl_qty} (DIRECTED), 키는 지시의 출발지 스냅샷.
 *       수시보충(RPLN)은 뺀다 — 예약의 주인이 할당이라 할당 항이 이미 센다.</li>
 *   <li>적치지시 — {@code putaway_task.drct_qty − cmpl_qty} (DIRECTED), 키는 라인의 상품 + RCV-STAGE + Lot.
 *       출발지 컬럼이 없어(항상 스테이징) 로케이션을 코드로 붙인다. 이동지시와 같은 성격의 예약이라
 *       같은 칸(mov_qty)에 합산한다.</li>
 *   <li>피킹된 물량 — {@code pikng_task.cmpl_qty} (살아 있는 지시 · 주문 미확정), 키는 지시의 상품·Lot 스냅샷
 *       + SHIP-STAGE. 피킹이 예약을 도착지로 옮기므로 이 몫이 스테이징 행의 {@code aloc_qty}여야 한다.</li>
 * </ul>
 * 재고 행과 FULL OUTER JOIN이다 — 「예약은 남았는데 행이 지워졌다」와 「행에 예약이 남았는데 원천이
 * 없다」를 둘 다 잡으려면 어느 쪽도 기준으로 삼을 수 없다. QueryDSL JPQL에는 FULL JOIN이 없어 네이티브다.
 */
@Repository
@RequiredArgsConstructor
public class InvAlocRecRepository {

    private static final String SQL = """
            WITH src AS (
                SELECT v.prod_id, v.loc_id, v.lot_id,
                       SUM(a.aloc_qty - a.pikng_qty) AS outb_qty, 0 AS mov_qty, 0 AS staged_qty
                  FROM outb_alloc a
                  JOIN inv v ON v.inv_id = a.inv_id
                 WHERE a.aloc_qty > a.pikng_qty
                 GROUP BY v.prod_id, v.loc_id, v.lot_id
                UNION ALL
                SELECT t.prod_id, t.from_loc_id, t.lot_id,
                       0, SUM(t.drct_qty - t.cmpl_qty), 0
                  FROM inv_mov_task t
                 WHERE t.status = 'DIRECTED' AND t.drct_qty > t.cmpl_qty AND t.mov_dvsn <> 'RPLN'
                 GROUP BY t.prod_id, t.from_loc_id, t.lot_id
                UNION ALL
                SELECT l.prod_id, g.loc_id, t.lot_id,
                       0, SUM(t.drct_qty - t.cmpl_qty), 0
                  FROM putaway_task t
                  JOIN ib_line l ON l.ib_line_id = t.ib_line_id
                  CROSS JOIN (SELECT loc_id FROM loc WHERE loc_cd = 'RCV-STAGE') g
                 WHERE t.status = 'DIRECTED' AND t.drct_qty > t.cmpl_qty
                 GROUP BY l.prod_id, g.loc_id, t.lot_id
                UNION ALL
                SELECT p.prod_id, s.loc_id, p.lot_id,
                       0, 0, SUM(p.cmpl_qty)
                  FROM pikng_task p
                  JOIN outb_alloc a ON a.outb_alloc_id = p.outb_alloc_id
                  JOIN outb_line l  ON l.outb_line_id = a.outb_line_id
                  JOIN outb_order o ON o.outb_order_id = l.outb_order_id
                  CROSS JOIN (SELECT loc_id FROM loc WHERE loc_cd = 'SHIP-STAGE') s
                 WHERE p.status <> 'CANCELLED' AND p.cmpl_qty > 0 AND o.status <> 'SHIPPED'
                 GROUP BY p.prod_id, s.loc_id, p.lot_id
            ),
            expected AS (
                SELECT prod_id, loc_id, lot_id,
                       SUM(outb_qty) AS outb_qty, SUM(mov_qty) AS mov_qty, SUM(staged_qty) AS staged_qty
                  FROM src
                 GROUP BY prod_id, loc_id, lot_id
            ),
            booked AS (
                SELECT prod_id, loc_id, lot_id, aloc_qty FROM inv WHERE aloc_qty > 0
            )
            SELECT pr.prod_cd, pr.prod_nm, lc.loc_cd, lt.lot_no,
                   COALESCE(b.aloc_qty, 0)   AS aloc_qty,
                   COALESCE(e.outb_qty, 0)   AS outb_qty,
                   COALESCE(e.mov_qty, 0)    AS mov_qty,
                   COALESCE(e.staged_qty, 0) AS staged_qty
              FROM booked b
              FULL OUTER JOIN expected e
                ON e.prod_id = b.prod_id AND e.loc_id = b.loc_id AND e.lot_id = b.lot_id
              JOIN prod pr ON pr.prod_id = COALESCE(b.prod_id, e.prod_id)
              JOIN loc  lc ON lc.loc_id  = COALESCE(b.loc_id,  e.loc_id)
              JOIN lot  lt ON lt.lot_id  = COALESCE(b.lot_id,  e.lot_id)
             ORDER BY pr.prod_cd, lc.loc_cd, lt.lot_no
            """;

    private final EntityManager em;

    /** 예약이 있거나 있어야 하는 재고 키 전부 — 어긋난 행만 고르는 것은 호출자 몫(전체를 보여 주는 화면도 있다) */
    @SuppressWarnings("unchecked")
    public List<InvAlocRecResponse> reconcile() {
        List<Object[]> rows = em.createNativeQuery(SQL).getResultList();
        List<InvAlocRecResponse> result = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            long aloc = ((Number) r[4]).longValue();
            long outb = ((Number) r[5]).longValue();
            long mov = ((Number) r[6]).longValue();
            long staged = ((Number) r[7]).longValue();
            result.add(new InvAlocRecResponse((String) r[0], (String) r[1], (String) r[2], (String) r[3],
                    aloc, outb, mov, staged, aloc - (outb + mov + staged)));
        }
        return result;
    }
}
