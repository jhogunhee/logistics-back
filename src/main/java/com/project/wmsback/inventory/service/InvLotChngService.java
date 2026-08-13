package com.project.wmsback.inventory.service;

import com.project.mdm.nbr.service.NbrService;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.wmsback.inventory.dto.InvLotChngRequest;
import com.project.wmsback.inventory.dto.InvLotChngResponse;
import com.project.wmsback.inventory.dto.InvLotChngSearchCond;
import com.project.wmsback.inventory.dto.InvLotChngTargetResponse;
import com.project.wmsback.inventory.dto.InvLotChngTargetSearchCond;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvLotChng;
import com.project.wmsback.inventory.entity.RefDocTyp;
import com.project.wmsback.inventory.repository.InvLotChngQueryRepository;
import com.project.wmsback.inventory.repository.InvLotChngRepository;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.warehouse.dto.LotResponse;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.wmsback.warehouse.repository.LotRepository;
import com.project.wmsback.warehouse.service.LotIssuer;
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
import java.util.Optional;
import java.util.Set;

/**
 * 재고 로트변경 — 수량을 지정한 Lot 속성정정. 「이 로케이션의 이 재고 중 N개는 제조일자가 X였다」를
 * 원 Lot에서 N개를 빼 (원 Lot의 상품+입고일자, X) 배치의 Lot으로 넣는 것으로 처리한다 —
 * 그 키의 Lot이 있으면 재사용(병합)하고 없으면 채번(분할)하므로 배치 키 충돌이 구조적으로 없다.
 * 배치 키의 정의는 검수와 같은 한 곳(LotIssuer)이다.
 *
 * 재고를 움직이지 않는 기존 재고 속성변경(LotAttrChngService — Lot 전량·번호 유지)과 별개 조작이다.
 * 재고 실체는 inv_hist의 ADJUST 2행이고, 원장은 inv_lot_chng(자기완결 로그)다.
 *
 * <b>락 순서 — 상품 → 원 Lot → inv 행(원+목적지, InvStore 표준 키 순서) → 채번.</b>
 * 상품·Lot은 검수·기존 정정과 같은 순서라 교착이 없고, 채번(nbr_seq)은 재고 락을 전부 잡은 뒤다
 * (보류 등록과 같은 이유 — 건별로 「재고 락 → 채번」을 반복하면 공유 카운터 행 락이 재고 락 사이에
 * 끼어 재고가 겹치는 두 요청이 맞물린다). 목적지 Lot에 이번에 처음 생기는 inv 행만 선락 대상에서
 * 빠지는데, 없는 행은 잠글 수 없고 생성 경합은 uq_inv가 방어한다.
 *
 * <b>한 트랜잭션 · 전량 롤백.</b> 기존 정정과 같은 이유 — 취소 경로가 없어서 절반만 반영된 상태를
 * 되돌릴 방법이 반대 방향 조작뿐이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvLotChngService {

    private static final String LOT_CHNG_NO_RULE_CD = "LOT_CHNG_NO";
    /** 사유 성격이 속성 정정과 같아 그룹을 재사용한다 */
    private static final String LOT_ATTR_RSN_GRP_CD = "LOT_ATTR_RSN";

    private final InvStore invStore;
    private final InvRepository invRepository;
    private final InvLotChngRepository invLotChngRepository;
    private final InvLotChngQueryRepository invLotChngQueryRepository;
    private final ProdRepository prodRepository;
    private final LotRepository lotRepository;
    private final LotIssuer lotIssuer;
    private final RsnValidator rsnValidator;
    private final NbrService nbrService;

    /** 변경 대상 재고 행 조회 (보관 로케이션 + 관리 상품 + 가용 > 0은 서버가 강제) */
    public List<InvLotChngTargetResponse> listTargets(InvLotChngTargetSearchCond cond) {
        return invLotChngQueryRepository.searchTargets(cond);
    }

    /**
     * 목적지 배치 후보 Lot 조회 — 원 Lot과 같은 상품+입고일자인 다른 Lot들 (병합 후보).
     * 화면의 목적지 선택 모달이 쓴다: 여기서 고르면 그 Lot의 날짜가 그대로 실려 병합이 되고
     * (§7 유통기한 불일치 거부가 입력 단계에서 차단된다), 없으면 직접 입력이 분할이 된다.
     */
    public List<LotResponse> listTargetLots(Long invId) {
        return invLotChngQueryRepository.searchTargetLots(invId).stream()
                .map(LotResponse::from)
                .toList();
    }

    /** 실적 조회 (append-only 로그) */
    public List<InvLotChngResponse> list(InvLotChngSearchCond cond) {
        return invLotChngRepository.search(cond);
    }

    /**
     * 로트변경 실행. 처리 순서는 (상품, 원 Lot, inv id) 오름차순으로 고정한다 —
     * 상품·Lot 락을 이 순서로 잡아 로트변경끼리·검수·기존 정정과 순서가 하나다.
     *
     * @return 발급된 로트변경 번호 목록 (요청 순서)
     */
    @Transactional
    public List<String> change(InvLotChngRequest request) {
        List<InvLotChngRequest.Item> items = request.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("로트변경 대상이 없습니다.");
        }

        Set<Long> invIds = new LinkedHashSet<>();
        List<ItemCtx> ctxs = new ArrayList<>();
        for (InvLotChngRequest.Item item : items) {
            if (item.getInvId() == null) {
                throw new IllegalArgumentException("변경할 재고가 지정되지 않았습니다.");
            }
            // 같은 행을 두 번 실으면 이중 차감이 된다 — 뒤 건의 가용 검증이 앞 건이 깎은 상태를 보게
            // 되어 결과가 요청 순서에 좌우된다. 애초에 거부한다
            if (!invIds.add(item.getInvId())) {
                throw new IllegalArgumentException("같은 재고 행이 두 번 실렸습니다 — 한 번에 한 값으로만 변경할 수 있습니다: " + item.getInvId());
            }
            if (item.getChngQty() == null || item.getChngQty() < 1) {
                throw new IllegalArgumentException("변경수량은 1 이상이어야 합니다.");
            }
            if (item.getMfgDt() == null || item.getExpiryDt() == null) {
                throw new IllegalArgumentException("제조일자와 유통기한은 모두 필수입니다.");
            }
            if (item.getExpiryDt().isBefore(item.getMfgDt())) {
                throw new IllegalArgumentException("유통기한이 제조일자보다 이전일 수 없습니다.");
            }
            String rsnDscr = rsnValidator.validate(LOT_ATTR_RSN_GRP_CD, "변경사유", item.getRsnCd(), item.getRsnDscr());
            ctxs.add(new ItemCtx(item, rsnDscr));
        }

        // 잠글 행을 고르기 위한 사전 조회 — 락 없는 스칼라 프로젝션 (엔티티로 미리 읽으면
        // 뒤에 락을 잡아도 낡은 인스턴스가 그대로 나온다. InvLockKey 참고)
        Map<Long, InvKey> keyByInvId = new HashMap<>();
        for (InvLockKey row : invRepository.findLockKeysByIdIn(invIds)) {
            keyByInvId.put(row.id(), row.key());
        }
        for (ItemCtx ctx : ctxs) {
            ctx.key = keyByInvId.get(ctx.item.getInvId());
            if (ctx.key == null) {
                throw new IllegalArgumentException("존재하지 않는 재고입니다: " + ctx.item.getInvId());
            }
        }

        ctxs.sort(Comparator.comparing((ItemCtx c) -> c.key.prodId())
                .thenComparing(c -> c.key.lotId())
                .thenComparing(c -> c.item.getInvId()));

        // 1) 상품 로우 락 — id 오름차순. 목적지 Lot의 재사용 조회·채번이 검수와 같은 경합을 가져
        //    같은 락으로 직렬화한다. FK가 없어 상품 행 부재를 DB가 못 잡는다 (기존 정정과 같은 방어)
        Map<Long, Prod> prodById = new HashMap<>();
        ctxs.stream().map(c -> c.key.prodId()).distinct().sorted().forEach(prodId ->
                prodById.put(prodId, prodRepository.findByIdForUpdate(prodId)
                        .orElseThrow(() -> new IllegalStateException("재고가 참조하는 상품이 없습니다 (정합성 오류): " + prodId))));

        // 2) 원 Lot 로우 락 — id 오름차순. 전량 정정(기존 화면)이 동시에 원 Lot의 제조일자를
        //    바꾸는 중이면 목적지 배치 키 계산이 어긋난다
        Map<Long, Lot> lotById = new HashMap<>();
        ctxs.stream().map(c -> c.key.lotId()).distinct().sorted().forEach(lotId ->
                lotById.put(lotId, lotRepository.findByIdForUpdate(lotId)
                        .orElseThrow(() -> new IllegalStateException("재고가 참조하는 Lot이 없습니다 (정합성 오류): " + lotId))));

        // 3) 검증 + 목적지 Lot 확보 — 상품 락 안이라 안전하고, 재고 락보다 앞이라 채번 규칙(락 계층)과 무관하다
        for (ItemCtx ctx : ctxs) {
            resolveTarget(ctx, prodById.get(ctx.key.prodId()), lotById.get(ctx.key.lotId()));
        }

        // 4) inv 행 락 — 원·목적지 키를 합쳐 InvStore 표준 순서(재고 키 오름차순)로 한 번에.
        //    목적지에 아직 행이 없으면 결과에서 빠진다 — changeLot의 findOrCreate가 생성한다
        Set<InvKey> keys = new LinkedHashSet<>();
        for (ItemCtx ctx : ctxs) {
            keys.add(ctx.key);
            keys.add(new InvKey(ctx.key.prodId(), ctx.key.locId(), ctx.toLot.getId()));
        }
        Map<InvKey, Inv> locked = invStore.lockAll(keys);

        // 5) 실행 — 가용 검증 → 채번(재고 락을 전부 잡은 뒤) → 장부 이동(ADJUST 2행) → 실적
        Map<Long, String> noByInvId = new HashMap<>();
        for (ItemCtx ctx : ctxs) {
            noByInvId.put(ctx.item.getInvId(), changeOne(ctx, locked.get(ctx.key)));
        }
        return items.stream().map(item -> noByInvId.get(item.getInvId())).toList();
    }

    /**
     * 목적지 Lot 확보 — 검수와 같은 배치 재사용 키 (원 Lot의 상품+입고일자, 요청 제조일자).
     * LotIssuer.findOrCreate를 쓰지 않는 이유: 재사용 경로에서 넘긴 유통기한이 무시되는 것이
     * 여기서는 「작업자 입력과 다른 값의 조용한 저장」이라, find로 조회해 검사한 뒤 create를 부른다.
     */
    private void resolveTarget(ItemCtx ctx, Prod prod, Lot fromLot) {
        InvLotChngRequest.Item item = ctx.item;
        ctx.fromLot = fromLot;

        if (prod.getShelfLifeDays() == null) {
            throw new IllegalArgumentException("유통기한 미관리 상품의 재고는 로트변경 대상이 아닙니다: "
                    + prod.getProdCd() + " / " + fromLot.getLotNo());
        }
        // lot.receipt_dt는 스키마상 nullable — null이면 목적지 배치 키가 정의되지 않는다 (기존 정정의 null 가드와 같은 방어)
        if (fromLot.getReceiptDt() == null) {
            throw new IllegalArgumentException("원 Lot의 입고일자가 없어 목적지 배치를 정할 수 없습니다: " + fromLot.getLotNo());
        }
        // 관리 전환 전에 생긴 Lot은 두 날짜가 비어 있을 수 있다(전환은 소급 적용하지 않는다) —
        // 원장 스냅샷이 NOT NULL이라 여기서 세우고, 채울 경로(기존 정정)를 안내한다
        if (fromLot.getMfgDt() == null || fromLot.getExpiryDt() == null) {
            throw new IllegalArgumentException("원 Lot의 제조일자·유통기한이 비어 있습니다 — 재고 속성변경(전량 정정)으로 먼저 채우십시오: "
                    + fromLot.getLotNo());
        }
        if (item.getMfgDt().isAfter(fromLot.getReceiptDt())) {
            throw new IllegalArgumentException("제조일자가 입고일자보다 미래일 수 없습니다 (입고 " + fromLot.getReceiptDt() + "): "
                    + fromLot.getLotNo());
        }
        // 제조일자가 같으면 목적지 = 원 Lot이라 무의미하다. 속성이 같은 순수 분할은 지원하지 않는다 —
        // 일부 격리는 재고보류가, 로케이션 나누기는 재고이동이 맡는 업무다
        if (item.getMfgDt().equals(fromLot.getMfgDt())) {
            throw new IllegalArgumentException("제조일자가 원 Lot과 같습니다 — 정정할 것이 없습니다 (유통기한만 고치려면 재고 속성변경을 쓰십시오): "
                    + fromLot.getLotNo());
        }

        Optional<Lot> found = lotIssuer.find(prod, fromLot.getReceiptDt(), item.getMfgDt());
        if (found.isPresent()) {
            Lot toLot = found.get();
            // 병합 대상의 유통기한이 입력과 다르면 거부 — 실제 값을 안내한다.
            // 정말 입력값이 맞다면 재고 속성변경으로 그 Lot의 유통기한을 먼저 고치고 다시 시도한다
            if (!item.getExpiryDt().equals(toLot.getExpiryDt())) {
                throw new IllegalArgumentException("그 배치의 Lot이 이미 있고 유통기한이 " + toLot.getExpiryDt()
                        + "입니다 (" + toLot.getLotNo() + "): 유통기한을 확인하거나, 그 Lot의 유통기한이 틀렸다면"
                        + " 재고 속성변경으로 먼저 정정하십시오 (요청 " + item.getExpiryDt() + ")");
            }
            ctx.toLot = toLot;
            ctx.toLotNew = false;
        } else {
            ctx.toLot = lotIssuer.create(prod, fromLot.getReceiptDt(), item.getMfgDt(), item.getExpiryDt());
            ctx.toLotNew = true;
        }
    }

    private String changeOne(ItemCtx ctx, Inv inv) {
        // 선조회와 락 사이에 그 행이 소진돼 지워졌으면 여기서 걸린다 (키 락은 재생성까지 따라간다)
        if (inv == null) {
            throw new IllegalArgumentException("존재하지 않는 재고입니다: " + ctx.item.getInvId());
        }
        Prod prod = inv.getProd();
        Loc loc = inv.getLoc();

        // 스테이징 제외 — 적치 잔량 집계가 (ib_line_id, lot_id) 기반이라 로트변경의 ADJUST(-N)가
        // 빠지고 새 Lot은 후보에 안 뜬다. 적치 전 제조일자 오류는 검수 취소 후 재검수가 정답
        if (loc.getLocTyp() != LocTyp.STORAGE) {
            throw new IllegalArgumentException("보관 로케이션의 재고만 로트변경할 수 있습니다: " + loc.getLocCd());
        }
        long qty = ctx.item.getChngQty();
        // 예약·보류 침범 금지 — outb_alloc(inv_id 참조)·inv_hld가 가리키는 재고가 사라지면 안 된다
        if (qty > inv.avalQty()) {
            throw new IllegalArgumentException("변경수량이 가용재고를 초과했습니다 (가용 " + inv.avalQty() + "): "
                    + prod.getProdCd() + " @ " + loc.getLocCd());
        }

        String lotChngNo = nbrService.issue(LOT_CHNG_NO_RULE_CD, LocalDate.now());
        invStore.changeLot(inv, ctx.toLot, qty, InvDocRef.of(RefDocTyp.LOT_CHNG, lotChngNo));
        // 실적은 자기완결 로그 — 두 Lot의 번호·날짜를 실행 시점 스냅샷으로 담는다
        invLotChngRepository.save(InvLotChng.builder()
                .lotChngNo(lotChngNo)
                .prod(prod).loc(loc)
                .fromLotId(ctx.fromLot.getId()).fromLotNo(ctx.fromLot.getLotNo())
                .fromMfgDt(ctx.fromLot.getMfgDt()).fromExpiryDt(ctx.fromLot.getExpiryDt())
                .toLotId(ctx.toLot.getId()).toLotNo(ctx.toLot.getLotNo())
                .toMfgDt(ctx.toLot.getMfgDt()).toExpiryDt(ctx.toLot.getExpiryDt())
                .chngQty(qty).toLotNewYn(ctx.toLotNew)
                .rsnCd(ctx.item.getRsnCd()).rsnDscr(ctx.rsnDscr)
                .build());
        return lotChngNo;
    }

    /** 건별 처리 문맥 — 검증·락 단계를 지나며 채워진다 */
    private static class ItemCtx {
        final InvLotChngRequest.Item item;
        final String rsnDscr;
        InvKey key;
        Lot fromLot;
        Lot toLot;
        boolean toLotNew;

        ItemCtx(InvLotChngRequest.Item item, String rsnDscr) {
            this.item = item;
            this.rsnDscr = rsnDscr;
        }
    }
}
