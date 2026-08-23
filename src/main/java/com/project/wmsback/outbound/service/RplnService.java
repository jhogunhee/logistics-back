package com.project.wmsback.outbound.service;

import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvMovDvsn;
import com.project.wmsback.inventory.entity.InvMovStatus;
import com.project.wmsback.inventory.entity.InvMovTask;
import com.project.wmsback.inventory.entity.RefDocTyp;
import com.project.wmsback.inventory.repository.InvMovTaskRepository;
import com.project.wmsback.inventory.service.InvDocRef;
import com.project.wmsback.inventory.service.InvKey;
import com.project.wmsback.inventory.service.InvMovLockKey;
import com.project.wmsback.inventory.service.InvStore;
import com.project.wmsback.outbound.dto.RplnActionResponse;
import com.project.wmsback.outbound.dto.RplnRowResponse;
import com.project.wmsback.outbound.dto.RplnSearchCond;
import com.project.wmsback.outbound.dto.RplnTaskRequest;
import com.project.wmsback.outbound.dto.RplnWaveResponse;
import com.project.wmsback.outbound.entity.OutbAlloc;
import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.entity.PikngTask;
import com.project.wmsback.outbound.entity.PikngTaskStatus;
import com.project.wmsback.outbound.entity.WaveStatus;
import com.project.wmsback.outbound.repository.OutbWaveRepository;
import com.project.wmsback.outbound.repository.PikngTaskRepository;
import com.project.wmsback.outbound.repository.RplnQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 수시보충 확정·취소 — 피킹지시 발행이 보관존 할당분에 짝으로 낸 보충지시(RPLN)를 처리한다.
 *
 * <p><b>보충지시는 예약을 들지 않는다.</b> 예약의 주인은 할당이고, 확정이 그 예약을 실물과 함께
 * 도착지(피킹존)로 옮긴 뒤 할당이 가리키는 재고 행을 도착지 행으로 바꾼다({@link InvStore#replenish}
 * + {@link OutbAlloc#relocate}). 그래서 취소는 예약을 건드리지 않고 상태 전이로만 끝난다 —
 * 보충지시와 짝 피킹지시가 함께 CANCELLED가 되고 할당은 그대로 남는다(재발행 대상).
 * 이동확정({@code InvMovService.confirm})과 갈리는 지점이 이것이라 경로를 따로 둔다.
 *
 * <p><b>전량 확정만</b> — 할당 행 하나는 재고 행 하나를 가리키므로 반만 옮기면 할당이 둘에 걸친다.
 *
 * <p>락 순서: 웨이브(오름차순) → 재고 행(키 오름차순, from·to) → 지시 행(id 오름차순). 피킹 실행이
 * 웨이브 → 재고 순으로 잡으므로 같은 순서로 맞춰야 「보충 확정 중 피킹 실행」이 교착 없이 직렬화된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RplnService {

    private final RplnQueryRepository rplnQueryRepository;
    private final InvMovTaskRepository invMovTaskRepository;
    private final PikngTaskRepository pikngTaskRepository;
    private final OutbWaveRepository outbWaveRepository;
    private final InvStore invStore;

    public List<RplnWaveResponse> searchWaves(RplnSearchCond cond) {
        return rplnQueryRepository.searchWaves(cond);
    }

    public List<RplnRowResponse> rows(Long wavId) {
        return rplnQueryRepository.rows(wavId);
    }

    /** 보충 확정 (전량). 여러 건을 보내도 한 트랜잭션 — 하나가 걸리면 전부 롤백 */
    @Transactional
    public RplnActionResponse confirm(RplnTaskRequest request) {
        List<Long> taskIds = validated(request);
        Map<Long, InvMovLockKey> keyByTaskId = lockWavesAndKeys(taskIds);

        List<InvKey> keys = new ArrayList<>();
        for (InvMovLockKey key : keyByTaskId.values()) {
            keys.add(key.fromKey());
            keys.add(key.toKey());
        }
        // 도착 행은 아직 없을 수 있어 결과에서 빠진다 (없으면 replenish가 만든다)
        Map<InvKey, Inv> locked = invStore.lockAll(keys);

        List<String> invMovNos = new ArrayList<>();
        for (Long taskId : new TreeSet<>(taskIds)) {
            InvMovTask task = lockRpln(taskId);
            PikngTask pikngTask = pikngTaskRepository.findById(task.getPikngTaskId())
                    .orElseThrow(() -> new IllegalStateException("짝 피킹지시가 없습니다 (정합성 오류): " + task.getInvMovNo()));
            if (pikngTask.getStatus() != PikngTaskStatus.DIRECTED) {
                throw new IllegalStateException("짝 피킹지시가 " + pikngTask.getStatus().getLabel()
                        + " 상태라 보충을 확정할 수 없습니다 — 보충을 취소하세요: " + task.getInvMovNo());
            }
            OutbAlloc alloc = pikngTask.getOutbAlloc();
            long qty = task.remainingQty();

            Inv fromInv = locked.get(keyByTaskId.get(taskId).fromKey());
            if (fromInv == null) {
                throw new IllegalStateException("보충할 재고가 없습니다 (할당 예약과 재고가 어긋났습니다): " + task.getInvMovNo());
            }
            if (!Objects.equals(alloc.getInv().getId(), fromInv.getId())) {
                throw new IllegalStateException("할당이 가리키는 재고와 보충 출발지가 다릅니다 (정합성 오류): " + task.getInvMovNo());
            }
            if (fromInv.getOnHandQty() < qty || fromInv.getAlocQty() < qty) {
                throw new IllegalStateException("예약 수량보다 실재고가 적습니다 (정합성 오류 — 보유 " + fromInv.getOnHandQty()
                        + " / 예약 " + fromInv.getAlocQty() + "): " + task.getInvMovNo());
            }

            Inv toInv = invStore.replenish(fromInv, task.getToLoc(), qty,
                    InvDocRef.of(RefDocTyp.INV_MOV, task.getInvMovNo()));
            alloc.relocate(toInv);
            task.confirm(qty);
            invMovNos.add(task.getInvMovNo());
        }
        return new RplnActionResponse(invMovNos.size(), invMovNos);
    }

    /**
     * 보충 취소 — 실물 이동 전이라 예약 변화가 없다. <b>짝 피킹지시도 함께 취소한다</b>
     * (지시취소가 짝 보충을 함께 취소하는 것의 반대 방향 — {@code PikngTaskService.cancelTasks}).
     *
     * <p>보충만 취소하고 지시를 DIRECTED로 남기면 실행 가드가 뚫린다. 가드는 취소되지 않은 보충만
     * 읽으므로({@code findByPikngTaskIdInAndStatusNot}) 취소된 짝은 「짝이 없는 지시」로 보여 그대로
     * 통과하고, 실물은 아직 보관존에 있는데 지시의 from은 피킹존이라 집품 실적이 실물과 어긋난다.
     *
     * <p>지시를 남길 이유도 없다 — from은 이번 발행에서 정한 도착지로 굳어 있어, 도착지를 다시 정하려면
     * 어차피 재발행이 필요하다. 「보충지시 단독 재발행은 없다 — 피킹지시 취소 → 추가 발행으로 갈음」이
     * 그대로 적용된다.
     */
    @Transactional
    public RplnActionResponse cancel(RplnTaskRequest request) {
        List<Long> taskIds = validated(request);
        Map<Long, OutbWave> waves = lockWaves(taskIds);

        List<String> invMovNos = new ArrayList<>();
        for (Long taskId : new TreeSet<>(taskIds)) {
            InvMovTask task = lockRpln(taskId);
            PikngTask pikngTask = pikngTaskRepository.findById(task.getPikngTaskId())
                    .orElseThrow(() -> new IllegalStateException("짝 피킹지시가 없습니다 (정합성 오류): " + task.getInvMovNo()));
            task.cancelRemainder();
            // DIRECTED일 때만 — 지시가 이미 취소돼 있어도 남은 보충은 정리돼야 한다 (지시취소 쪽 짝 처리와 대칭)
            if (pikngTask.getStatus() == PikngTaskStatus.DIRECTED) {
                pikngTask.cancel();
            }
            invMovNos.add(task.getInvMovNo());
        }

        // 살아 있는 지시가 0건이 된 웨이브는 PLANNED로 돌린다 — 「지시 없는 ISSUED 웨이브」를 만들지 않는
        // 것은 지시를 취소하는 모든 진입의 공통 책임이다. 세기 전에 방금 한 전이를 반영한다
        pikngTaskRepository.flush();
        for (OutbWave wave : waves.values()) {
            if (wave.getStatus() == WaveStatus.ISSUED
                    && pikngTaskRepository.findByWaveIdAndStatusNot(wave.getId(), PikngTaskStatus.CANCELLED).isEmpty()) {
                wave.cancelIssue();
            }
        }
        return new RplnActionResponse(invMovNos.size(), invMovNos);
    }

    /**
     * 웨이브 락(오름차순) + 재고 키 선조회. 웨이브 id는 짝 피킹지시에서 얻는다 — 지시의 짝과 재고 키는
     * 만들어진 뒤 바뀌지 않으므로 락 없이 읽어도 된다(이동확정의 키 선조회와 같은 전제).
     */
    private Map<Long, InvMovLockKey> lockWavesAndKeys(List<Long> taskIds) {
        Map<Long, InvMovLockKey> keyByTaskId = new HashMap<>();
        for (InvMovLockKey row : invMovTaskRepository.findLockKeysByIdIn(taskIds)) {
            keyByTaskId.put(row.taskId(), row);
        }
        if (keyByTaskId.size() != taskIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 보충지시가 포함돼 있습니다.");
        }
        lockWaves(taskIds);
        return keyByTaskId;
    }

    /**
     * 짝 피킹지시가 속한 웨이브를 id 오름차순으로 잠근다 — 전 조작의 첫 락이다. 웨이브 id는 짝
     * 피킹지시에서 스칼라로 얻는다(엔티티로 먼저 읽으면 뒤에 거는 락이 낡은 인스턴스를 돌려준다).
     */
    private Map<Long, OutbWave> lockWaves(List<Long> taskIds) {
        Map<Long, OutbWave> waves = new LinkedHashMap<>();
        List<Long> pikngTaskIds = invMovTaskRepository.findPikngTaskIdsByIdIn(taskIds);
        if (pikngTaskIds.isEmpty()) {
            return waves;
        }
        for (Long wavId : new TreeSet<>(pikngTaskRepository.findWaveIdsByTaskIds(pikngTaskIds))) {
            waves.put(wavId, outbWaveRepository.findByIdForUpdate(wavId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 웨이브입니다: " + wavId)));
        }
        return waves;
    }

    private InvMovTask lockRpln(Long taskId) {
        InvMovTask task = invMovTaskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 보충지시입니다: " + taskId));
        if (task.getMovDvsn() != InvMovDvsn.RPLN) {
            throw new IllegalArgumentException("수시보충 지시만 이 화면에서 처리할 수 있습니다 (이동구분 "
                    + task.getMovDvsn().getLabel() + "): " + task.getInvMovNo());
        }
        if (task.getStatus() != InvMovStatus.DIRECTED) {
            throw new IllegalArgumentException("지시 상태의 보충만 처리할 수 있습니다 (현재 "
                    + task.getStatus().getLabel() + "): " + task.getInvMovNo());
        }
        return task;
    }

    private static List<Long> validated(RplnTaskRequest request) {
        if (request.getTaskIds() == null || request.getTaskIds().isEmpty()) {
            throw new IllegalArgumentException("보충지시를 선택하세요.");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Long id : request.getTaskIds()) {
            if (id == null) {
                throw new IllegalArgumentException("보충지시를 지정하세요.");
            }
            ids.add(id);
        }
        return new ArrayList<>(ids);
    }
}
