package com.project.wmsback.inventory.service;

import com.project.wmsback.inventory.dto.InvMovRegisterRequest;
import com.project.wmsback.inventory.dto.InvMovTaskResponse;
import com.project.wmsback.inventory.dto.InvMovTaskSearchCond;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvHist;
import com.project.wmsback.inventory.entity.InvMovDvsn;
import com.project.wmsback.inventory.entity.InvMovStatus;
import com.project.wmsback.inventory.entity.InvMovTask;
import com.project.wmsback.inventory.entity.RefDocType;
import com.project.wmsback.inventory.entity.TxType;
import com.project.wmsback.inventory.repository.InvHistRepository;
import com.project.wmsback.inventory.repository.InvMovTaskRepository;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocType;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.warehouse.repository.LocRepository;
import com.project.mdm.nbr.service.NbrService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 재고 이동지시 (보관 ↔ 보관 2단계: 지시=예약 → 확정=실물 MOVE).
 *
 * 등록이 FROM 재고의 aloc를 선점해 출고 할당(FEFO)과의 경합을 원천 차단하고,
 * 확정이 inv_hist MOVE 2행을 남기며 예약을 소진한다 (피킹이 aloc를 소진하는 것과 같은 패턴).
 * 실적 테이블은 없다 — 분할확정 실적은 inv_hist에 확정 횟수만큼 쌓인다 (rfn_doc_no = 지시번호).
 * 적치지시가 선점을 두지 않는 것과 다른 이유: 적치 대상(스테이징)은 할당 후보가 아니지만
 * 이동 FROM(보관 재고)은 할당 후보 그 자체다. docs/sg/wms-st-화면프로세스정의서.md 4절 참고.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvMovService {

    private static final String MOV_NO_RULE_CD = "INV_MOV_NO";

    private final InvRepository invRepository;
    private final InvHistRepository invHistRepository;
    private final InvMovTaskRepository invMovTaskRepository;
    private final LocRepository locRepository;
    private final NbrService nbrService;

    public List<InvMovTaskResponse> list(InvMovTaskSearchCond cond) {
        return invMovTaskRepository.search(cond);
    }

    /**
     * 이동지시 등록 (예약). 전체가 한 트랜잭션 — 한 건이라도 검증에 걸리면 전량 롤백.
     * @return 발급된 이동지시 번호 목록
     */
    @Transactional
    public List<String> register(InvMovRegisterRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("이동지시 대상이 없습니다.");
        }
        List<String> movNos = new ArrayList<>();
        for (InvMovRegisterRequest.Item item : request.getItems()) {
            movNos.add(registerOne(item));
        }
        return movNos;
    }

    private String registerOne(InvMovRegisterRequest.Item item) {
        if (item.getQty() == null || item.getQty() < 1) {
            throw new IllegalArgumentException("이동수량은 1 이상이어야 합니다.");
        }
        // FROM 재고 행 락 — 예약(aloc) 증감의 직렬화 지점 (출고 할당이 같은 행을 잡는 지점과 동일)
        Inv fromInv = invRepository.findByIdForUpdate(item.getInvId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 재고입니다: " + item.getInvId()));
        Prod prodEntity = fromInv.getProd();
        Lot lotEntity = fromInv.getLot();
        Loc from = fromInv.getLoc();

        if (from.getLocTyp() != LocType.STORAGE) {
            throw new IllegalArgumentException("보관 로케이션의 재고만 이동할 수 있습니다 (스테이징 재고는 적치·출고확정의 소관): " + from.getLocCd());
        }
        Loc to = locRepository.findById(item.getToLocId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로케이션입니다: " + item.getToLocId()));
        if (to.getId().equals(from.getId())) {
            throw new IllegalArgumentException("출발지와 도착지가 같습니다: " + from.getLocCd());
        }
        if (to.getLocTyp() != LocType.STORAGE) {
            throw new IllegalArgumentException("보관 로케이션으로만 이동할 수 있습니다: " + to.getLocCd());
        }
        if (to.getTmpZon() != prodEntity.getTmpZon()) {
            throw new IllegalArgumentException("온도대가 일치하지 않습니다 (상품 " + prodEntity.getTmpZon()
                    + " / 로케이션 " + to.getTmpZon() + "): " + to.getLocCd());
        }
        if (item.getQty() > fromInv.availableQty()) {
            throw new IllegalArgumentException("이동수량이 가용재고를 초과했습니다 (가용 " + fromInv.availableQty() + "): "
                    + prodEntity.getProdCd() + " @ " + from.getLocCd());
        }
        // 적재가능수량 = max_qty − 현재고 − 미완료 이동지시 유입 잔량. STORAGE는 max_qty NOT NULL이
        // DB 강제이지만(ck_loc_storage_capacity) 강제 이전의 옛 행일 수 있어 null이면 무제한으로 본다.
        // TODO 적치지시(putaway_task) 구현 시 그 미완료 잔량도 같은 항에 합산해야 한다 (양쪽 계산식 공통화).
        if (to.getMaxQty() != null) {
            long capacity = to.getMaxQty()
                    - invRepository.sumOnHandQtyByLocId(to.getId())
                    - invMovTaskRepository.sumOpenInboundQty(to.getId(), InvMovStatus.DIRECTED);
            if (item.getQty() > capacity) {
                throw new IllegalArgumentException("도착 로케이션의 적재가능수량을 초과했습니다 (적재가능 " + Math.max(capacity, 0)
                        + "): " + to.getLocCd());
            }
        }

        fromInv.reserve(item.getQty());
        InvMovTask task = InvMovTask.builder()
                .invMovNo(nbrService.issue(MOV_NO_RULE_CD, LocalDate.now()))
                .movDvsn(InvMovDvsn.INV_MOV) // 이 화면 경로의 이동구분은 재고이동 고정 (레거시 '01' 고정과 동일)
                .prod(prodEntity).lot(lotEntity)
                .fromLoc(from).toLoc(to)
                .drctQty(item.getQty())
                .build();
        invMovTaskRepository.save(task);
        return task.getInvMovNo();
    }

    /**
     * 이동확정 (실물 MOVE, 부분확정 허용). 지시 TO와 다른 로케이션으로는 확정할 수 없다 —
     * 지시는 권고가 아니라 명령이며, 다른 곳에 두려면 잔량 취소 후 재지시한다.
     */
    @Transactional
    public void confirm(Long taskId, Long qty) {
        if (qty == null || qty < 1) {
            throw new IllegalArgumentException("확정수량은 1 이상이어야 합니다.");
        }
        InvMovTask task = invMovTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이동지시입니다: " + taskId));
        // 재고이동 유형만 이 경로에서 확정 가능 — 적치·피킹 지시는 각자의 경로 전용 (레거시의 입고적치 차단과 같은 방어)
        if (task.getMovDvsn() != InvMovDvsn.INV_MOV) {
            throw new IllegalArgumentException("재고이동 유형의 지시만 이 화면에서 확정할 수 있습니다 (이동구분 " + task.getMovDvsn().getLabel() + "): " + task.getInvMovNo());
        }
        if (task.getStatus() != InvMovStatus.DIRECTED) {
            throw new IllegalArgumentException("지시 상태의 이동지시만 확정할 수 있습니다 (현재 " + task.getStatus().getLabel() + "): " + task.getInvMovNo());
        }
        if (qty > task.remainingQty()) {
            throw new IllegalArgumentException("확정수량이 잔여수량을 초과했습니다 (잔여 " + task.remainingQty() + "): " + task.getInvMovNo());
        }

        Prod prodEntity = task.getProd();
        Lot lotEntity = task.getLot();
        Loc from = task.getFromLoc();
        Loc to = task.getToLoc();

        Inv fromInv = invRepository.findByKeyForUpdate(prodEntity.getId(), from.getId(), lotEntity.getId())
                .orElseThrow(() -> new IllegalStateException("이동지시가 예약한 재고가 없습니다 (정합성 오류): " + task.getInvMovNo()));
        if (fromInv.getOnHandQty() < qty || fromInv.getAlocQty() < qty) {
            throw new IllegalStateException("예약 수량보다 실재고가 적습니다 (정합성 오류 — 보유 " + fromInv.getOnHandQty()
                    + " / 예약 " + fromInv.getAlocQty() + "): " + task.getInvMovNo());
        }

        // 실물 이동 + 예약 소진 (피킹이 onHand와 aloc를 함께 줄이는 것과 같은 패턴)
        fromInv.decreaseOnHand(qty);
        fromInv.release(qty);
        Inv toInv = invRepository.findByProdIdAndLocIdAndLotId(prodEntity.getId(), to.getId(), lotEntity.getId())
                .orElseGet(() -> invRepository.save(Inv.builder().prod(prodEntity).loc(to).lot(lotEntity).build()));
        toInv.increaseOnHand(qty);

        invHistRepository.save(InvHist.builder()
                .txTyp(TxType.MOVE)
                .prod(prodEntity).loc(from).lot(lotEntity)
                .qty(-qty)
                .rfnDocTyp(RefDocType.INV_MOV)
                .rfnDocNo(task.getInvMovNo())
                .fromLocId(from.getId()).toLocId(to.getId())
                .build());
        invHistRepository.save(InvHist.builder()
                .txTyp(TxType.MOVE)
                .prod(prodEntity).loc(to).lot(lotEntity)
                .qty(qty)
                .rfnDocTyp(RefDocType.INV_MOV)
                .rfnDocNo(task.getInvMovNo())
                .fromLocId(from.getId()).toLocId(to.getId())
                .build());

        task.confirm(qty);

        // 재고수량이 0(보유·예약·보류 모두 0)이 된 스냅샷 행은 삭제한다 (PutawayService와 같은 관례 — 실물이 있는 행만 남긴다).
        // 이력 합계=스냅샷 불변식은 유지된다: 이력 SUM=0 ↔ 행 없음.
        if (fromInv.getOnHandQty() == 0 && fromInv.getAlocQty() == 0 && fromInv.getHldQty() == 0) {
            invRepository.delete(fromInv);
        }
    }

    /** 이동취소 (잔량 취소 — 예약 해제). 물리 이동이 없으므로 inv_hist 기록도 없다. */
    @Transactional
    public void cancel(Long taskId) {
        InvMovTask task = invMovTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이동지시입니다: " + taskId));
        // 재고이동 유형만 이 경로에서 취소 가능 (확정과 같은 방어)
        if (task.getMovDvsn() != InvMovDvsn.INV_MOV) {
            throw new IllegalArgumentException("재고이동 유형의 지시만 이 화면에서 취소할 수 있습니다 (이동구분 " + task.getMovDvsn().getLabel() + "): " + task.getInvMovNo());
        }
        if (task.getStatus() != InvMovStatus.DIRECTED) {
            throw new IllegalArgumentException("지시 상태의 이동지시만 취소할 수 있습니다 (현재 " + task.getStatus().getLabel() + "): " + task.getInvMovNo());
        }
        long remaining = task.remainingQty();

        Inv fromInv = invRepository.findByKeyForUpdate(task.getProd().getId(), task.getFromLoc().getId(), task.getLot().getId())
                .orElseThrow(() -> new IllegalStateException("이동지시가 예약한 재고가 없습니다 (정합성 오류): " + task.getInvMovNo()));
        if (fromInv.getAlocQty() < remaining) {
            throw new IllegalStateException("예약 잔량보다 재고의 예약 수량이 적습니다 (정합성 오류 — 예약 " + fromInv.getAlocQty()
                    + " / 잔여 " + remaining + "): " + task.getInvMovNo());
        }

        fromInv.release(remaining);
        task.cancelRemainder();
    }
}
