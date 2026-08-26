package com.project.wmsback.inventory.service;

import com.project.common.dto.PageCond;
import com.project.common.dto.PageResponse;
import com.project.mdm.nbr.service.NbrService;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.wmsback.inventory.dto.InvAdjHldTargetResponse;
import com.project.wmsback.inventory.dto.InvAdjRequest;
import com.project.wmsback.inventory.dto.InvAdjResponse;
import com.project.wmsback.inventory.dto.InvAdjSearchCond;
import com.project.wmsback.inventory.dto.InvAdjTargetResponse;
import com.project.wmsback.inventory.dto.InvAdjTargetSearchCond;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvAdj;
import com.project.wmsback.inventory.entity.InvHld;
import com.project.wmsback.inventory.entity.RefDocTyp;
import com.project.wmsback.inventory.entity.TxTyp;
import com.project.wmsback.inventory.repository.InvAdjQueryRepository;
import com.project.wmsback.inventory.repository.InvAdjRepository;
import com.project.wmsback.inventory.repository.InvHldRepository;
import com.project.wmsback.warehouse.entity.BizDvsn;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.wmsback.warehouse.repository.LocRepository;
import com.project.wmsback.warehouse.repository.LotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 재고조정 — 장부와 실물이 맞는 상태에서 둘을 함께 증감시키는 의도된 처분(폐기·견본출고).
 *
 * <p><b>재고조사와의 경계.</b> 조사는 장부와 실물이 <b>어긋났을 때</b> 장부를 실물에 맞추고
 * 조정수량이 실사수량에서 파생된다. 조정은 둘이 <b>맞는 상태에서</b> 함께 움직이고 조정수량이
 * 입력값이다. 불량 반품을 폐기하는데 실사수량 0으로 적으면 「세어보니 없었다」는 거짓 기록이 되고,
 * 그러면 조사가 남긴 ADJUST를 장부 품질 지표로 읽을 수 없게 된다.
 *
 * <p><b>라인은 두 종류다.</b> 가용 라인(재고 행 지목, ±)과 보류 라인(보류 건 지목, − 전용).
 * 보류 라인이 보류 「건」을 지목하는 이유: 같은 재고 행에 사유가 다른 미해제 보류가 여러 건
 * 병존해서(2026-08-09 확정) 재고 행만 지목하면 어느 건에서 빠지는지 정해지지 않는다.
 * 건을 지목하면 항등식(inv.hld_qty = SUM(미해제 잔량))도 해제 실적이 그대로 지킨다.
 * 보류 라인이 해제와 차감을 한 트랜잭션에 묶는 것이 핵심이다 — 「보류 해제 → 조정」 2단계면
 * 그 사이에 폐기 대기분이 가용재고로 떠서, 재고이동이 가용만 보고(InvMovService) 그걸 보관존으로
 * 옮겨 정상 재고로 만들 수 있다.
 *
 * <p><b>락 순서 — 재고 행(InvStore 표준 키 오름차순) → 보류 건(id 오름차순) → 채번.</b>
 * {@link InvHldService#release}와 같은 순서라 둘이 동시에 돌아도 교착이 없다. 보류 건을 나중에
 * 잡는 이유도 그쪽과 같다 — 건별로 「보류 건 → 그 건의 재고 행」으로 잡으면 다건에서 물린다.
 * 채번이 마지막인 이유: 건별로 「재고 락 → 채번」을 반복하면 날짜별 공유 카운터 행 락이 재고 락
 * 사이에 끼어, 재고가 겹치는 두 요청이 카운터와 재고를 나눠 쥐고 맞물린다.
 *
 * <p><b>한 트랜잭션 · 전량 롤백.</b> 취소 경로가 없어 절반만 반영된 상태를 되돌릴 방법이
 * 반대 부호 조정뿐이다 (보류·로트변경과 같은 정책).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvAdjService {

    private static final String INV_ADJ_NO_RULE_CD = "INV_ADJ_NO";
    private static final String INV_ADJ_RSN_GRP_CD = "INV_ADJ_RSN";

    private final InvStore invStore;
    private final InvAdjRepository invAdjRepository;
    private final InvAdjQueryRepository invAdjQueryRepository;
    private final InvHldRepository invHldRepository;
    private final InvHldService invHldService;
    private final ProdRepository prodRepository;
    private final LocRepository locRepository;
    private final LotRepository lotRepository;
    private final RsnValidator rsnValidator;
    private final NbrService nbrService;

    /** 가용 라인 대상 조회 (보관 로케이션 재고 행 — 가용 0인 행도 포함, (+) 조정 대상이다) */
    public List<InvAdjTargetResponse> listTargets(InvAdjTargetSearchCond cond) {
        return invAdjQueryRepository.searchTargets(cond);
    }

    /** 보류 라인 대상 조회 (미해제 잔량이 남은 보류 건) */
    public List<InvAdjHldTargetResponse> listHldTargets(InvAdjTargetSearchCond cond) {
        return invAdjQueryRepository.searchHldTargets(cond);
    }

    /** 실적 조회 (append-only 로그 — 무한히 자라므로 서버 페이징, 전량 조회를 두지 않는다) */
    public PageResponse<InvAdjResponse> list(InvAdjSearchCond cond, PageCond pageCond) {
        return invAdjRepository.search(cond, pageCond);
    }

    /**
     * 재고조정 실행. 처리 순서는 재고 키 → 보류 건 id 오름차순으로 고정한다 — 같은 재고 행에
     * 여러 라인이 걸릴 수 있어(보류 건 둘을 함께 폐기하는 요청) 결과가 요청 순서에 좌우되면 안 된다.
     *
     * @return 발급된 재고조정 번호 목록 (요청 순서)
     */
    @Transactional
    public List<String> adjust(InvAdjRequest request) {
        List<InvAdjRequest.Item> items = request.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("조정 대상이 없습니다.");
        }

        Set<LineKey> lineKeys = new LinkedHashSet<>();
        List<ItemCtx> ctxs = new ArrayList<>();
        for (InvAdjRequest.Item item : items) {
            if (item.getProdId() == null || item.getLocId() == null || item.getLotId() == null) {
                throw new IllegalArgumentException("조정할 재고의 상품·로케이션·Lot을 모두 지정해야 합니다.");
            }
            if (item.getAdjQty() == null || item.getAdjQty() == 0) {
                throw new IllegalArgumentException("조정수량은 0일 수 없습니다.");
            }
            if (item.getHldId() != null && item.getAdjQty() > 0) {
                throw new IllegalArgumentException("보류분은 감소 조정만 할 수 있습니다 — 늘리려면 재고 보류 등록을 쓰십시오.");
            }
            InvKey key = new InvKey(item.getProdId(), item.getLocId(), item.getLotId());
            // 중복 단위에 보류 건이 들어간다 — 같은 재고 행이라도 다른 보류 건 둘을 함께 폐기하는 것은
            // 정당하다. 가용 라인(hldId = null)만 재고 키당 1건이고, 두 번 실리면 뒤 건의 가용 검증이
            // 앞 건이 깎은 상태를 보게 되어 결과가 요청 순서에 좌우된다
            if (!lineKeys.add(new LineKey(key, item.getHldId()))) {
                throw new IllegalArgumentException("같은 대상이 두 번 실렸습니다 — 한 번에 한 값으로만 조정할 수 있습니다.");
            }
            String rsnDscr = rsnValidator.validate(INV_ADJ_RSN_GRP_CD, "조정사유", item.getRsnCd(), item.getRsnDscr());
            ctxs.add(new ItemCtx(item, key, rsnDscr));
        }

        ctxs.sort(Comparator.comparing((ItemCtx c) -> c.key.prodId())
                .thenComparing(c -> c.key.locId())
                .thenComparing(c -> c.key.lotId())
                .thenComparing(c -> c.item.getHldId(), Comparator.nullsFirst(Comparator.naturalOrder())));

        // 1) 재고 행 락 — InvStore 표준 순서(키 오름차순). 없는 키는 결과에서 빠진다
        //    ((+) 조정으로 이번에 처음 생기는 행 — increase의 findOrCreate가 그 시점에 잠근다.
        //     표준 순서 밖의 뒤늦은 획득이고 로트변경의 목적지 행이 갖는 노출과 같다)
        Map<InvKey, Inv> lockedInv = invStore.lockAll(ctxs.stream().map(c -> c.key).toList());

        // 2) 보류 건 락 — id 오름차순 (InvHldService.release와 같은 순서)
        Map<Long, InvHld> lockedHld = new HashMap<>();
        ctxs.stream().map(c -> c.item.getHldId()).filter(Objects::nonNull)
                .distinct().sorted()
                .forEach(hldId -> lockedHld.put(hldId, invHldRepository.findByIdForUpdate(hldId)
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 보류 건입니다: " + hldId))));

        // 3) 실행 — 검증 → 채번(락을 전부 잡은 뒤) → 재고 반영 → 실적
        Map<LineKey, String> noByLine = new HashMap<>();
        for (ItemCtx ctx : ctxs) {
            noByLine.put(new LineKey(ctx.key, ctx.item.getHldId()),
                    adjustOne(ctx, lockedInv.get(ctx.key), lockedHld.get(ctx.item.getHldId())));
        }
        return items.stream()
                .map(item -> noByLine.get(new LineKey(
                        new InvKey(item.getProdId(), item.getLocId(), item.getLotId()), item.getHldId())))
                .toList();
    }

    private String adjustOne(ItemCtx ctx, Inv inv, InvHld hld) {
        long adjQty = ctx.item.getAdjQty();

        // 재고 행이 없으면 (+) 조정으로 새로 만든다 — 장부에 없던 재고를 올리는 것이 아니라
        // 직전 조정을 되돌리는 것(ERR_ADJ)이 주 용도다. 발견재고·기초재고는 재고조사 소관
        if (inv == null) {
            if (adjQty < 0) {
                throw new IllegalArgumentException("존재하지 않는 재고는 감소 조정할 수 없습니다 (상품 " + ctx.item.getProdId()
                        + " / 로케이션 " + ctx.item.getLocId() + " / Lot " + ctx.item.getLotId() + ")");
            }
            if (hld != null) {
                throw new IllegalStateException("보류 건이 잡아둔 재고가 없습니다 (정합성 오류): " + hld.getHldNo());
            }
        }

        Prod prod = inv != null ? inv.getProd() : prodRepository.findById(ctx.item.getProdId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다: " + ctx.item.getProdId()));
        Loc loc = inv != null ? inv.getLoc() : locRepository.findById(ctx.item.getLocId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로케이션입니다: " + ctx.item.getLocId()));
        Lot lot = inv != null ? inv.getLot() : lotRepository.findById(ctx.item.getLotId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 Lot입니다: " + ctx.item.getLotId()));

        // 스테이징 제외 — 적치·출고확정이 소진 중이라 수량이 불안정하고, 적치 잔량 집계가
        // (ib_line_id, lot_id) 기반이라 조정의 ADJUST가 그 계산에서 빠진다 (보류·이동·조사와 같은 경계)
        if (loc.getLocTyp() != LocTyp.STORAGE) {
            throw new IllegalArgumentException("보관 로케이션의 재고만 조정할 수 있습니다: " + loc.getLocCd());
        }
        // 대기 구역 존 제외 — 로케이션 유형과 겹쳐 보이지만 서로를 대신하지 않는다. 유형은 로케이션마다
        // 자유롭게 정해지므로 입고대기·출고대기 존에 STORAGE 로케이션을 하나 등록하면 위 검사를 통과한다.
        // 존은 FK가 없어 미등록일 수 있다 — 그때는 대기존이 아니다 (RtngsLocResolver.inRtngsZon과 같은 방어)
        if (loc.getZon() != null && loc.getZon().getBizDvsn() != null && loc.getZon().getBizDvsn().staging()) {
            throw new IllegalArgumentException("대기 구역(" + loc.getZon().getBizDvsn().getLabel()
                    + ") 재고는 조정할 수 없습니다: " + loc.getLocCd()
                    + " — 적치·출고확정이 소진 중이라 세는 시점이 불안정합니다.");
        }
        // 재고조사 라인 수동 추가와 같은 검증 — Lot은 상품에 종속이라 어긋난 조합이면 키 자체가 틀렸다
        if (!lot.getProd().getId().equals(prod.getId())) {
            throw new IllegalArgumentException("Lot이 해당 상품의 것이 아닙니다 (" + lot.getLotNo() + " ↔ " + prod.getProdCd() + ")");
        }

        // 수량 검증은 채번보다 앞이다 — 롤백이 번호를 되돌리긴 하지만, 실패할 요청이 날짜별 공유
        // 카운터 행 락을 쥐고 있는 구간만큼 모든 조정이 직렬화된다 (로트변경·보류 등록과 같은 순서)
        if (hld != null) {
            if (!hld.getProd().getId().equals(prod.getId())
                    || !hld.getLoc().getId().equals(loc.getId())
                    || !hld.getLot().getId().equals(lot.getId())) {
                throw new IllegalArgumentException("보류 건이 가리키는 재고와 조정 대상이 다릅니다: " + hld.getHldNo());
            }
            // 잔량·상태의 판정 기준은 보류 원장의 주인이 가지므로 여기서 복제하지 않는다 —
            // releaseOn이 같은 검사를 하고, 이 위치에서는 「담을 수 있는 건인가」만 앞당겨 본다
            if (-adjQty > hld.remainingQty()) {
                throw new IllegalArgumentException("조정수량이 보류 미해제 잔량을 초과했습니다 (잔량 "
                        + hld.remainingQty() + "): " + hld.getHldNo());
            }
        } else if (adjQty < 0 && -adjQty > inv.avalQty()) {
            // 예약·보류를 침범하면 outb_alloc(inv_id 참조)·inv_hld가 가리키는 재고가 사라진다.
            // 보류분을 없애려면 보류 라인으로 담아야 한다 — 그래야 해제 실적이 함께 남는다
            throw new IllegalArgumentException("조정수량이 가용재고를 초과했습니다 (가용 " + inv.avalQty()
                    + " / 예약 " + inv.getAlocQty() + " / 보류 " + inv.getHldQty() + "): "
                    + prod.getProdCd() + " @ " + loc.getLocCd()
                    + " — 할당 해제·이동지시 취소로 먼저 정리하거나, 보류분이라면 보류 건을 담아 조정하세요.");
        }

        // 조정전수량은 화면 입력값이 아니라 락을 잡고 다시 읽은 값이다 (조사의 cfmSysQty와 같은 성격)
        long adjBfrQty = inv != null ? inv.getOnHandQty() : 0L;
        String adjNo = nbrService.issue(INV_ADJ_NO_RULE_CD, LocalDate.now());
        InvDocRef ref = InvDocRef.of(RefDocTyp.INV_ADJ, adjNo);
        String hldNo = hld != null ? hld.getHldNo() : null;

        if (hld != null) {
            // 보류 소진을 물리 감소보다 먼저 — 반대 순서면 aloc + hld <= on_hand 를 위반하는 중간
            // 상태가 이후 조회의 auto-flush에 실려 DB에 닿을 수 있다 (move()·pick()과 같은 함정).
            // 해제 실적(사유 ADJ)과 건 상태 전이는 보류 원장의 주인이 진다
            invHldService.releaseOn(hld, inv, -adjQty, InvHldService.RLZ_RSN_ADJ, null);
            invStore.decrease(inv, -adjQty, TxTyp.ADJUST, ref);
        } else if (adjQty > 0) {
            invStore.increase(prod, loc, lot, adjQty, TxTyp.ADJUST, ref);
        } else {
            invStore.decrease(inv, -adjQty, TxTyp.ADJUST, ref);
        }

        invAdjRepository.save(InvAdj.builder()
                .adjNo(adjNo)
                .prod(prod).loc(loc).lot(lot)
                .adjBfrQty(adjBfrQty).adjQty(adjQty)
                .hldNo(hldNo)
                .rsnCd(ctx.item.getRsnCd()).rsnDscr(ctx.rsnDscr)
                .build());
        return adjNo;
    }

    /** 라인의 정체성 — 재고 키 + 보류 건. 같은 재고 행에 보류 건이 여럿 걸릴 수 있어 키만으로는 부족하다 */
    private record LineKey(InvKey invKey, Long hldId) {
    }

    /** 건별 처리 문맥 — 검증·락 단계를 지나며 쓰인다 */
    private static class ItemCtx {
        final InvAdjRequest.Item item;
        final InvKey key;
        final String rsnDscr;

        ItemCtx(InvAdjRequest.Item item, InvKey key, String rsnDscr) {
            this.item = item;
            this.key = key;
            this.rsnDscr = rsnDscr;
        }
    }
}
