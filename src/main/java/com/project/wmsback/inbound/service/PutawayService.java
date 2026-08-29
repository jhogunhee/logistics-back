package com.project.wmsback.inbound.service;

import com.project.wmsback.inbound.dto.PutawayBulkExecuteRequest;
import com.project.wmsback.inbound.dto.PutawayLocCandidateResponse;
import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.PutawayTask;
import com.project.wmsback.inbound.entity.PutawayTaskStatus;
import com.project.wmsback.inbound.repository.IbLineRepository;
import com.project.wmsback.inbound.repository.PutawayTaskRepository;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.RefDocTyp;
import com.project.wmsback.inventory.service.InvDocRef;
import com.project.wmsback.inventory.service.InvKey;
import com.project.wmsback.inventory.service.InvStore;
import com.project.wmsback.inventory.service.LocCapacityService;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.wmsback.warehouse.repository.LocRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 적치 실행 — 발행된 적치지시를 실물 MOVE로 소진한다 (스테이징 → 지시받은 보관 로케이션).
 * <p>
 * 지시가 이미 (라인, Lot, 로케이션, 수량)을 확정해 놨으므로 실행은 수량만 받는다(부분 실행 허용).
 * 지시는 권고가 아니라 명령이라 다른 로케이션으로는 실행할 수 없다 — 다른 곳에 두려면
 * 지시의 로케이션을 먼저 변경한다 ({@link PutawayTaskService#changeLoc}).
 * 한 트랜잭션에서 예약 소진 · 스냅샷 갱신 · 이력 2행 · 지시 누계를 함께 처리한다
 * (불변식: inv_hist 합계 = inv 스냅샷).
 * <p>
 * 락은 <b>읽기보다 먼저</b>다 — 상품 → 재고 행 → 지시 순으로 잡고 그 뒤에 지시·라인을 읽는다
 * (아래 {@code lockRows} 참고). 누계 둘({@code cmpl_qty} · {@code ptawy_qty})을 낡은 스냅샷에서
 * 올리면 동시 실행의 절반이 사라진다.
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
    private final ProdRepository prodRepository;
    private final InvStore invStore;
    private final LocCapacityService locCapacityService;

    /**
     * 수동 지시용 로케이션 후보 — 상품 온도대와 일치하는 보관 로케이션을 피킹순위 순으로.
     * 전략 추천과 달리 조건·정렬 전략을 타지 않는 단순 목록이고, 적재가능수량만 함께 붙여준다.
     */
    public List<PutawayLocCandidateResponse> candidateLocs(Long ibLineId) {
        IbLine ibLine = ibLineRepository.findById(ibLineId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고 라인입니다: " + ibLineId));
        return locRepository
                .findAllByTmpZonAndLocTypOrderByPtawyPrtyAsc(ibLine.getProd().getTmpZon(), LocTyp.STORAGE)
                .stream()
                // 반품존은 적치 후보가 아니다 — RtngsLocResolver.inRtngsZon 참고
                .filter(loc -> !RtngsLocResolver.inRtngsZon(loc))
                .map(loc -> PutawayLocCandidateResponse.of(loc, locCapacityService.availCapacity(loc)))
                .toList();
    }

    /** 적치 실행 (부분 허용). 지시받은 로케이션으로만 옮긴다 */
    @Transactional
    public void execute(Long taskId, Long qty) {
        if (taskId == null) {
            throw new IllegalArgumentException("실행할 적치지시가 지정되지 않았습니다.");
        }
        executeOne(taskId, qty, lockRows(List.of(taskId)));
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
        List<Long> taskIds = new ArrayList<>();
        for (PutawayBulkExecuteRequest.Item item : request.getItems()) {
            if (item.getTaskId() == null) {
                throw new IllegalArgumentException("실행할 적치지시가 지정되지 않았습니다.");
            }
            taskIds.add(item.getTaskId());
        }
        LockedRows locked = lockRows(taskIds);
        // 지시 락은 id 오름차순으로 잡는다 — 요청 순서대로 잡으면 겹치는 두 일괄 실행이 서로 반대 순서가 된다
        request.getItems().stream()
                .sorted(Comparator.comparing(PutawayBulkExecuteRequest.Item::getTaskId))
                .forEach(item -> executeOne(item.getTaskId(), item.getQty(), locked));
    }

    /**
     * 실행이 건드리는 행을 <b>읽기 전에 전부 잠근다</b> — 상품 → 재고(스테이징 · 도착지) 순서로,
     * 지시 자체는 건별 처리가 id 오름차순으로 잡는다(전역 락 계층: prod → inv → 지시).
     *
     * <p><b>상품 락이 필요한 이유.</b> 실행은 재고뿐 아니라 {@code putaway_task.cmpl_qty} ·
     * {@code ib_line.ptawy_qty} 두 누계를 올리는데, 재고 행 락은 그 둘을 지켜주지 못한다 —
     * 같은 입고라인의 지시가 Lot마다 다른 스테이징 행을 잡으면 라인 누계가 락 없이 겹치고,
     * 같은 라인을 동시에 검수하는 트랜잭션은 아예 다른 행을 만진다. {@code ib_line}에는
     * {@code @Version}이 없어 둘이 각자 읽은 값에 더한 뒤 절대값으로 덮어써 한쪽이 증발한다
     * (실물은 옮겨졌는데 누계만 모자라 적치 잔여가 유령으로 남고 입고확정이 영구히 막힌다).
     * 검수가 같은 이유로 상품 행을 잠그므로({@code ReceivingService.lockProds}), 적치도 같은
     * 락을 지나야 검수와 직렬화된다.
     *
     * <p>그래서 잠글 키를 <b>스칼라 조회</b>로 고른다 — 지시를 엔티티로 미리 읽으면 영속성
     * 컨텍스트에 올라가 뒤에 거는 락이 낡은 인스턴스를 돌려줘 이 방어선이 그대로 무너진다.
     */
    private LockedRows lockRows(List<Long> taskIds) {
        Map<Long, PutawayLockKey> keyByTaskId = new HashMap<>();
        for (PutawayLockKey key : putawayTaskRepository.findLockKeysByIdIn(new LinkedHashSet<>(taskIds))) {
            keyByTaskId.put(key.taskId(), key);
        }
        for (Long taskId : taskIds) {
            if (!keyByTaskId.containsKey(taskId)) {
                throw new IllegalArgumentException("존재하지 않는 적치지시입니다: " + taskId);
            }
        }

        // 상품 락 (id 오름차순 — 요청 순서대로 잡으면 상품이 겹치는 두 실행이 서로 반대 순서가 된다)
        keyByTaskId.values().stream()
                .map(PutawayLockKey::prodId)
                .distinct()
                .sorted()
                .forEach(prodRepository::findByIdForUpdate);

        Loc staging = locRepository.findByLocCd(STAGING_LOC_CD)
                .orElseThrow(() -> new IllegalStateException("입고 스테이징 로케이션(RCV-STAGE)이 없습니다."));

        // 스테이징·도착 행을 함께 선락한다 (InvStore가 키 오름차순으로 잠근다). 락 없이 읽고 옮기면
        // 같은 스테이징 행의 동시 적치·검수취소가 각자 읽은 수량 기준으로 서로 덮어쓴다.
        // 도착 행이 아직 없으면 빠지고 move가 만든다 (동시 생성은 uq_inv가 방어)
        List<InvKey> invKeys = new ArrayList<>();
        for (Long taskId : taskIds) {
            PutawayLockKey key = keyByTaskId.get(taskId);
            invKeys.add(key.stagingKey(staging.getId()));
            invKeys.add(key.targetKey());
        }
        return new LockedRows(staging, keyByTaskId, invStore.lockAll(invKeys));
    }

    /** 선락 결과 — 잠근 재고 행과 그 키, 스테이징 로케이션 */
    private record LockedRows(Loc staging, Map<Long, PutawayLockKey> keyByTaskId, Map<InvKey, Inv> inv) {}

    private void executeOne(Long taskId, Long qty, LockedRows locked) {
        if (qty == null || qty < 1) {
            throw new IllegalArgumentException("적치수량은 1 이상이어야 합니다.");
        }
        PutawayLockKey lockKey = locked.keyByTaskId().get(taskId);
        // 락을 모두 잡은 뒤에 지시를 읽는다 — 이 락이 잔여 검증과 완료수량 누적을 직렬화한다
        PutawayTask task = putawayTaskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 적치지시입니다: " + taskId));
        if (task.getStatus() != PutawayTaskStatus.DIRECTED) {
            throw new IllegalArgumentException("지시 상태의 적치지시만 실행할 수 있습니다 (현재 "
                    + task.getStatus().getLabel() + "): " + taskId);
        }
        if (qty > task.remainingQty()) {
            throw new IllegalArgumentException("적치수량이 잔여수량을 초과했습니다 (잔여 " + task.remainingQty() + "): " + taskId);
        }

        IbLine ibLine = task.getIbLine();
        // 라인 상한 선검증 — 정상 데이터에선 지시 예약이 미적치(검수 − 적치누계)를 넘을 수 없어
        // 여기 걸리면 라인 카운터와 재고가 어긋난 것이다. DB CHECK(ck_ib_line_qty)에 맡기면
        // 「허용 범위를 벗어난 값」이라는 읽히지 않는 메시지가 나가서 원인을 먼저 말해준다
        long lineRemaining = ibLine.getRcvdQty() - ibLine.getPtawyQty();
        if (qty > lineRemaining) {
            throw new IllegalStateException("검수수량을 초과해 적치할 수 없습니다 (정합성 오류 — 검수 "
                    + ibLine.getRcvdQty() + " / 적치 완료 " + ibLine.getPtawyQty()
                    + " / 미적치 " + lineRemaining + "): " + ibLine.getProd().getProdCd());
        }
        Prod prod = ibLine.getProd();
        Loc target = task.getToLoc();

        // 선락 이후 지시의 로케이션이 바뀌었으면(지시 변경·분할) 잠근 도착 행이 지시와 어긋난다 —
        // 여기서 새 도착지를 잠그면 락 순서가 무너지므로 이번 실행을 되돌리고 다시 부르게 한다
        if (!target.getId().equals(lockKey.toLocId())) {
            throw new IllegalStateException("적치지시의 로케이션이 방금 변경됐습니다 — 다시 실행해 주세요: " + taskId);
        }
        // 지시 발행 이후 로케이션 마스터가 바뀌었을 수 있다 — 어긋났으면 취소 후 재지시가 정답이라 여기서 막는다
        if (target.getLocTyp() != LocTyp.STORAGE || target.getTmpZon() != prod.getTmpZon()) {
            throw new IllegalStateException("지시받은 로케이션이 적치 조건과 어긋납니다 (취소 후 재지시 필요): " + target.getLocCd());
        }

        Inv stagingInv = locked.inv().get(lockKey.stagingKey(locked.staging().getId()));
        if (stagingInv == null) {
            throw new IllegalStateException("적치지시가 예약한 재고가 없습니다 (정합성 오류): " + taskId);
        }
        if (stagingInv.getOnHandQty() < qty || stagingInv.getAlocQty() < qty) {
            throw new IllegalStateException("예약 수량보다 실재고가 적습니다 (정합성 오류 — 보유 " + stagingInv.getOnHandQty()
                    + " / 예약 " + stagingInv.getAlocQty() + "): " + taskId);
        }

        // 예약 소진 + 실물 이동 (이동확정과 같은 패턴). 예약을 먼저 푸는 이유는 순서가 뒤집히면
        // 스테이징 행이 「예약은 남았는데 실물은 0」인 중간 상태를 지나기 때문
        invStore.release(stagingInv, qty);
        invStore.move(stagingInv, target, qty,
                InvDocRef.ofIbLine(RefDocTyp.INBOUND, ibLine.getIbOrder().getIbNo(), ibLine.getId()));

        task.execute(qty);
        ibLine.putaway(qty);
        // 전량 적치돼도 헤더 상태는 안 바뀐다 — 종결은 입고확정 버튼(confirm)만이 한다
    }
}
