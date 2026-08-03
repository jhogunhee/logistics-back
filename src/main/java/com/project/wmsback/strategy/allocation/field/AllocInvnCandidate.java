package com.project.wmsback.strategy.allocation.field;

import com.project.wmsback.inventory.entity.Inv;

import java.time.LocalDate;

/**
 * 할당 후보 재고 1건의 값 스냅샷 (상품 + 로케이션 + Lot).
 *
 * <p>{@code avalQty}는 <b>스냅샷을 뜬 시점</b>의 가용재고다. 산정 중에는 이 값을 직접 깎지 않고
 * 산정기가 별도의 잔량 맵으로 소진을 추적한다 — 레코드를 불변으로 두어야 미리보기가 실제 재고를
 * 건드리지 않고, 같은 후보 목록으로 몇 번이든 다시 산정할 수 있다.
 *
 * <p>{@code bizDvsn}은 존 마스터에서 온다(로케이션 → 존코드 → 존). 존이 등록되지 않은
 * 로케이션이면 null이고, 그때는 계층 지정(BIZ_DVSN IN) 조건에서 자연히 빠진다.
 */
public record AllocInvnCandidate(
        Long invId,
        Long locId,
        String locCd,
        int pikngPrty,
        String bizDvsn,
        Long lotId,
        String lotNo,
        LocalDate mfgDt,
        LocalDate expiryDt,
        LocalDate receiptDt,
        long avalQty
) {

    public static AllocInvnCandidate of(Inv inv, String bizDvsn) {
        Integer pikngPrty = inv.getLoc().getPikngPrty();
        return new AllocInvnCandidate(
                inv.getId(),
                inv.getLoc().getId(), inv.getLoc().getLocCd(), pikngPrty != null ? pikngPrty : 0,
                bizDvsn,
                inv.getLot().getId(), inv.getLot().getLotNo(),
                inv.getLot().getMfgDt(), inv.getLot().getExpiryDt(), inv.getLot().getReceiptDt(),
                inv.avalQty());
    }
}
