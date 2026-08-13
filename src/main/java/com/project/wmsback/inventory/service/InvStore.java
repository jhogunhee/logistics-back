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

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 재고 스냅샷 쓰기 포트. inv 행을 바꾸는 모든 경로가 여기를 지난다.
 *
 * <p>세 가지가 언제나 함께 일어나야 해서 존재한다 — (1) 스냅샷 증감, (2) inv_hist 1행 기록,
 * (3) 수량이 모두 0이 된 행 삭제. 서비스마다 손으로 맞추면 하나씩 빠지고 조건이 갈라진다.
 * <b>서비스는 {@link Inv}의 증감 메서드를 직접 부르지 않는다</b> — 그 메서드들은 이 클래스 전용이다.
 *
 * <p>락 창구이기도 하다 (2026-08-09 — 「락은 서비스 쪽 책임」 번복). 순서를 서비스마다 손으로
 * 맞추다 세 갈래(id순·키순·무정렬)로 갈라져 교착 짝이 생겼다. 표준 순서는 <b>재고 키(상품 →
 * 로케이션 → Lot) 오름차순</b> 하나이고 그 정의는 {@link #LOCK_ORDER}뿐이다 — 다건을 잠글
 * 서비스는 {@link #lockAll}/{@link #lockAllByIds}를 지나고, 쓰기 메서드에 넘기는 {@link Inv}는
 * 그렇게 잠근 행이어야 한다. docs/design.md 「락 순서」 참고.
 */
@Component
@RequiredArgsConstructor
public class InvStore {

    /** 락 순서의 유일한 정의 — 재고 키(상품 → 로케이션 → Lot) 오름차순 */
    private static final Comparator<InvKey> LOCK_ORDER =
            Comparator.comparing(InvKey::prodId).thenComparing(InvKey::locId).thenComparing(InvKey::lotId);

    private final InvRepository invRepository;
    private final InvHistRepository invHistRepository;

    /** 재고 행 비관적 락 — 단건. 단건은 순서가 없지만 창구를 하나로 두려고 여기로 모은다 */
    public Optional<Inv> lock(InvKey key) {
        return invRepository.findByKeyForUpdate(key.prodId(), key.locId(), key.lotId());
    }

    /**
     * 재고 행 비관적 락 — 키 오름차순 일괄. 없는 키는 결과에서 빠진다 (전량 소진으로 지워진 행,
     * 아직 안 생긴 행 — 있어야 하는지의 판정은 호출자 몫).
     *
     * <p>한 건씩 잠그는 이유: {@code WHERE key IN (…) ORDER BY} 일괄 락은 결과 정렬을 보장할 뿐
     * <b>락 획득 순서를 보장하지 않는다</b> (플랜에 따라 물리 순서로 잠근다).
     */
    public Map<InvKey, Inv> lockAll(Collection<InvKey> keys) {
        Map<InvKey, Inv> locked = new LinkedHashMap<>();
        keys.stream().distinct().sorted(LOCK_ORDER)
                .forEach(key -> lock(key).ifPresent(inv -> locked.put(key, inv)));
        return locked;
    }

    /**
     * id로 지목된 재고 행을 키 순서로 잠근다 (요청·후보 목록이 inv id만 들고 있는 경로용).
     * 키를 스칼라로 선조회한 뒤 <b>키로</b> 잠그는 이유 둘 — (1) 정렬 표준이 키다. (2) 행이
     * 0이 되어 지워졌다 다시 생기면 id가 바뀌는데, 선조회와 락 사이의 재생성을 키 락은 따라간다.
     * 없는 id는 결과에서 빠진다.
     *
     * @return 요청한 inv id → 잠근 행
     */
    public Map<Long, Inv> lockAllByIds(Collection<Long> invIds) {
        if (invIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<Long, InvKey> keyById = new LinkedHashMap<>();
        for (InvLockKey row : invRepository.findLockKeysByIdIn(invIds)) {
            keyById.put(row.id(), row.key());
        }
        Map<InvKey, Inv> byKey = lockAll(keyById.values());
        Map<Long, Inv> locked = new LinkedHashMap<>();
        keyById.forEach((id, key) -> {
            Inv inv = byKey.get(key);
            if (inv != null) {
                locked.put(id, inv);
            }
        });
        return locked;
    }

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

    /**
     * 증가·이동 도착 대상 확보 — 있으면 잠그고, 없으면 만든다. 락 없이 읽어 증가시키면 같은 행에
     * 동시에 들어온 증가들이 각자 읽은 스냅샷 기준으로 덮어쓴다 (이력은 두 행인데 스냅샷은 한 번만
     * 는다). 새로 만드는 행은 잠글 수 없다 — 동시 생성은 uq_inv가 한쪽을 거부하는 것으로 방어한다.
     */
    private Inv findOrCreate(Prod prod, Loc loc, Lot lot) {
        return invRepository.findByKeyForUpdate(prod.getId(), loc.getId(), lot.getId())
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
