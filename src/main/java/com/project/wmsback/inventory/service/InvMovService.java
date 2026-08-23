package com.project.wmsback.inventory.service;

import com.project.wmsback.inventory.dto.InvMovConfirmRequest;
import com.project.wmsback.inventory.dto.InvMovRegisterRequest;
import com.project.wmsback.inventory.dto.InvMovTaskResponse;
import com.project.wmsback.inventory.dto.InvMovTaskSearchCond;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvMovDvsn;
import com.project.wmsback.inventory.entity.InvMovStatus;
import com.project.wmsback.inventory.entity.InvMovTask;
import com.project.wmsback.inventory.entity.RefDocTyp;
import com.project.wmsback.inventory.repository.InvMovTaskRepository;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.warehouse.repository.LocRepository;
import com.project.mdm.nbr.service.NbrService;
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
import java.util.TreeSet;

/**
 * 재고 이동지시 (보관 ↔ 보관 2단계: 지시=예약 → 확정=실물 MOVE).
 *
 * 등록이 FROM 재고의 aloc를 선점해 출고 할당(FEFO)과의 경합을 원천 차단하고,
 * 확정이 inv_hist MOVE 2행을 남기며 예약을 소진한다 (피킹이 aloc를 소진하는 것과 같은 패턴).
 * 실적 테이블은 없다 — 분할확정 실적은 inv_hist에 확정 횟수만큼 쌓인다 (rfn_doc_no = 지시번호).
 * 적치지시(putaway_task)도 같은 형태로 스테이징 재고를 예약한다 — aloc_qty는 예약수량 일반이라
 * 원천이 무엇이든 같은 컬럼을 쓴다. docs/design.md 「재고 이동」·「적치 지시」 참고.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvMovService {

    private final InvRepository invRepository;
    private final InvStore invStore;
    private final InvMovTaskRepository invMovTaskRepository;
    private final LocRepository locRepository;
    private final LocCapacityService locCapacityService;
    private final NbrService nbrService;

    public List<InvMovTaskResponse> list(InvMovTaskSearchCond cond) {
        return invMovTaskRepository.search(cond);
    }

    /**
     * 이동지시 등록 (예약). 전체가 한 트랜잭션 — 한 건이라도 검증에 걸리면 전량 롤백.
     *
     * 「도착 loc 선락 → FROM 재고 전량 선락 → 건별 검증·채번」의 3단계다. 재고 선락은 보류 등록과
     * 같은 이유 — 건별로 「재고 락 → 채번」을 반복하면 채번 카운터 행 락이 재고 행 락 사이에 끼어
     * 재고가 겹치는 두 요청이 카운터와 재고를 나눠 쥐고 맞물린다. 도착 loc 선락의 이유는 아래 주석 참고.
     *
     * @return 발급된 이동지시 번호 목록 (요청 순서)
     */
    @Transactional
    public List<String> register(InvMovRegisterRequest request) {
        return register(request, InvMovDvsn.INV_MOV);
    }

    /**
     * 이동구분을 지정하는 등록 — 정기보충(SPMT) 발행이 자기 검증을 마친 뒤 이 창구로 위임한다.
     * 락 순서·검증·예약이 유형과 무관하게 동일해 경로를 복제하지 않는다. 채번규칙은 구분값이 든다.
     */
    @Transactional
    public List<String> register(InvMovRegisterRequest request, InvMovDvsn movDvsn) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("이동지시 대상이 없습니다.");
        }
        Set<Long> invIds = new LinkedHashSet<>();
        Set<Long> toLocIds = new TreeSet<>();
        for (InvMovRegisterRequest.Item item : request.getItems()) {
            if (item.getInvId() == null) {
                throw new IllegalArgumentException("이동할 재고가 지정되지 않았습니다.");
            }
            if (item.getToLocId() == null) {
                throw new IllegalArgumentException("이동할 도착 로케이션이 지정되지 않았습니다.");
            }
            invIds.add(item.getInvId());
            toLocIds.add(item.getToLocId());
        }

        // 도착 로케이션 선락 (id 오름차순, 재고 행보다 먼저 — docs/design.md 「락 순서」) —
        // 적재가능수량 검증의 직렬화 지점. 검증이 락 없는 집계 읽기라, 잠그지 않으면 같은 도착지로
        // 향하는 두 등록이 서로의 유입을 못 본 채 둘 다 통과해 합산 유입이 max_qty를 넘긴다
        Map<Long, Loc> lockedToLocs = new HashMap<>();
        for (Long toLocId : toLocIds) {
            lockedToLocs.put(toLocId, locRepository.findByIdForUpdate(toLocId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로케이션입니다: " + toLocId)));
        }

        // FROM 재고 행 선락 — 예약(aloc) 증감의 직렬화 지점 (출고 할당이 같은 행을 잡는 지점과 동일)
        Map<Long, Inv> locked = invStore.lockAllByIds(invIds);

        List<String> movNos = new ArrayList<>();
        for (InvMovRegisterRequest.Item item : request.getItems()) {
            Inv fromInv = locked.get(item.getInvId());
            if (fromInv == null) {
                throw new IllegalArgumentException("존재하지 않는 재고입니다: " + item.getInvId());
            }
            movNos.add(registerOne(item, fromInv, lockedToLocs.get(item.getToLocId()), movDvsn));
        }
        return movNos;
    }

    private String registerOne(InvMovRegisterRequest.Item item, Inv fromInv, Loc to, InvMovDvsn movDvsn) {
        if (item.getQty() == null || item.getQty() < 1) {
            throw new IllegalArgumentException("이동수량은 1 이상이어야 합니다.");
        }
        Prod prodEntity = fromInv.getProd();
        Lot lotEntity = fromInv.getLot();
        Loc from = fromInv.getLoc();

        if (from.getLocTyp() != LocTyp.STORAGE) {
            throw new IllegalArgumentException("보관 로케이션의 재고만 이동할 수 있습니다 (스테이징 재고는 적치·출고확정의 소관): " + from.getLocCd());
        }
        if (to.getId().equals(from.getId())) {
            throw new IllegalArgumentException("출발지와 도착지가 같습니다: " + from.getLocCd());
        }
        if (to.getLocTyp() != LocTyp.STORAGE) {
            throw new IllegalArgumentException("보관 로케이션으로만 이동할 수 있습니다: " + to.getLocCd());
        }
        if (to.getTmpZon() != prodEntity.getTmpZon()) {
            throw new IllegalArgumentException("온도대가 일치하지 않습니다 (상품 " + prodEntity.getTmpZon()
                    + " / 로케이션 " + to.getTmpZon() + "): " + to.getLocCd());
        }
        if (item.getQty() > fromInv.avalQty()) {
            throw new IllegalArgumentException("이동수량이 가용재고를 초과했습니다 (가용 " + fromInv.avalQty() + "): "
                    + prodEntity.getProdCd() + " @ " + from.getLocCd());
        }
        // 적재가능수량은 LocCapacityService가 단일 정의를 갖는다 (적치지시 유입분도 같은 항에 합산된다).
        // null = max_qty 미설정(무제한) — STORAGE는 NOT NULL이 DB 강제(ck_loc_storage_capacity)이지만
        // 강제 이전의 옛 행일 수 있다
        Long capacity = locCapacityService.availCapacity(to);
        if (capacity != null && item.getQty() > capacity) {
            throw new IllegalArgumentException("도착 로케이션의 적재가능수량을 초과했습니다 (적재가능 " + capacity
                    + "): " + to.getLocCd());
        }

        invStore.reserve(fromInv, item.getQty());
        InvMovTask task = InvMovTask.builder()
                .invMovNo(nbrService.issue(movDvsn.getNoRuleCd(), LocalDate.now()))
                .movDvsn(movDvsn)
                .prod(prodEntity).lot(lotEntity)
                .fromLoc(from).toLoc(to)
                .drctQty(item.getQty())
                .build();
        invMovTaskRepository.save(task);
        return task.getInvMovNo();
    }

    /**
     * 이동확정 (실물 MOVE, 건마다 부분확정 허용). 전체가 한 트랜잭션 — 한 건이라도 검증에 걸리면 전량 롤백.
     * 지시 TO와 다른 로케이션으로는 확정할 수 없다 — 지시는 권고가 아니라 명령이며,
     * 다른 곳에 두려면 잔량 취소 후 재지시한다.
     *
     * 락은 재고 행(FROM·TO)을 전부 잡은 뒤 지시를 잡는다. 건별로 「지시 → 그 지시의 재고 행」 순서로
     * 잡으면 다건에서 교착이 난다 — 한 재고 행에 지시가 여러 건 병존할 수 있어서, 그 행의 지시 둘을
     * 함께 확정하는 요청이 앞 건에서 재고 행을 쥔 채 뒤 건의 지시를 기다리는 동안, 그 지시 하나만
     * 확정하는 요청이 반대로 물린다 (보류 해제와 같은 짝). 그래서 재고 행을 먼저 모두 선락하고
     * (InvStore가 키 오름차순으로 잠근다), 지시는 id 오름차순으로 잡아 순서를 맞춘다.
     */
    @Transactional
    public void confirm(InvMovConfirmRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("확정 대상이 없습니다.");
        }
        Set<Long> taskIds = new LinkedHashSet<>();
        for (InvMovConfirmRequest.Item item : request.getItems()) {
            Long taskId = item.getTaskId();
            if (taskId == null) {
                throw new IllegalArgumentException("확정할 이동지시가 지정되지 않았습니다.");
            }
            // 같은 지시를 두 번 실으면 잔여를 두 번 깎으면서 실적만 두 벌 남는다 — 애초에 거부한다
            if (!taskIds.add(taskId)) {
                throw new IllegalArgumentException("같은 이동지시가 두 번 실렸습니다 — 한 번에 한 값으로만 확정할 수 있습니다: " + taskId);
            }
        }

        // 잠글 재고 행을 고르기 위한 사전 조회. 정렬 키(상품·로케이션·Lot)는 지시가 만들어질 때
        // 정해져 바뀌지 않으므로 락 없이 미리 읽는다
        Map<Long, InvMovLockKey> keyByTaskId = new HashMap<>();
        for (InvMovLockKey row : invMovTaskRepository.findLockKeysByIdIn(taskIds)) {
            keyByTaskId.put(row.taskId(), row);
        }
        List<InvKey> keys = new ArrayList<>();
        for (Long taskId : taskIds) {
            InvMovLockKey row = keyByTaskId.get(taskId);
            if (row == null) {
                throw new IllegalArgumentException("존재하지 않는 이동지시입니다: " + taskId);
            }
            keys.add(row.fromKey());
            keys.add(row.toKey());
        }

        // 도착 행은 아직 없을 수 있어 결과에서 빠진다 (없으면 move가 만든다).
        // 출발 행이 없는 것은 예약이 사라졌다는 뜻이라 건별 처리가 정합성 오류로 잡는다
        Map<InvKey, Inv> locked = invStore.lockAll(keys);

        request.getItems().stream()
                .sorted(Comparator.comparing(InvMovConfirmRequest.Item::getTaskId))
                .forEach(item -> confirmOne(item, locked));
    }

    private void confirmOne(InvMovConfirmRequest.Item item, Map<InvKey, Inv> locked) {
        Long qty = item.getQty();
        if (qty == null || qty < 1) {
            throw new IllegalArgumentException("확정수량은 1 이상이어야 합니다.");
        }
        InvMovTask task = invMovTaskRepository.findByIdForUpdate(item.getTaskId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이동지시입니다: " + item.getTaskId()));
        // 예약을 드는 구분(재고이동·정기보충)만 이 경로에서 확정 가능 — 실물을 옮기며 예약을 소진하는 동일 작업이다.
        // 수시보충(RPLN)은 예약을 들지 않아 확정이 할당 재배치까지 해야 하므로 RplnService가 전담한다.
        // 허용 여부는 구분값의 속성으로 판정한다 — 값 비교 차단목록이면 새 구분이 기본 허용으로 열린다
        if (!task.getMovDvsn().isReserving()) {
            throw new IllegalArgumentException(task.getMovDvsn().getLabel() + " 지시는 이 화면에서 확정할 수 없습니다 (예약을 들지 않는 이동구분): " + task.getInvMovNo());
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

        // 선락 단계에서 잠근 행을 꺼내 쓴다 (지시의 재고 키는 등록 후 바뀌지 않는다). 도착 행도
        // 증가 전에 잠가야 같은 행으로 동시에 들어오는 유입이 서로 덮어쓰지 않아 함께 잠겨 있고,
        // 아직 없던 행이면 거기서 빠져 move가 만든다 (동시 생성은 uq_inv가 방어)
        Inv fromInv = locked.get(new InvKey(prodEntity.getId(), from.getId(), lotEntity.getId()));
        if (fromInv == null) {
            throw new IllegalStateException("이동지시가 예약한 재고가 없습니다 (정합성 오류): " + task.getInvMovNo());
        }
        if (fromInv.getOnHandQty() < qty || fromInv.getAlocQty() < qty) {
            throw new IllegalStateException("예약 수량보다 실재고가 적습니다 (정합성 오류 — 보유 " + fromInv.getOnHandQty()
                    + " / 예약 " + fromInv.getAlocQty() + "): " + task.getInvMovNo());
        }

        // 예약 소진 + 실물 이동 (피킹이 aloc와 onHand를 함께 줄이는 것과 같은 패턴).
        // 예약을 먼저 푸는 이유는 순서가 뒤집히면 FROM 행이 「예약은 남았는데 실물은 0」인 중간 상태를 지나기 때문
        invStore.release(fromInv, qty);
        invStore.move(fromInv, to, qty, InvDocRef.of(RefDocTyp.INV_MOV, task.getInvMovNo()));

        task.confirm(qty);
    }

    /**
     * 이동취소 (잔량 취소 — 예약 해제). 물리 이동이 없으므로 inv_hist 기록도 없다.
     * 단건이지만 락 순서는 확정과 같다 — 재고 행 먼저, 지시는 그 뒤. 뒤집으면 다건 확정과 맞물려
     * 교착 짝이 된다. 잠글 키는 스칼라로 선조회한다 (지시를 엔티티로 먼저 읽으면 뒤에 거는 락이
     * 그때 올라간 낡은 인스턴스를 그대로 돌려줘 완료수량이 갱신되지 않는다).
     */
    @Transactional
    public void cancel(Long taskId) {
        InvMovLockKey lockKey = invMovTaskRepository.findLockKeysByIdIn(List.of(taskId)).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이동지시입니다: " + taskId));
        // 없는 행을 여기서 문제 삼지 않는다 — 이미 취소·완료된 지시는 재고 행이 남아 있지 않을 수 있고,
        // 그건 아래 상태 검증이 「지시 상태의 지시만 취소할 수 있다」로 잡아야 할 몫이다
        Optional<Inv> lockedFrom = invStore.lock(lockKey.fromKey());

        InvMovTask task = invMovTaskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이동지시입니다: " + taskId));
        // 예약을 드는 구분만 이 경로에서 취소 가능 (확정과 같은 방어)
        if (!task.getMovDvsn().isReserving()) {
            throw new IllegalArgumentException(task.getMovDvsn().getLabel() + " 지시는 이 화면에서 취소할 수 없습니다 (예약을 들지 않는 이동구분): " + task.getInvMovNo());
        }
        if (task.getStatus() != InvMovStatus.DIRECTED) {
            throw new IllegalArgumentException("지시 상태의 이동지시만 취소할 수 있습니다 (현재 " + task.getStatus().getLabel() + "): " + task.getInvMovNo());
        }
        long remaining = task.remainingQty();

        Inv fromInv = lockedFrom
                .orElseThrow(() -> new IllegalStateException("이동지시가 예약한 재고가 없습니다 (정합성 오류): " + task.getInvMovNo()));
        if (fromInv.getAlocQty() < remaining) {
            throw new IllegalStateException("예약 잔량보다 재고의 예약 수량이 적습니다 (정합성 오류 — 예약 " + fromInv.getAlocQty()
                    + " / 잔여 " + remaining + "): " + task.getInvMovNo());
        }

        invStore.release(fromInv, remaining);
        task.cancelRemainder();
    }
}
