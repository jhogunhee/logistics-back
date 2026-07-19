package com.project.wmsback.inbound.service;

import com.project.wmsback.inbound.dto.ReceiptResponse;
import com.project.wmsback.inbound.dto.ReceiveRequest;
import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.entity.IbStatus;
import com.project.wmsback.inbound.repository.IbLineRepository;
import com.project.wmsback.inbound.repository.IbOrderRepository;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvHist;
import com.project.wmsback.inventory.entity.RefDocType;
import com.project.wmsback.inventory.entity.TxType;
import com.project.wmsback.inventory.repository.InvHistRepository;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.master.entity.Loc;
import com.project.wmsback.master.entity.Lot;
import com.project.wmsback.master.entity.Sku;
import com.project.wmsback.master.repository.LocRepository;
import com.project.wmsback.master.repository.LotRepository;
import com.project.wmsback.master.repository.SkuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 입고 검수/마감. 검수 저장은 라인 수량 누계 + Lot 생성 + 스테이징 재고 증가 + 재고 이력이
 * 한 트랜잭션으로 묶인다 (불변식: 이력 합계 = 스냅샷).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceivingService {

    /** 검수 합격분이 들어가는 입고 스테이징. 온도대 검증은 여기선 하지 않는다 (적치 때 수행) */
    private static final String STAGING_LOC_CD = "RCV-STAGE";

    private final IbOrderRepository ibOrderRepository;
    private final IbLineRepository ibLineRepository;
    private final LotRepository lotRepository;
    private final LocRepository locRepository;
    private final InvRepository invRepository;
    private final InvHistRepository invHistRepository;
    private final SkuRepository skuRepository;

    /** 검수 저장 (증분). 요청 라인 중 한 건이라도 실패하면 전체 롤백 */
    @Transactional
    public void receive(Long ibOrderId, ReceiveRequest req) {
        if (req.getLines() == null || req.getLines().isEmpty()) {
            throw new IllegalArgumentException("검수할 라인이 없습니다.");
        }
        IbOrder order = ibOrderRepository.findById(ibOrderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고예정입니다: " + ibOrderId));
        order.startReceiving();

        Loc staging = locRepository.findByLocCd(STAGING_LOC_CD)
                .orElseThrow(() -> new IllegalStateException("입고 스테이징 로케이션(RCV-STAGE)이 없습니다."));

        for (ReceiveRequest.Line line : req.getLines()) {
            receiveLine(order, staging, line);
        }

        order.checkAndAutoReceive(); // 전 라인 전량 검수됐으면 마감 없이 바로 RECEIVED(→ 적치까지 끝났다면 COMPLETED)로 전이
    }

    private void receiveLine(IbOrder order, Loc staging, ReceiveRequest.Line line) {
        IbLine ibLine = ibLineRepository.findById(line.getIbLineId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고 라인입니다: " + line.getIbLineId()));
        if (!ibLine.getIbOrder().getId().equals(order.getId())) {
            throw new IllegalArgumentException("다른 입고의 라인입니다: " + line.getIbLineId());
        }

        Sku sku = ibLine.getSku();
        long inspect = line.getInspectQty() != null ? line.getInspectQty() : 0;
        if (inspect < 1) {
            throw new IllegalArgumentException("검수수량은 1 이상이어야 합니다: " + sku.getSkuCd());
        }

        ibLine.receive(inspect);

        // 입고일자: 소급 등록 대비 라인별 입력 (비우면 오늘 = 실시간 등록)
        LocalDate receiptDt = line.getReceiptDt() != null ? line.getReceiptDt() : LocalDate.now();

        // 검수분: Lot 확보 → 스테이징 스냅샷 증가 → 재고 이력. 셋이 항상 한 트랜잭션
        Lot lot = findOrCreateLot(sku, line.getMfgDt(), receiptDt);
        Inv inv = invRepository.findBySkuIdAndLocIdAndLotId(sku.getId(), staging.getId(), lot.getId())
                .orElseGet(() -> invRepository.save(Inv.builder().sku(sku).loc(staging).lot(lot).build()));
        inv.increaseOnHand(inspect);
        invHistRepository.save(InvHist.builder()
                .txType(TxType.RECEIVE)
                .sku(sku).loc(staging).lot(lot)
                .qty(inspect)
                .refDocType(RefDocType.INBOUND)
                .refDocNo(order.getIbNo())
                .ibLineId(ibLine.getId())
                .build());
    }

    /**
     * 같은 배치(SKU+입고일자+제조일자)는 같은 Lot을 재사용한다
     * (증분 검수로 같은 라인을 여러 번 나눠 검수해도 Lot이 쪼개지지 않도록).
     * 유통기한 미관리 SKU는 제조일자가 항상 null이라 사실상 SKU+입고일자로만 구분된다.
     */
    private Lot findOrCreateLot(Sku sku, LocalDate mfgDt, LocalDate receiptDt) {
        boolean tracksShelfLife = sku.getShelfLifeDays() != null;
        if (tracksShelfLife) {
            if (mfgDt == null) {
                throw new IllegalArgumentException("제조일자는 필수입니다: " + sku.getSkuCd());
            }
            if (mfgDt.isAfter(receiptDt)) {
                throw new IllegalArgumentException("제조일자가 입고일자보다 미래일 수 없습니다: " + sku.getSkuCd());
            }
        }
        LocalDate effectiveMfgDt = tracksShelfLife ? mfgDt : null;

        // SKU 로우 락: 동시 검수로 같은 SKU의 "재사용 조회 → 건수 세기 → 채번"이 겹치지 않게 직렬화
        skuRepository.findByIdForUpdate(sku.getId());

        return lotRepository.findBySkuIdAndReceiptDtAndMfgDt(sku.getId(), receiptDt, effectiveMfgDt)
                .orElseGet(() -> {
                    long seq = lotRepository.countBySkuIdAndReceiptDt(sku.getId(), receiptDt) + 1;
                    String lotNo = String.format("LOT-%s-%03d",
                            receiptDt.format(DateTimeFormatter.ofPattern("yyMMdd")), seq);
                    LocalDate expiryDt = tracksShelfLife ? effectiveMfgDt.plusDays(sku.getShelfLifeDays()) : null;
                    return lotRepository.save(Lot.builder()
                            .sku(sku)
                            .lotNo(lotNo)
                            .receiptDt(receiptDt)
                            .mfgDt(effectiveMfgDt)
                            .expiryDt(expiryDt)
                            .build());
                });
    }

    /** 입고 마감 — 상태 검증/전이는 엔티티가 한다. 잔량(예정-검수)은 미입고로 확정 */
    @Transactional
    public void close(Long ibOrderId) {
        IbOrder order = ibOrderRepository.findById(ibOrderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고예정입니다: " + ibOrderId));
        order.close();
    }

    /** 특정 라인의 검수 이력(RECEIVE 건) 목록. 최근 순 — 검수 취소 대상 선택용. 이미 취소된 건은 cancelled=true로 표시 */
    public List<ReceiptResponse> receipts(Long ibOrderId, Long ibLineId) {
        IbLine ibLine = ibLineRepository.findById(ibLineId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고 라인입니다: " + ibLineId));
        if (!ibLine.getIbOrder().getId().equals(ibOrderId)) {
            throw new IllegalArgumentException("다른 입고의 라인입니다: " + ibLineId);
        }
        List<InvHist> receiveRows = invHistRepository.findAllByIbLineIdAndTxTypeOrderByCreatedAtDesc(ibLineId, TxType.RECEIVE);
        Set<Long> cancelledIds = invHistRepository.findAllByIbLineIdAndTxTypeOrderByCreatedAtDesc(ibLineId, TxType.ADJUST)
                .stream()
                .map(InvHist::getCancelsInvHistId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return receiveRows.stream()
                .map(r -> ReceiptResponse.from(r, cancelledIds.contains(r.getId())))
                .toList();
    }

    /**
     * 검수 취소. 검수 건 하나(inv_hist RECEIVE 1건)를 되돌린다.
     * 원 이력은 그대로 두고 ADJUST(-수량)를 추가한다 (append-only 원장 원칙).
     */
    @Transactional
    public void cancelReceipt(Long ibOrderId, Long invHistId) {
        InvHist receipt = invHistRepository.findById(invHistId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 검수 이력입니다: " + invHistId));
        if (receipt.getTxType() != TxType.RECEIVE) {
            throw new IllegalArgumentException("검수 이력이 아닙니다: " + invHistId);
        }
        boolean alreadyCancelled = invHistRepository.findAllByIbLineIdAndTxTypeOrderByCreatedAtDesc(receipt.getIbLineId(), TxType.ADJUST)
                .stream()
                .anyMatch(a -> invHistId.equals(a.getCancelsInvHistId()));
        if (alreadyCancelled) {
            throw new IllegalStateException("이미 취소된 검수 이력입니다: " + invHistId);
        }
        IbLine ibLine = ibLineRepository.findById(receipt.getIbLineId())
                .orElseThrow(() -> new IllegalStateException("검수 이력의 입고 라인을 찾을 수 없습니다: " + invHistId));
        IbOrder order = ibLine.getIbOrder();
        if (!order.getId().equals(ibOrderId)) {
            throw new IllegalArgumentException("다른 입고의 검수 이력입니다: " + invHistId);
        }
        // 전량 검수 시 자동으로 RECEIVED로 전이하므로, 적치 전이면 RECEIVED까지도 취소를 허용한다.
        // COMPLETED는 막아야 함 — 같은 Lot을 공유하는 다른 주문이 있으면 이 주문이 끝났어도
        // 스테이징 잔량이 남아있을 수 있어, 잔량 체크만으로는 완료된 주문의 수량이 되돌아갈 수 있다.
        if (order.getStatus() != IbStatus.RECEIVING && order.getStatus() != IbStatus.RECEIVED) {
            throw new IllegalStateException("적치가 완료되지 않은 입고만 검수를 취소할 수 있습니다 (" + order.getStatus().getLabel() + "): " + order.getIbNo());
        }

        Sku sku = receipt.getSku();
        long qty = receipt.getQty();
        Inv inv = invRepository.findBySkuIdAndLocIdAndLotId(sku.getId(), receipt.getLoc().getId(), receipt.getLot().getId())
                .orElseThrow(() -> new IllegalStateException("스테이징 재고를 찾을 수 없습니다: " + sku.getSkuCd()));
        if (inv.getOnHandQty() < qty) {
            throw new IllegalStateException("이미 적치된 수량이 있어 검수를 취소할 수 없습니다: " + sku.getSkuCd());
        }

        inv.decreaseOnHand(qty);
        ibLine.cancelReceive(qty);
        invHistRepository.save(InvHist.builder()
                .txType(TxType.ADJUST)
                .sku(sku).loc(receipt.getLoc()).lot(receipt.getLot())
                .qty(-qty)
                .refDocType(RefDocType.INBOUND)
                .refDocNo(order.getIbNo())
                .ibLineId(ibLine.getId())
                .cancelsInvHistId(receipt.getId())
                .build());
        // 스테이징 재고가 0이 되면 스냅샷 행을 삭제한다 (재고 테이블엔 실물이 있는 행만 남긴다)
        if (inv.getOnHandQty() == 0 && inv.getAllocQty() == 0) {
            invRepository.delete(inv);
        }
        order.reopenIfNoLongerFullyReceived(); // 전량검수로 자동 마감됐던 게 이 취소로 깨졌으면 RECEIVING으로 되돌림
    }
}
