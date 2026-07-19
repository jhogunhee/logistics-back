package com.project.wmsback.inbound.service;

import com.project.wmsback.inbound.dto.PutawayCandidateResponse;
import com.project.wmsback.inbound.dto.PutawaySearchCond;
import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.repository.IbLineRepository;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvHist;
import com.project.wmsback.inventory.entity.RefDocType;
import com.project.wmsback.inventory.entity.TxType;
import com.project.wmsback.inventory.repository.InvHistRepository;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.master.dto.LocResponse;
import com.project.wmsback.master.entity.Loc;
import com.project.wmsback.master.entity.LocType;
import com.project.wmsback.master.entity.Lot;
import com.project.wmsback.master.entity.Sku;
import com.project.wmsback.master.repository.LocRepository;
import com.project.wmsback.master.repository.LotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 적치. 재고 이동(MOVE)의 특수 케이스 — 별도 지시/단계 없이 스테이징 재고를 바로 옮긴다 (docs/design.md 참고).
 * 화면 단위는 (입고 라인, Lot) 배치이고, 목록이 이미 유통기한(FEFO) 순으로 정렬돼 있으므로
 * 실행은 그 배치 하나만 처리한다 — FEFO 소진 순서는 목록 정렬이 대신한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PutawayService {

    private static final String STAGING_LOC_CD = "RCV-STAGE";

    private final IbLineRepository ibLineRepository;
    private final LocRepository locRepository;
    private final LotRepository lotRepository;
    private final InvRepository invRepository;
    private final InvHistRepository invHistRepository;

    /** 적치 대상 (라인, Lot) 배치 전체 — 검수는 됐지만 아직 전량 적치되지 않은 것 */
    public List<PutawayCandidateResponse> pendingLines(PutawaySearchCond cond) {
        return ibLineRepository.findAllPendingPutawayBatches(cond);
    }

    /** 대상 로케이션 후보 (SKU 온도대와 일치하는 STORAGE, pick_prty 오름차순 추천) */
    public List<LocResponse> candidateLocs(Long ibLineId) {
        IbLine ibLine = ibLineRepository.findById(ibLineId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고 라인입니다: " + ibLineId));
        return locRepository.findAllByTempZoneAndLocTypeOrderByPickPrtyAsc(ibLine.getSku().getTempZone(), LocType.STORAGE)
                .stream().map(LocResponse::from).toList();
    }

    /**
     * 적치 실행. 목록 화면에서 이미 (라인, Lot) 배치 단위로 행을 골랐으므로, 여기서는
     * FEFO 순회 없이 그 배치(Lot) 하나에서 요청 수량만큼 스테이징 → 대상 로케이션으로 옮긴다.
     */
    @Transactional
    public void putaway(Long ibLineId, Long lotId, Long qty, Long targetLocId) {
        if (qty == null || qty < 1) {
            throw new IllegalArgumentException("적치수량은 1 이상이어야 합니다.");
        }
        IbLine ibLine = ibLineRepository.findById(ibLineId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고 라인입니다: " + ibLineId));
        Sku sku = ibLine.getSku();
        Lot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 Lot입니다: " + lotId));

        Loc staging = locRepository.findByLocCd(STAGING_LOC_CD)
                .orElseThrow(() -> new IllegalStateException("입고 스테이징 로케이션(RCV-STAGE)이 없습니다."));
        Loc target = locRepository.findById(targetLocId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로케이션입니다: " + targetLocId));
        if (target.getLocType() != LocType.STORAGE) {
            throw new IllegalArgumentException("보관 로케이션이 아닙니다: " + target.getLocCd());
        }
        if (target.getTempZone() != sku.getTempZone()) {
            throw new IllegalArgumentException("온도대가 일치하지 않습니다 (SKU " + sku.getTempZone() + " / 로케이션 " + target.getTempZone() + "): " + target.getLocCd());
        }

        Inv stagingInv = invRepository.findBySkuIdAndLocIdAndLotId(sku.getId(), staging.getId(), lot.getId())
                .orElseThrow(() -> new IllegalStateException("스테이징 재고를 찾을 수 없습니다: " + sku.getSkuCd()));
        if (qty > stagingInv.getOnHandQty()) {
            throw new IllegalArgumentException("적치수량이 이 배치의 미적치 잔량을 초과했습니다 (다른 주문과 공유된 Lot이 먼저 적치됐을 수 있습니다): " + sku.getSkuCd());
        }

        stagingInv.decreaseOnHand(qty);
        Inv targetInv = invRepository.findBySkuIdAndLocIdAndLotId(sku.getId(), target.getId(), lot.getId())
                .orElseGet(() -> invRepository.save(Inv.builder().sku(sku).loc(target).lot(lot).build()));
        targetInv.increaseOnHand(qty);

        invHistRepository.save(InvHist.builder()
                .txType(TxType.MOVE)
                .sku(sku).loc(staging).lot(lot)
                .qty(-qty)
                .refDocType(RefDocType.INBOUND)
                .refDocNo(ibLine.getIbOrder().getIbNo())
                .ibLineId(ibLineId)
                .fromLocId(staging.getId()).toLocId(target.getId())
                .build());
        invHistRepository.save(InvHist.builder()
                .txType(TxType.MOVE)
                .sku(sku).loc(target).lot(lot)
                .qty(qty)
                .refDocType(RefDocType.INBOUND)
                .refDocNo(ibLine.getIbOrder().getIbNo())
                .ibLineId(ibLineId)
                .fromLocId(staging.getId()).toLocId(target.getId())
                .build());

        // 스테이징 재고가 0이 되면 스냅샷 행을 삭제한다 (재고 테이블엔 실물이 있는 행만 남긴다).
        // 이력 합계=스냅샷 불변식은 유지된다 — 이력 SUM=0 ↔ 스냅샷 행 없음(=0).
        if (stagingInv.getOnHandQty() == 0 && stagingInv.getAllocQty() == 0) {
            invRepository.delete(stagingInv);
        }

        ibLine.putaway(qty);
        ibLine.getIbOrder().checkAndComplete();
    }
}