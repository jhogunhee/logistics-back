package com.project.wmsback.inbound.service;

import com.project.wmsback.inbound.dto.PutawayBulkExecuteRequest;
import com.project.wmsback.inbound.dto.PutawayLocCandidateResponse;
import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.PutawayTask;
import com.project.wmsback.inbound.entity.PutawayTaskStatus;
import com.project.wmsback.inbound.repository.IbLineRepository;
import com.project.wmsback.inbound.repository.PutawayTaskRepository;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvHist;
import com.project.wmsback.inventory.entity.RefDocTyp;
import com.project.wmsback.inventory.entity.TxTyp;
import com.project.wmsback.inventory.repository.InvHistRepository;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.inventory.service.LocCapacityService;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.warehouse.repository.LocRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 적치 실행 — 발행된 적치지시를 실물 MOVE로 소진한다 (스테이징 → 지시받은 보관 로케이션).
 * <p>
 * 지시가 이미 (라인, Lot, 로케이션, 수량)을 확정해 놨으므로 실행은 수량만 받는다(부분 실행 허용).
 * 지시는 권고가 아니라 명령이라 다른 로케이션으로는 실행할 수 없다 — 다른 곳에 두려면 취소 후 재지시한다.
 * 한 트랜잭션에서 예약 소진 · 스냅샷 갱신 · 이력 2행 · 지시 누계를 함께 처리한다
 * (불변식: inv_hist 합계 = inv 스냅샷).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PutawayService {

    /** 적치 출발지. ReceivingService/PutawayTaskService/IbLineRepositoryImpl의 상수와 동일 */
    private static final String STAGING_LOC_CD = "RCV-STAGE";

    private final PutawayTaskRepository putawayTaskRepository;
    private final IbLineRepository ibLineRepository;
    private final LocRepository locRepository;
    private final InvRepository invRepository;
    private final InvHistRepository invHistRepository;
    private final LocCapacityService locCapacityService;

    /**
     * 수동 지시용 로케이션 후보 — 상품 온도대와 일치하는 보관 로케이션을 피킹순위 순으로.
     * 전략 추천과 달리 조건·정렬 전략을 타지 않는 단순 목록이고, 적재가능수량만 함께 붙여준다.
     */
    public List<PutawayLocCandidateResponse> candidateLocs(Long ibLineId) {
        IbLine ibLine = ibLineRepository.findById(ibLineId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고 라인입니다: " + ibLineId));
        return locRepository
                .findAllByTmpZonAndLocTypOrderByPikngPrtyAsc(ibLine.getProd().getTmpZon(), LocTyp.STORAGE)
                .stream()
                .map(loc -> PutawayLocCandidateResponse.of(loc, locCapacityService.availCapacity(loc)))
                .toList();
    }

    /** 적치 실행 (부분 허용). 지시받은 로케이션으로만 옮긴다 */
    @Transactional
    public void execute(Long taskId, Long qty) {
        executeOne(taskId, qty);
    }

    /**
     * 일괄 실행 — 한 상품의 지시 여러 건을 한 트랜잭션으로 소진한다.
     * 한 건이라도 실패하면 전량 롤백이라 "몇 건까지 반영됐는지" 같은 애매한 상태가 남지 않는다.
     */
    @Transactional
    public void executeAll(PutawayBulkExecuteRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("실행할 적치지시가 없습니다.");
        }
        for (PutawayBulkExecuteRequest.Item item : request.getItems()) {
            executeOne(item.getTaskId(), item.getQty());
        }
    }

    private void executeOne(Long taskId, Long qty) {
        if (qty == null || qty < 1) {
            throw new IllegalArgumentException("적치수량은 1 이상이어야 합니다.");
        }
        PutawayTask task = putawayTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 적치지시입니다: " + taskId));
        if (task.getStatus() != PutawayTaskStatus.DIRECTED) {
            throw new IllegalArgumentException("지시 상태의 적치지시만 실행할 수 있습니다 (현재 "
                    + task.getStatus().getLabel() + "): " + taskId);
        }
        if (qty > task.remainingQty()) {
            throw new IllegalArgumentException("적치수량이 잔여수량을 초과했습니다 (잔여 " + task.remainingQty() + "): " + taskId);
        }

        IbLine ibLine = task.getIbLine();
        Prod prod = ibLine.getProd();
        Lot lot = task.getLot();
        Loc target = task.getToLoc();
        Loc staging = locRepository.findByLocCd(STAGING_LOC_CD)
                .orElseThrow(() -> new IllegalStateException("입고 스테이징 로케이션(RCV-STAGE)이 없습니다."));

        // 지시 발행 이후 로케이션 마스터가 바뀌었을 수 있다 — 어긋났으면 취소 후 재지시가 정답이라 여기서 막는다
        if (target.getLocTyp() != LocTyp.STORAGE || target.getTmpZon() != prod.getTmpZon()) {
            throw new IllegalStateException("지시받은 로케이션이 적치 조건과 어긋납니다 (취소 후 재지시 필요): " + target.getLocCd());
        }

        Inv stagingInv = invRepository.findByKeyForUpdate(prod.getId(), staging.getId(), lot.getId())
                .orElseThrow(() -> new IllegalStateException("적치지시가 예약한 재고가 없습니다 (정합성 오류): " + taskId));
        if (stagingInv.getOnHandQty() < qty || stagingInv.getAlocQty() < qty) {
            throw new IllegalStateException("예약 수량보다 실재고가 적습니다 (정합성 오류 — 보유 " + stagingInv.getOnHandQty()
                    + " / 예약 " + stagingInv.getAlocQty() + "): " + taskId);
        }

        // 실물 이동 + 예약 소진 (이동확정과 같은 패턴)
        stagingInv.decreaseOnHand(qty);
        stagingInv.release(qty);
        Inv targetInv = invRepository.findByProdIdAndLocIdAndLotId(prod.getId(), target.getId(), lot.getId())
                .orElseGet(() -> invRepository.save(Inv.builder().prod(prod).loc(target).lot(lot).build()));
        targetInv.increaseOnHand(qty);

        // MOVE는 이력 2행 — 두 행 모두 같은 from/to를 가져 한 행만 봐도 이동 전체를 알 수 있다
        String ibNo = ibLine.getIbOrder().getIbNo();
        invHistRepository.save(InvHist.builder()
                .txTyp(TxTyp.MOVE)
                .prod(prod).loc(staging).lot(lot)
                .qty(-qty)
                .rfnDocTyp(RefDocTyp.INBOUND)
                .rfnDocNo(ibNo)
                .ibLineId(ibLine.getId())
                .fromLocId(staging.getId()).toLocId(target.getId())
                .build());
        invHistRepository.save(InvHist.builder()
                .txTyp(TxTyp.MOVE)
                .prod(prod).loc(target).lot(lot)
                .qty(qty)
                .rfnDocTyp(RefDocTyp.INBOUND)
                .rfnDocNo(ibNo)
                .ibLineId(ibLine.getId())
                .fromLocId(staging.getId()).toLocId(target.getId())
                .build());

        // 재고수량이 0(보유·예약·보류 모두 0)이 된 스냅샷 행은 삭제한다 — 실물이 있는 행만 남긴다.
        // 이력 합계=스냅샷 불변식은 유지된다: 이력 SUM=0 ↔ 행 없음
        if (stagingInv.getOnHandQty() == 0 && stagingInv.getAlocQty() == 0 && stagingInv.getHldQty() == 0) {
            invRepository.delete(stagingInv);
        }

        task.execute(qty);
        ibLine.putaway(qty);
        ibLine.getIbOrder().checkAndComplete();
    }
}
