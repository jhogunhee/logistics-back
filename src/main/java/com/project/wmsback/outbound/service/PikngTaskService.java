package com.project.wmsback.outbound.service;

import com.project.wmsback.outbound.dto.PikngAcrstResponse;
import com.project.wmsback.outbound.dto.PikngCancelRequest;
import com.project.wmsback.outbound.dto.PikngCancelResponse;
import com.project.wmsback.outbound.dto.PikngIssueRequest;
import com.project.wmsback.outbound.dto.PikngIssueResponse;
import com.project.wmsback.outbound.dto.PikngTaskSearchCond;
import com.project.wmsback.outbound.dto.PikngWaveDetailResponse;
import com.project.wmsback.outbound.dto.PikngWaveResponse;
import com.project.wmsback.outbound.entity.OutbAlloc;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.entity.PikngTask;
import com.project.wmsback.outbound.entity.PikngTaskStatus;
import com.project.wmsback.outbound.entity.WaveStatus;
import com.project.wmsback.outbound.repository.OutbAllocRepository;
import com.project.wmsback.outbound.repository.OutbOrderRepository;
import com.project.wmsback.outbound.repository.PikngAcrstRepository;
import com.project.wmsback.outbound.repository.PikngTaskRepository;
import com.project.wmsback.outbound.repository.OutbWaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 피킹지시 — <b>웨이브의 할당 레코드를 로케이션 순으로 정렬해 지시 문서(pikng_task)로 발행한다.</b>
 *
 * <p>지시 행은 할당과 1:1이다(상품별 집약 없음). 발행은 재고에 손대지 않는다 — 예약은 할당이
 * 이미 잡았고, 실행({@link PikngService})이 소진한다. 재고 무변동이므로 취소도 문서 조작뿐이다.
 *
 * <p><b>발행 가드(주문 단위)</b>: 할당이 0건인 주문이 섞여 있으면 웨이브 발행을 차단한다 —
 * 그대로 발행하면 그 주문(CREATED)이 ISSUED 웨이브에 갇혀 편성 변경도 할당도 영영 못 받는다.
 * 부분할당 주문은 발행을 막지 않는다 — 미할당 잔량은 부족 출고로 정상 진행한다(백오더 없음).
 *
 * <p><b>지시취소는 웨이브 단위 · 실적 0일 때만.</b> 취소는 삭제가 아니라 CANCELLED 전이(행 보존)다 —
 * putaway_task·inv_mov_task와 같은 상태 기계. 재발행은 새 행을 만들고, 살아 있는 지시의
 * 유일성은 부분 유니크(uq_pikng_task_alloc)가 지킨다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PikngTaskService {

    private final PikngTaskRepository pikngTaskRepository;
    private final PikngAcrstRepository pikngAcrstRepository;
    private final OutbAllocRepository outbAllocRepository;
    private final OutbOrderRepository outbOrderRepository;
    private final OutbWaveRepository outbWaveRepository;

    // ── 조회 ─────────────────────────────────────────────────────────────────

    public List<PikngWaveResponse> searchWaves(PikngTaskSearchCond cond) {
        return pikngTaskRepository.searchTaskWaves(cond);
    }

    /**
     * 웨이브 상세. 발행 전(PLANNED)에는 할당 행을 발행 순서 그대로 보여주고(발행 미리보기),
     * 발행 후(ISSUED)에는 지시 스냅샷을 보여준다 — 완료된 지시는 재고 행이 삭제됐을 수 있어
     * alloc → inv 조인으로는 표시할 수 없다.
     */
    public PikngWaveDetailResponse detail(Long wavId) {
        OutbWave wave = findWave(wavId);
        boolean issued = wave.getStatus() == WaveStatus.ISSUED;
        return new PikngWaveDetailResponse(wave.getId(), wave.getWavNo(), wave.getStatus(),
                issued ? pikngTaskRepository.taskRows(wavId) : pikngTaskRepository.allocRowsForIssue(wavId),
                issued ? List.of() : pikngTaskRepository.noAllocOrders(wavId));
    }

    /** 지시의 실행 실적 로그 (실적 내역 모달) */
    public List<PikngAcrstResponse> acrsts(Long pikngTaskId) {
        if (!pikngTaskRepository.existsById(pikngTaskId)) {
            throw new IllegalArgumentException("존재하지 않는 피킹지시입니다: " + pikngTaskId);
        }
        return pikngAcrstRepository.findByPikngTaskIdOrderByIdDesc(pikngTaskId).stream()
                .map(acrst -> new PikngAcrstResponse(acrst.getId(), acrst.getPikngQty(),
                        acrst.getCreatedAt(), acrst.getCreatedBy()))
                .toList();
    }

    // ── 발행 ─────────────────────────────────────────────────────────────────

    /**
     * 피킹지시 발행. 여러 웨이브를 한 번에 보낼 수 있지만 <b>한 트랜잭션</b>이다 —
     * 도중 실패하면 이번 발행 전체가 롤백된다 (부분 성공 없음).
     */
    @Transactional
    public PikngIssueResponse issue(PikngIssueRequest request) {
        List<Long> wavIds = distinct(request.getWavIds());
        if (wavIds.isEmpty()) {
            throw new IllegalArgumentException("발행할 웨이브를 선택하세요.");
        }
        List<PikngIssueResponse.WaveResult> results = new ArrayList<>();
        int total = 0;
        for (Long wavId : wavIds) {
            // 웨이브 행 락 — 할당 실행·해제, 편성 변경, 취소, 피킹 실행과의 직렬화 지점.
            // 락 순서는 웨이브(오름차순) → 재고 한 방향이고, 발행은 재고를 건드리지 않아 앞 단계뿐이다.
            OutbWave wave = lockWave(wavId);
            if (wave.getStatus() != WaveStatus.PLANNED) {
                throw new IllegalStateException("이미 피킹지시가 발행된 웨이브입니다: " + wave.getWavNo());
            }

            List<OutbOrder> orders = outbOrderRepository.findByWaveId(wavId);
            if (orders.isEmpty()) {
                throw new IllegalArgumentException("웨이브에 편성된 주문이 없습니다: " + wave.getWavNo());
            }
            List<OutbAlloc> allocs = outbAllocRepository.findAllWithDetailsByWaveId(wavId);

            // 발행 가드 — 할당 0건 주문이 있으면 웨이브째 차단. 부분할당(라인 잔량)은 막지 않는다
            Set<Long> allocOrderIds = new HashSet<>();
            allocs.forEach(alloc -> allocOrderIds.add(alloc.getOutbLine().getOutbOrder().getId()));
            List<String> noAllocOutbNos = orders.stream()
                    .filter(order -> !allocOrderIds.contains(order.getId()))
                    .map(OutbOrder::getOutbNo)
                    .toList();
            if (!noAllocOutbNos.isEmpty()) {
                throw new IllegalStateException("할당이 없는 주문이 있어 피킹지시를 발행할 수 없습니다"
                        + " (웨이브에서 빼거나 할당 후 다시 시도하세요): " + String.join(", ", noAllocOutbNos));
            }

            // 집품 순서를 발행 시점에 고정한다 — 조회 시 정렬이면 작업 중 마스터 변경(pikng_prty)이
            // 리스트 순서를 흔든다. 끝에 할당 id를 붙여 결정적으로 만든다 (allocRowsForIssue와 한 쌍).
            List<OutbAlloc> sorted = new ArrayList<>(allocs);
            sorted.sort(Comparator
                    .comparing((OutbAlloc alloc) -> alloc.getInv().getLoc().getPikngPrty())
                    .thenComparing(alloc -> alloc.getInv().getLoc().getLocCd())
                    .thenComparing(OutbAlloc::getId));

            List<PikngTask> tasks = new ArrayList<>(sorted.size());
            int seq = 0;
            for (OutbAlloc alloc : sorted) {
                // 재고 키는 발행 시점 스냅샷으로 지시에 남긴다 — inv 행은 전량 피킹 후 삭제될 수 있다
                tasks.add(PikngTask.builder()
                        .wave(wave).outbAlloc(alloc)
                        .prod(alloc.getInv().getProd())
                        .fromLoc(alloc.getInv().getLoc())
                        .lot(alloc.getInv().getLot())
                        .drctQty(alloc.getAlocQty())
                        .srtSeq(++seq)
                        .build());
            }
            pikngTaskRepository.saveAll(tasks);
            wave.issue();

            results.add(new PikngIssueResponse.WaveResult(wave.getWavNo(), tasks.size()));
            total += tasks.size();
        }
        return new PikngIssueResponse(results.size(), total, results);
    }

    // ── 지시취소 ──────────────────────────────────────────────────────────────

    /** 지시취소 — 웨이브 단위, 실적 0일 때만. 재고 무변동 (발행이 재고에 손대지 않았으므로) */
    @Transactional
    public PikngCancelResponse cancel(PikngCancelRequest request) {
        List<Long> wavIds = distinct(request.getWavIds());
        if (wavIds.isEmpty()) {
            throw new IllegalArgumentException("취소할 웨이브를 선택하세요.");
        }
        List<PikngCancelResponse.WaveResult> results = new ArrayList<>();
        int total = 0;
        for (Long wavId : wavIds) {
            OutbWave wave = lockWave(wavId);
            if (wave.getStatus() != WaveStatus.ISSUED) {
                throw new IllegalStateException("피킹지시가 발행되지 않은 웨이브입니다: " + wave.getWavNo());
            }
            List<PikngTask> live = pikngTaskRepository.findByWaveIdAndStatusNot(wavId, PikngTaskStatus.CANCELLED);
            if (live.isEmpty()) {
                throw new IllegalStateException("취소할 지시가 없습니다: " + wave.getWavNo());
            }
            long picked = live.stream().mapToLong(PikngTask::getCmplQty).sum();
            if (picked > 0) {
                throw new IllegalStateException("피킹이 시작된 웨이브는 지시를 취소할 수 없습니다"
                        + " (피킹 " + picked + "): " + wave.getWavNo());
            }
            live.forEach(PikngTask::cancel);
            wave.cancelIssue();

            results.add(new PikngCancelResponse.WaveResult(wave.getWavNo(), live.size()));
            total += live.size();
        }
        return new PikngCancelResponse(results.size(), total, results);
    }

    // ── 공통 ─────────────────────────────────────────────────────────────────

    private OutbWave findWave(Long wavId) {
        return outbWaveRepository.findById(wavId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 웨이브입니다: " + wavId));
    }

    private OutbWave lockWave(Long wavId) {
        return outbWaveRepository.findByIdForUpdate(wavId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 웨이브입니다: " + wavId));
    }

    private static List<Long> distinct(List<Long> ids) {
        return ids == null ? List.of()
                : ids.stream().filter(Objects::nonNull).distinct()
                        .sorted(Comparator.naturalOrder()).toList();
    }
}
