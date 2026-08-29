package com.project.wmsback.inbound.service;

import com.project.wmsback.inbound.dto.PutawayCandidateResponse;
import com.project.wmsback.inbound.dto.PutawaySearchCond;
import com.project.wmsback.inbound.dto.PutawayTaskCreateRequest;
import com.project.wmsback.inbound.dto.PutawayTaskResponse;
import com.project.wmsback.inbound.dto.PutawayTaskSearchCond;
import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.IbStatus;
import com.project.wmsback.inbound.entity.PutawayTask;
import com.project.wmsback.inbound.entity.PutawayTaskStatus;
import com.project.wmsback.inbound.repository.IbLineRepository;
import com.project.wmsback.inbound.repository.PutawayTaskQueryRepository;
import com.project.wmsback.inbound.repository.PutawayTaskRepository;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.service.InvKey;
import com.project.wmsback.inventory.service.InvStore;
import com.project.wmsback.inventory.service.LocCapacityService;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.wmsback.warehouse.repository.LocRepository;
import com.project.wmsback.warehouse.repository.LotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 적치지시 생성·취소와 지시 대기 목록.
 * <p>
 * 지시 생성은 스테이징 재고를 aloc로 예약한다. <b>예약이 막는 것은 검수 취소 하나</b>다 —
 * 지시받은 물량을 검수 취소가 빼가면 스테이징은 0인데 지시는 남는 유령이 된다.
 * 중복 지시는 아래 ①(배치 상한)이, 로케이션 이중 배정은 유입 잔량이 막는다 — 셋은 서로 대체하지 못한다.
 * 실행(실물 MOVE)은 {@link PutawayService}가 맡아 예약을 소진한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PutawayTaskService {

    /** 적치 출발지. ReceivingService/PutawayService/IbLineRepositoryImpl의 상수와 동일 */
    private static final String STAGING_LOC_CD = "RCV-STAGE";

    private final PutawayTaskRepository putawayTaskRepository;
    private final PutawayTaskQueryRepository putawayTaskQueryRepository;
    private final IbLineRepository ibLineRepository;
    private final LotRepository lotRepository;
    private final LocRepository locRepository;
    private final ProdRepository prodRepository;
    private final InvStore invStore;
    private final LocCapacityService locCapacityService;

    /**
     * 적치 대기 배치 목록 (지시 등록 화면). 스테이징 잔량에 미완료 지시 잔량을 붙여
     * 「아직 지시하지 않은 수량」까지 계산해 내려준다 — 이미 전량 지시된 배치도 목록에는 남는다
     * (진행 중인 배치가 화면에서 사라지면 무슨 일이 있었는지 알 수 없다).
     */
    public List<PutawayCandidateResponse> candidates(PutawaySearchCond cond) {
        List<PutawayCandidateResponse> batches = ibLineRepository.findAllPendingPutawayBatches(cond);
        Map<String, Long> directedByBatch = putawayTaskQueryRepository.openRemainderByBatch();
        for (PutawayCandidateResponse batch : batches) {
            batch.applyDirectedQty(directedByBatch.getOrDefault(
                    PutawayTaskQueryRepository.batchKey(batch.getIbLineId(), batch.getLotId()), 0L));
        }
        return batches;
    }

    public List<PutawayTaskResponse> list(PutawayTaskSearchCond cond) {
        return putawayTaskQueryRepository.search(cond);
    }

    /**
     * 적치지시 생성 (예약). 전체가 한 트랜잭션 — 한 건이라도 검증에 걸리면 전량 롤백.
     * 추천 시점과 생성 시점 사이의 재고·용량 변동을 여기서 같은 식으로 다시 검증한다.
     *
     * @return 생성된 지시 id 목록
     */
    @Transactional
    public List<Long> create(PutawayTaskCreateRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("적치지시 대상이 없습니다.");
        }
        Loc staging = locRepository.findByLocCd(STAGING_LOC_CD)
                .orElseThrow(() -> new IllegalStateException("입고 스테이징 로케이션(RCV-STAGE)이 없습니다."));

        List<Long> taskIds = new ArrayList<>();
        for (PutawayTaskCreateRequest.Item item : request.getItems()) {
            taskIds.addAll(createForBatch(item, staging));
        }
        return taskIds;
    }

    private List<Long> createForBatch(PutawayTaskCreateRequest.Item item, Loc staging) {
        if (item.getAssignments() == null || item.getAssignments().isEmpty()) {
            throw new IllegalArgumentException("배정된 로케이션이 없습니다: 입고 라인 " + item.getIbLineId());
        }
        IbLine ibLine = ibLineRepository.findById(item.getIbLineId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고 라인입니다: " + item.getIbLineId()));
        // 확정된 입고는 닫힌 문서다. 수량 상한(미지시 잔량 0)으로도 어차피 거부되지만,
        // 그때 메시지(「미지시 잔량 초과」)가 원인을 오도해서 여기서 먼저 막는다
        if (ibLine.getIbOrder().getStatus() == IbStatus.CONFIRMED) {
            throw new IllegalStateException("확정된 입고에는 적치지시를 만들 수 없습니다: " + ibLine.getIbOrder().getIbNo());
        }
        Lot lot = lotRepository.findById(item.getLotId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 Lot입니다: " + item.getLotId()));
        Prod prod = ibLine.getProd();

        List<Long> taskIds = new ArrayList<>();
        for (PutawayTaskCreateRequest.Assignment assignment : item.getAssignments()) {
            taskIds.add(createOne(ibLine, lot, prod, staging, assignment));
        }
        return taskIds;
    }

    private Long createOne(IbLine ibLine, Lot lot, Prod prod, Loc staging,
                           PutawayTaskCreateRequest.Assignment assignment) {
        long qty = assignment.getQty() != null ? assignment.getQty() : 0;
        if (qty < 1) {
            throw new IllegalArgumentException("지시수량은 1 이상이어야 합니다: " + prod.getProdCd());
        }
        Loc toLoc = locRepository.findById(assignment.getLocId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로케이션입니다: " + assignment.getLocId()));
        validateToLoc(toLoc, prod);

        // ① 배치 상한 — 이 (입고라인, Lot)이 스테이징에 남긴 잔량에서 이미 지시한 몫을 뺀 만큼만 지시할 수 있다.
        //    ②의 재고 예약만으로는 같은 Lot을 공유하는 다른 입고라인의 물량까지 끌어다 지시하게 된다
        long stagingQty = putawayTaskQueryRepository.stagingQtyOfBatch(ibLine.getId(), lot.getId(), STAGING_LOC_CD);
        long directedQty = putawayTaskQueryRepository.openQtyOfBatch(ibLine.getId(), lot.getId());
        long unDirected = stagingQty - directedQty;
        if (qty > unDirected) {
            throw new IllegalArgumentException("지시수량이 미지시 잔량을 초과했습니다 (미지시 " + Math.max(unDirected, 0)
                    + "): " + prod.getProdCd() + " / " + lot.getLotNo());
        }

        // ② 스테이징 재고 예약 — 행 락으로 예약 증감을 직렬화한다 (이동지시 등록과 같은 지점).
        //    물리 이동이 아니므로 inv_hist는 남기지 않는다. ck_inv_qty(aloc+hld<=onHand)가 최후 방어선
        Inv stagingInv = invStore.lock(new InvKey(prod.getId(), staging.getId(), lot.getId()))
                .orElseThrow(() -> new IllegalArgumentException("스테이징 재고가 없습니다: "
                        + prod.getProdCd() + " / " + lot.getLotNo()));
        if (qty > stagingInv.avalQty()) {
            throw new IllegalArgumentException("지시수량이 스테이징 가용재고를 초과했습니다 (가용 " + stagingInv.avalQty()
                    + "): " + prod.getProdCd() + " / " + lot.getLotNo());
        }

        // ③ 적재가능수량 재검증 (추천 때와 같은 식 — 2회 검증). 같은 트랜잭션에서 앞서 만든 지시는
        //    JPQL 실행 전 auto-flush로 합산에 잡힌다
        Long capacity = locCapacityService.availCapacity(toLoc);
        if (capacity != null && qty > capacity) {
            throw new IllegalArgumentException("적재가능수량을 초과했습니다 (적재가능 " + capacity + "): " + toLoc.getLocCd());
        }

        invStore.reserve(stagingInv, qty);
        PutawayTask task = putawayTaskRepository.save(PutawayTask.builder()
                .ibLine(ibLine)
                .lot(lot)
                .toLoc(toLoc)
                .drctQty(qty)
                .build());
        return task.getId();
    }

    /**
     * 적치지시 로케이션 변경·분할 — 지시받은 자리에 실물을 못 넣을 때 취소 후 재지시를 대신한다.
     * 잔여 전량(미실행)이면 목적지만 바꾸고, 일부면 그만큼 새 지시로 떼어낸다(분할).
     * 분할로 원 지시에 실행분만 남으면 완료로 전이하므로, 부분 실행된 지시의 잔여분도 이 창구로 빠져나간다.
     * 예약은 스테이징 재고에 (상품, 로케이션, Lot) 단위로 걸려 있어 분할해도 총량이 그대로다 — 재고 무접촉.
     * 목적지 검증은 생성 때와 같은 식이되, 적재가능수량은 옮기는 수량 기준이다.
     *
     * <p>재고를 건드리지 않아도 <b>상품 락은 잡는다</b> — 잔여수량 판정과 분할이 실행({@link PutawayService})과
     * 같은 지시 행을 읽고 쓰기 때문이다. 락 없이 먼저 읽으면 그 사이에 커밋된 실행분을 못 본 채 잔여를
     * 계산하고, 전체 컬럼을 쓰는 UPDATE가 방금 오른 {@code cmpl_qty}를 되돌린다. 실행과 같은 순서
     * (상품 → 지시)라 교착도 없다.
     */
    @Transactional
    public Long changeLoc(Long taskId, Long locId, Long qty) {
        lockProdOf(taskId);
        // 지시는 락을 잡은 뒤에 읽는다 — 먼저 읽으면 뒤에 거는 락이 낡은 인스턴스를 그대로 돌려준다
        PutawayTask task = putawayTaskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 적치지시입니다: " + taskId));
        if (task.getStatus() != PutawayTaskStatus.DIRECTED) {
            throw new IllegalArgumentException("지시 상태의 적치지시만 로케이션을 변경할 수 있습니다 (현재 "
                    + task.getStatus().getLabel() + "): " + taskId);
        }
        long moveQty = qty != null ? qty : task.remainingQty();
        if (moveQty < 1) {
            throw new IllegalArgumentException("변경 수량은 1 이상이어야 합니다: " + taskId);
        }
        if (moveQty > task.remainingQty()) {
            throw new IllegalArgumentException("변경 수량이 잔여수량을 초과했습니다 (잔여 "
                    + task.remainingQty() + "): " + taskId);
        }
        Loc toLoc = locRepository.findById(locId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로케이션입니다: " + locId));
        if (toLoc.getId().equals(task.getToLoc().getId())) {
            throw new IllegalArgumentException("현재 지시 로케이션과 같습니다: " + toLoc.getLocCd());
        }
        validateToLoc(toLoc, task.getIbLine().getProd());
        Long capacity = locCapacityService.availCapacity(toLoc);
        if (capacity != null && moveQty > capacity) {
            throw new IllegalArgumentException("적재가능수량을 초과했습니다 (적재가능 " + capacity + "): " + toLoc.getLocCd());
        }

        // 전량·미실행이면 지시 이동, 아니면 분할 — moveQty == drctQty ⟺ 실행분 0 + 잔여 전량.
        // 옮긴 수량이 실린 지시의 id를 돌려준다 — 화면이 저장 시점에 변경과 실행을 이어 부르는 데 쓴다
        if (moveQty == task.getDrctQty()) {
            task.changeToLoc(toLoc);
            return task.getId();
        }
        task.split(moveQty);
        return putawayTaskRepository.save(PutawayTask.builder()
                .ibLine(task.getIbLine())
                .lot(task.getLot())
                .toLoc(toLoc)
                .drctQty(moveQty)
                .build()).getId();
    }

    /**
     * 지시가 걸린 상품 행을 잠그고 그 지시의 재고 키를 돌려준다 — 변경·취소가 실행과 공유하는 첫 락이다.
     * 키를 <b>스칼라 조회</b>로 읽는 이유는 실행 쪽과 같다: 지시를 엔티티로 먼저 읽으면 영속성 컨텍스트에
     * 올라가 뒤에 거는 락이 낡은 인스턴스를 돌려준다 ({@link PutawayLockKey}).
     */
    private PutawayLockKey lockProdOf(Long taskId) {
        PutawayLockKey key = putawayTaskRepository.findLockKeysByIdIn(List.of(taskId)).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 적치지시입니다: " + taskId));
        prodRepository.findByIdForUpdate(key.prodId());
        return key;
    }

    /** 목적지 검증 (생성·변경 공용) — 보관 로케이션 + 반품존 제외 + 상품 온도대 일치 */
    private void validateToLoc(Loc toLoc, Prod prod) {
        if (toLoc.getLocTyp() != LocTyp.STORAGE) {
            throw new IllegalArgumentException("보관 로케이션으로만 적치할 수 있습니다: " + toLoc.getLocCd());
        }
        // 후보 산출(PutawayService.candidateLocs · PutawayQueryRepository)은 반품존을 이미 빼는데
        // 여기만 안 봐서 수동 지시·지시 변경으로는 들어갈 수 있었다 — 반품존은 검수만 넣는 자리다
        if (RtngsLocResolver.inRtngsZon(toLoc)) {
            throw new IllegalArgumentException("반품존에는 적치할 수 없습니다: " + toLoc.getLocCd());
        }
        if (toLoc.getTmpZon() != prod.getTmpZon()) {
            throw new IllegalArgumentException("온도대가 일치하지 않습니다 (상품 " + prod.getTmpZon()
                    + " / 로케이션 " + toLoc.getTmpZon() + "): " + toLoc.getLocCd());
        }
    }

    /**
     * 적치지시 취소 — 예약 해제. 물리 이동이 없으므로 inv_hist 기록도 없다.
     * 실행 실적이 있는 지시는 취소하지 않는다 (이미 옮긴 실물은 재고이동 화면의 소관).
     *
     * <p>락 순서는 실행과 같다 — 상품 → 스테이징 재고 → 지시. 지시를 락보다 먼저 읽으면
     * 그 사이에 커밋된 실행분을 못 본 「완료 0」으로 취소 가드를 통과해, 이미 소진된 예약을
     * 다시 풀고 전체 컬럼 UPDATE가 {@code cmpl_qty}를 0으로 되돌린다.
     */
    @Transactional
    public void cancel(Long taskId) {
        PutawayLockKey lockKey = lockProdOf(taskId);
        Loc staging = locRepository.findByLocCd(STAGING_LOC_CD)
                .orElseThrow(() -> new IllegalStateException("입고 스테이징 로케이션(RCV-STAGE)이 없습니다."));
        // 없는 행을 여기서 문제 삼지 않는다 — 상태 가드가 「지시 상태의 지시만 취소할 수 있다」로 먼저 잡아야 할 몫이다
        Optional<Inv> lockedStaging = invStore.lock(lockKey.stagingKey(staging.getId()));

        PutawayTask task = putawayTaskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 적치지시입니다: " + taskId));
        if (task.getStatus() != PutawayTaskStatus.DIRECTED) {
            throw new IllegalArgumentException("지시 상태의 적치지시만 취소할 수 있습니다 (현재 "
                    + task.getStatus().getLabel() + "): " + taskId);
        }
        if (task.getCmplQty() != 0L) {
            throw new IllegalArgumentException("이미 적치된 수량이 있어 취소할 수 없습니다 (완료 " + task.getCmplQty()
                    + ") — 되돌리려면 재고이동으로 처리합니다: " + taskId);
        }

        Inv stagingInv = lockedStaging
                .orElseThrow(() -> new IllegalStateException("적치지시가 예약한 재고가 없습니다 (정합성 오류): " + taskId));
        if (stagingInv.getAlocQty() < task.getDrctQty()) {
            throw new IllegalStateException("예약 잔량보다 재고의 예약 수량이 적습니다 (정합성 오류 — 예약 "
                    + stagingInv.getAlocQty() + " / 지시 " + task.getDrctQty() + "): " + taskId);
        }

        invStore.release(stagingInv, task.getDrctQty());
        task.cancel();
    }
}
