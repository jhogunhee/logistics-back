package com.project.wmsback.inventory.service;

import com.project.mdm.nbr.service.NbrService;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.wmsback.inventory.dto.InvStktkCreateRequest;
import com.project.wmsback.inventory.dto.InvStktkCreateResponse;
import com.project.wmsback.inventory.dto.InvStktkDetailResponse;
import com.project.wmsback.inventory.dto.InvStktkLnAddRequest;
import com.project.wmsback.inventory.dto.InvStktkLnSaveRequest;
import com.project.wmsback.inventory.dto.InvStktkResponse;
import com.project.wmsback.inventory.dto.InvStktkSearchCond;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvStktk;
import com.project.wmsback.inventory.entity.InvStktkLn;
import com.project.wmsback.inventory.entity.RefDocTyp;
import com.project.wmsback.inventory.entity.TxTyp;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.inventory.repository.InvStktkLnRepository;
import com.project.wmsback.inventory.repository.InvStktkRepository;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.wmsback.warehouse.repository.LocRepository;
import com.project.wmsback.warehouse.repository.LotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 재고조사(실사). 조사 범위를 지정해 라인을 만들고(전산수량 스냅샷), 실사수량을 입력한 뒤
 * 확정 시점에 차이를 ADJUST로 보정한다.
 *
 * 이 서비스가 재고 수량 정정의 유일한 경로다 — 건별 즉시 조정 화면을 따로 두지 않고, 특정 재고 하나의
 * 정정도 범위를 좁게 잡은 조사로 수행한다(입고 확정 후 수량 정정 포함 — docs/design.md).
 *
 * 조정수량 = 실사수량 − <b>확정시점</b> 전산수량이다(2026-08-03 결정). 확정 트랜잭션이 재고 행 락을 걸고
 * 전산수량을 다시 읽으므로 확정 후 onHand는 실사수량과 정확히 일치한다 — 조사 중 다른 업무로 재고가
 * 변했더라도 실사값이 이긴다. 조사 생성 시점 스냅샷(sysQty)은 「조사 시작 때는 얼마였나」의 기록으로 남고,
 * 화면은 둘을 비교해 「조사 중 변동됨」을 표시한다.
 *
 * 실적 테이블은 없다 — 실적의 실체는 inv_hist의 ADJUST 행(rfn_doc_no = 조사번호)이며,
 * 「이력 1건 기록 + 스냅샷 갱신 = 한 트랜잭션」 불변식을 그대로 지킨다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvStktkService {

    private static final String STKTK_NO_RULE_CD = "STKTK_NO";
    private static final String ADJ_RSN_GRP_CD = "ADJ_RSN";
    private static final String ADJ_RSN_LABEL = "조정사유";

    private final InvRepository invRepository;
    private final InvStore invStore;
    private final InvStktkRepository invStktkRepository;
    private final InvStktkLnRepository invStktkLnRepository;
    private final LocRepository locRepository;
    private final LotRepository lotRepository;
    private final ProdRepository prodRepository;
    private final RsnValidator rsnValidator;
    private final NbrService nbrService;

    public List<InvStktkResponse> list(InvStktkSearchCond cond) {
        return invStktkRepository.search(cond);
    }

    public InvStktkDetailResponse detail(Long stktkId) {
        InvStktk stktk = get(stktkId);
        return new InvStktkDetailResponse(stktk, invStktkLnRepository.searchByStktkId(stktkId));
    }

    /**
     * 조사 생성. 범위(존/로케이션/상품 — 모두 선택)에 걸리는 보관 재고를 훑어 라인을 만들고
     * 각 라인에 전산수량을 스냅샷한다. 재고를 선점하지 않으므로 이력·예약 어느 쪽도 건드리지 않는다.
     * @return 생성된 조사의 PK와 발급된 조사번호
     */
    @Transactional
    public InvStktkCreateResponse create(InvStktkCreateRequest request) {
        Loc scopeLoc = request.getLocId() == null ? null
                : locRepository.findById(request.getLocId())
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로케이션입니다: " + request.getLocId()));
        if (scopeLoc != null && scopeLoc.getLocTyp() != LocTyp.STORAGE) {
            throw new IllegalArgumentException("보관 로케이션만 조사할 수 있습니다 (스테이징 재고는 적치·출고확정의 소관): " + scopeLoc.getLocCd());
        }
        Prod scopeProd = request.getProdId() == null ? null
                : prodRepository.findById(request.getProdId())
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다: " + request.getProdId()));

        String zonCd = StringUtils.hasText(request.getZonCd()) ? request.getZonCd().trim() : null;
        List<Inv> targets = invRepository.searchStorageByScope(zonCd, request.getLocId(), request.getProdId());
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("조사 대상 재고가 없습니다 (범위를 확인하세요). "
                    + "장부에 없는 재고를 등록하려면 빈 조사가 아니라 조사 생성 후 [라인 추가]를 쓰세요.");
        }

        InvStktk stktk = InvStktk.builder()
                .stktkNo(nbrService.issue(STKTK_NO_RULE_CD, LocalDate.now()))
                .zonCd(zonCd)
                .loc(scopeLoc)
                .prod(scopeProd)
                .build();
        for (Inv target : targets) {
            stktk.addLine(InvStktkLn.builder()
                    .prod(target.getProd())
                    .loc(target.getLoc())
                    .lot(target.getLot())
                    .sysQty(target.getOnHandQty())
                    .build());
        }
        invStktkRepository.save(stktk);
        return new InvStktkCreateResponse(stktk.getId(), stktk.getStktkNo());
    }

    /**
     * 라인 수동 추가 — 장부에 없는 재고를 실사에서 발견했을 때(과잉재고)와 기초재고 등록 경로.
     * 재고 행이 없으면 전산수량 0으로 담기고, 확정 시 (+)조정이 재고 행을 새로 만든다.
     */
    @Transactional
    public void addLine(Long stktkId, InvStktkLnAddRequest request) {
        InvStktk stktk = getForUpdate(stktkId);
        stktk.requireEditable();

        if (request.getProdId() == null || request.getLocId() == null || request.getLotId() == null) {
            throw new IllegalArgumentException("추가할 재고의 상품·로케이션·Lot을 모두 지정해야 합니다.");
        }
        Prod prod = prodRepository.findById(request.getProdId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다: " + request.getProdId()));
        Loc loc = locRepository.findById(request.getLocId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로케이션입니다: " + request.getLocId()));
        Lot lot = lotRepository.findById(request.getLotId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 Lot입니다: " + request.getLotId()));

        if (loc.getLocTyp() != LocTyp.STORAGE) {
            throw new IllegalArgumentException("보관 로케이션만 조사 대상입니다: " + loc.getLocCd());
        }
        if (!lot.getProd().getId().equals(prod.getId())) {
            throw new IllegalArgumentException("Lot이 해당 상품의 것이 아닙니다 (" + lot.getLotNo() + " ↔ " + prod.getProdCd() + ")");
        }
        if (invStktkLnRepository.existsByInvStktkIdAndProdIdAndLocIdAndLotId(
                stktkId, prod.getId(), loc.getId(), lot.getId())) {
            throw new IllegalArgumentException("이미 담긴 재고입니다: " + prod.getProdCd() + " @ " + loc.getLocCd() + " · " + lot.getLotNo());
        }

        long sysQty = invRepository.findByProdIdAndLocIdAndLotId(prod.getId(), loc.getId(), lot.getId())
                .map(Inv::getOnHandQty)
                .orElse(0L);
        stktk.addLine(InvStktkLn.builder().prod(prod).loc(loc).lot(lot).sysQty(sysQty).build());
    }

    /** 라인 삭제 (조사 대상에서 제외). 작성 중인 조사만 — 소속 검증은 컬렉션에서 찾는 것으로 겸한다 */
    @Transactional
    public void deleteLine(Long stktkId, Long lnId) {
        InvStktk stktk = getForUpdate(stktkId);
        stktk.requireEditable();
        if (!stktk.removeLine(lnId)) {
            throw new IllegalArgumentException("이 조사의 라인이 아닙니다: " + lnId);
        }
    }

    /**
     * 실사수량·사유 저장 (작성 중). 사유 필수 여부는 여기서 따지지 않는다 — 차이가 있는 라인만 필수인데
     * 그 차이는 확정 시점 전산수량으로 정해지기 때문이다. 입력된 사유의 형식(코드 존재·기타 텍스트)만 검증한다.
     */
    @Transactional
    public void saveLines(Long stktkId, InvStktkLnSaveRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("저장할 라인이 없습니다.");
        }
        InvStktk stktk = getForUpdate(stktkId);
        stktk.requireEditable();

        Map<Long, InvStktkLn> lnById = new HashMap<>();
        for (InvStktkLn ln : invStktkLnRepository.findByStktkIdOrderByInvKey(stktkId)) {
            lnById.put(ln.getId(), ln);
        }

        for (InvStktkLnSaveRequest.Item item : request.getItems()) {
            if (item.getLnId() == null) {
                throw new IllegalArgumentException("저장할 라인이 지정되지 않았습니다.");
            }
            InvStktkLn ln = lnById.get(item.getLnId());
            if (ln == null) {
                throw new IllegalArgumentException("이 조사의 라인이 아닙니다: " + item.getLnId());
            }
            if (item.getStktkQty() != null && item.getStktkQty() < 0) {
                throw new IllegalArgumentException("실사수량은 0 이상이어야 합니다: " + ln.getProd().getProdCd()
                        + " @ " + ln.getLoc().getLocCd());
            }
            String rsnCd = StringUtils.hasText(item.getRsnCd()) ? item.getRsnCd() : null;
            String rsnDscr = rsnCd == null ? null
                    : rsnValidator.validate(ADJ_RSN_GRP_CD, ADJ_RSN_LABEL, rsnCd, item.getRsnDscr());
            ln.count(item.getStktkQty(), rsnCd, rsnDscr);
        }
    }

    /**
     * 전산수량 재스냅샷. 조사 중 다른 업무로 재고가 변했을 때 화면의 기준값(sysQty)을 현재로 맞춘다.
     * 실사수량은 건드리지 않는다 — 실물을 센 값이라 전산 사정으로 바뀌면 안 된다.
     * 조정 계산에는 영향이 없다(확정은 언제나 확정시점 전산수량을 다시 읽는다) — 순수 표시용 갱신이다.
     */
    @Transactional
    public void resync(Long stktkId) {
        InvStktk stktk = getForUpdate(stktkId);
        stktk.requireEditable();
        for (InvStktkLn ln : invStktkLnRepository.findByStktkIdOrderByInvKey(stktkId)) {
            long nowQty = invRepository
                    .findByProdIdAndLocIdAndLotId(ln.getProd().getId(), ln.getLoc().getId(), ln.getLot().getId())
                    .map(Inv::getOnHandQty)
                    .orElse(0L);
            ln.resync(nowQty);
        }
    }

    /** 조사 취소 (확정 전 폐기). 재고를 선점한 적이 없으므로 되돌릴 것이 없다 — 라인은 보존한다 */
    @Transactional
    public void cancel(Long stktkId) {
        InvStktk stktk = getForUpdate(stktkId);
        stktk.cancel();
    }

    /**
     * 확정 — 라인별 차이를 ADJUST로 반영한다. 전체가 한 트랜잭션이라 한 라인이라도 검증에 걸리면 전량 롤백된다.
     *
     * 라인마다 (1) 재고 행 락 → (2) 확정시점 전산수량 기록 → (3) 차이만큼 inv_hist ADJUST + onHand 보정을
     * 수행한다. 차이가 0인 라인은 ADJUST를 남기지 않고(ck_invh_qty가 변동 0을 금지) 사유도 요구하지 않는다.
     * 실사수량 미입력(null) 라인은 「미조사」로 건너뛴다 — 0(실물 없음)과 구분된다.
     */
    @Transactional
    public void confirm(Long stktkId) {
        InvStktk stktk = getForUpdate(stktkId);
        stktk.requireEditable();

        List<InvStktkLn> lines = invStktkLnRepository.findByStktkIdOrderByInvKey(stktkId);
        List<InvStktkLn> counted = lines.stream().filter(InvStktkLn::counted).toList();
        if (counted.isEmpty()) {
            throw new IllegalArgumentException("실사수량이 입력된 라인이 없습니다: " + stktk.getStktkNo());
        }

        // 실사 라인의 재고 행 전부 선락 — 여러 조사가 동시에 확정돼도 InvStore가 키 오름차순으로
        // 잠가 교착이 없다. 없는 행(전량 소진·수동 추가 라인)은 빠지고 아래에서 0으로 본다
        Map<InvKey, Inv> locked = invStore.lockAll(counted.stream()
                .map(ln -> new InvKey(ln.getProd().getId(), ln.getLoc().getId(), ln.getLot().getId()))
                .toList());

        for (InvStktkLn ln : counted) {
            Prod prod = ln.getProd();
            Loc loc = ln.getLoc();
            Lot lot = ln.getLot();

            // 확정 기준은 지금 이 순간의 전산수량이다 — 조사 생성 시점 스냅샷(sysQty)이 아니다.
            Inv inv = locked.get(new InvKey(prod.getId(), loc.getId(), lot.getId()));
            long cfmSysQty = inv == null ? 0L : inv.getOnHandQty();
            ln.confirm(cfmSysQty);

            long adjQty = ln.getStktkQty() - cfmSysQty;
            if (adjQty == 0) {
                continue; // 차이 없음 — 이력도 사유도 없다
            }
            requireRsn(ln);

            InvDocRef ref = InvDocRef.of(RefDocTyp.INV_STKTK, stktk.getStktkNo());
            if (adjQty < 0) {
                // 예약·보류분은 실사로 지울 수 없다 — 먼저 풀어야 한다 (ck_inv_qty가 최후 방어)
                long reserved = inv.getAlocQty() + inv.getHldQty();
                if (ln.getStktkQty() < reserved) {
                    throw new IllegalArgumentException("실사수량이 예약·보류 잔량보다 적습니다 (예약 " + inv.getAlocQty()
                            + " / 보류 " + inv.getHldQty() + " / 실사 " + ln.getStktkQty() + "): "
                            + prod.getProdCd() + " @ " + loc.getLocCd()
                            + " — 할당 해제·피킹 지시취소·결품 종결·이동지시 취소·보류 해제로 먼저 정리한 뒤 확정하세요.");
                }
                invStore.decrease(inv, -adjQty, TxTyp.ADJUST, ref);
            } else {
                // 장부에 없던 재고를 실사에서 발견했으면 재고 행이 새로 생긴다 (기초재고 등록도 이 경로)
                invStore.increase(prod, loc, lot, adjQty, TxTyp.ADJUST, ref);
            }
        }

        stktk.confirm();
    }

    private InvStktk get(Long stktkId) {
        return invStktkRepository.findById(stktkId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 재고조사입니다: " + stktkId));
    }

    /**
     * 편집·확정·취소가 상태를 검증하기 전에 거는 헤더 락. 락 없이 검증하면 확정과 동시에 돈 편집이
     * 낡은 「작성 중」 상태로 통과해, 확정된 조사의 라인이 뒤늦게 고쳐진다 (ADJUST는 이미 이전 값으로
     * 반영된 뒤라 기록과 실제 조정이 어긋난다).
     */
    private InvStktk getForUpdate(Long stktkId) {
        return invStktkRepository.findByIdForUpdate(stktkId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 재고조사입니다: " + stktkId));
    }

    /** 차이가 있는 라인은 사유가 필수다 (차이 0 라인은 조정 자체가 없으므로 사유도 없다) */
    private void requireRsn(InvStktkLn ln) {
        if (!StringUtils.hasText(ln.getRsnCd())) {
            throw new IllegalArgumentException("차이가 있는 라인은 조정사유가 필요합니다 (전산 " + ln.getCfmSysQty()
                    + " / 실사 " + ln.getStktkQty() + "): " + ln.getProd().getProdCd() + " @ " + ln.getLoc().getLocCd());
        }
        rsnValidator.validate(ADJ_RSN_GRP_CD, ADJ_RSN_LABEL, ln.getRsnCd(), ln.getRsnDscr());
    }
}
