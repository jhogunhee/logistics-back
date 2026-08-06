package com.project.wmsback.inventory.service;

import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvHist;
import com.project.wmsback.inventory.entity.TxTyp;
import com.project.wmsback.inventory.repository.InvHistRepository;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.Lot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 재고 스냅샷 쓰기 포트. inv 행을 바꾸는 모든 경로가 여기를 지난다.
 *
 * <p>세 가지가 언제나 함께 일어나야 해서 존재한다 — (1) 스냅샷 증감, (2) inv_hist 1행 기록,
 * (3) 수량이 모두 0이 된 행 삭제. 서비스마다 손으로 맞추면 하나씩 빠지고 조건이 갈라진다.
 * <b>서비스는 {@link Inv}의 증감 메서드를 직접 부르지 않는다</b> — 그 메서드들은 이 클래스 전용이다.
 *
 * <p>락은 호출자 책임으로 남겨둔다. 어떤 락이 필요한지(행 락 유무·잡는 순서)는 업무마다 다르고
 * 교착 회피 순서를 아는 쪽도 서비스다 — 여기서는 이미 잡아둔 스냅샷을 받아 쓴다.
 */
@Component
@RequiredArgsConstructor
public class InvStore {

    private final InvRepository invRepository;
    private final InvHistRepository invHistRepository;

    /**
     * 물리 증가 + 이력 1행. 스냅샷이 없으면 만든다 (입고 검수 RECEIVE, 재고조사 증가 ADJUST).
     * 도착지가 있는 실물 이동은 {@link #move}를 쓴다.
     */
    public Inv increase(Prod prod, Loc loc, Lot lot, long qty, TxTyp txTyp, InvDocRef ref) {
        Inv inv = findOrCreate(prod, loc, lot);
        inv.increaseOnHand(qty);
        saveHist(txTyp, prod, loc, lot, qty, ref, null, null);
        return inv;
    }

    /**
     * 물리 감소 + 이력 1행 + 빈 행 정리 (검수 취소·재고조사 감소 ADJUST).
     * 수량이 감당 가능한지(잔량 초과·예약 침범)는 호출자가 이미 검증했다고 본다.
     */
    public void decrease(Inv inv, long qty, TxTyp txTyp, InvDocRef ref) {
        inv.decreaseOnHand(qty);
        saveHist(txTyp, inv.getProd(), inv.getLoc(), inv.getLot(), -qty, ref, null, null);
        purgeIfEmpty(inv);
    }

    /**
     * 실물 이동 — 출발 감소 + 도착 증가(없으면 생성) + 이력 2행 + 출발 빈 행 정리.
     * 이력 2행은 같은 from/to를 갖는다 (한 행만 봐도 이동 전체를 알 수 있게).
     *
     * @return 도착지 스냅샷
     */
    public Inv move(Inv fromInv, Loc toLoc, long qty, InvDocRef ref) {
        Prod prod = fromInv.getProd();
        Lot lot = fromInv.getLot();
        Loc fromLoc = fromInv.getLoc();

        // 도착지 조회를 출발지 감소보다 먼저 한다 — 조회는 auto-flush를 부르고, 그 사이에 낀 감소는
        // ck_inv_qty(aloc+hld<=on_hand)를 만족하지 못하는 중간 상태로 DB에 닿을 수 있다
        Inv toInv = findOrCreate(prod, toLoc, lot);
        fromInv.decreaseOnHand(qty);
        toInv.increaseOnHand(qty);

        saveHist(TxTyp.MOVE, prod, fromLoc, lot, -qty, ref, fromLoc.getId(), toLoc.getId());
        saveHist(TxTyp.MOVE, prod, toLoc, lot, qty, ref, fromLoc.getId(), toLoc.getId());
        purgeIfEmpty(fromInv);
        return toInv;
    }

    /** 예약 (출고 할당·이동지시 등록). 물리 이동이 아니므로 이력에 남기지 않는다 */
    public void reserve(Inv inv, long qty) {
        inv.reserve(qty);
    }

    /** 예약 해제·소진 (할당 해제·이동지시 취소, 피킹·이동확정) */
    public void release(Inv inv, long qty) {
        inv.release(qty);
        purgeIfEmpty(inv);
    }

    /** 보류 (재고보류 등록). 원장은 inv_hld 쪽이 담당하므로 이력에 남기지 않는다 */
    public void hold(Inv inv, long qty) {
        inv.hold(qty);
    }

    /** 보류 해제 */
    public void releaseHold(Inv inv, long qty) {
        inv.releaseHold(qty);
        purgeIfEmpty(inv);
    }

    private Inv findOrCreate(Prod prod, Loc loc, Lot lot) {
        return invRepository.findByProdIdAndLocIdAndLotId(prod.getId(), loc.getId(), lot.getId())
                .orElseGet(() -> invRepository.save(Inv.builder().prod(prod).loc(loc).lot(lot).build()));
    }

    private void saveHist(TxTyp txTyp, Prod prod, Loc loc, Lot lot, long qty, InvDocRef ref,
                          Long fromLocId, Long toLocId) {
        invHistRepository.save(InvHist.builder()
                .txTyp(txTyp)
                .prod(prod).loc(loc).lot(lot)
                .qty(qty)
                .rfnDocTyp(ref.rfnDocTyp())
                .rfnDocNo(ref.rfnDocNo())
                .ibLineId(ref.ibLineId())
                .cnclInvHistId(ref.cnclInvHistId())
                .fromLocId(fromLocId).toLocId(toLocId)
                .build());
    }

    /**
     * 수량이 모두 0이 된 스냅샷 행은 지운다 — 재고 테이블엔 실물·예약·보류가 있는 행만 남긴다.
     * 이력 합계=스냅샷 불변식은 유지된다 (이력 SUM=0 ↔ 행 없음).
     */
    private void purgeIfEmpty(Inv inv) {
        if (inv.isEmpty()) {
            invRepository.delete(inv);
        }
    }
}
