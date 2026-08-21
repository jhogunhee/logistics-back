package com.project.wmsback.outbound.service;

import com.project.wmsback.outbound.dto.PikngAcrstResponse;
import com.project.wmsback.outbound.dto.PikngCancelRequest;
import com.project.wmsback.outbound.dto.PikngCancelResponse;
import com.project.wmsback.outbound.dto.PikngIssueRequest;
import com.project.wmsback.outbound.dto.PikngIssueResponse;
import com.project.wmsback.outbound.dto.PikngRowResponse;
import com.project.wmsback.outbound.dto.PikngTaskSearchCond;
import com.project.wmsback.outbound.dto.PikngWaveDetailResponse;
import com.project.wmsback.outbound.dto.PikngWaveResponse;
import com.project.wmsback.outbound.entity.OutbAlloc;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbStatus;
import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.entity.PikngTask;
import com.project.wmsback.outbound.entity.PikngTaskStatus;
import com.project.wmsback.outbound.entity.WaveStatus;
import com.project.wmsback.outbound.repository.OutbAllocRepository;
import com.project.wmsback.outbound.repository.OutbOrderRepository;
import com.project.wmsback.outbound.repository.PikngAcrstRepository;
import com.project.wmsback.outbound.repository.PikngTaskRepository;
import com.project.wmsback.outbound.repository.OutbWaveRepository;
import com.project.wmsback.warehouse.repository.LocRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * <p><b>지시취소는 웨이브 단위 또는 지시 단위이고, 실적 0인 것만 취소한다.</b> 두 진입의 차이는
 * 실적 판정 범위뿐이다 — 웨이브 단위는 발행의 역조작이라 웨이브 전체를, 지시 단위는 대상 지시
 * 자신만 본다. 취소는 삭제가 아니라 CANCELLED 전이(행 보존)다 — putaway_task·inv_mov_task와
 * 같은 상태 기계. 재발행은 새 행을 만들고, 살아 있는 지시의 유일성은 부분
 * 유니크(uq_pikng_task_alloc)가 지킨다.
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
    private final LocRepository locRepository;

    /** 피킹 도착지. 실행(PikngService)·출고확정(OutbShmtService)과 같은 값 — 여기서는 존재만 확인한다 */
    private static final String SHIP_STAGING_LOC_CD = "SHIP-STAGE";

    // ── 조회 ─────────────────────────────────────────────────────────────────

    public List<PikngWaveResponse> searchWaves(PikngTaskSearchCond cond) {
        return pikngTaskRepository.searchTaskWaves(cond);
    }

    /**
     * 웨이브 상세. 발행 전(PLANNED)에는 할당 행을 발행 순서 그대로 보여주고(발행 미리보기),
     * 발행 후(ISSUED)에는 지시 스냅샷을 보여준다 — 완료된 지시는 재고 행이 삭제됐을 수 있어
     * alloc → inv 조인으로는 표시할 수 없다.
     *
     * <p><b>미발행 할당({@code pendingRows})은 발행 후에만, 할당 0건 주문은 상태와 무관하게 내려보낸다.</b>
     * 발행 전에는 미발행 할당이 곧 {@code rows}라 같은 목록을 두 번 보내지 않는다. 발행 후에도 할당이
     * 붙을 수 있고(결품 종결이 잔량을 키우거나 재할당이 들어온다), 지시취소 뒤 할당해제로 할당이
     * 0건이 된 주문이 생길 수 있다. 예전에는 ISSUED에서 둘 다 빈 목록으로 내려 그 주문이 화면
     * 어디에도 드러나지 않았다.
     */
    public PikngWaveDetailResponse detail(Long wavId) {
        OutbWave wave = findWave(wavId);
        boolean issued = wave.getStatus() == WaveStatus.ISSUED;
        List<PikngRowResponse> pending = pikngTaskRepository.allocRowsForIssue(wavId);
        return new PikngWaveDetailResponse(wave.getId(), wave.getWavNo(), wave.getStatus(),
                issued ? pikngTaskRepository.taskRows(wavId) : pending,
                issued ? pending : List.of(),
                pikngTaskRepository.noAllocOrders(wavId));
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
        return issue(request, false);
    }

    /**
     * 추가 발행 — 이미 발행된 웨이브에 <b>나중에 붙은 할당</b>의 지시를 낸다. 웨이브 상태는
     * 그대로 ISSUED이고 집품 순번은 기존 뒤에 이어붙는다.
     *
     * <p>여는 이유는 둘이다. ① 결품 종결이 {@code aloc_qty}를 낮춰 잔량을 사후에 키운다
     * ② 지시 단위 취소 뒤 할당해제를 하면 그 주문의 할당이 0건이 되는데, 웨이브가 ISSUED로
     * 남아 있으면 재할당·재발행이 전부 막혀 주문이 갇힌다. 그 둘의 출구가 여기다.
     */
    @Transactional
    public PikngIssueResponse issueAdditional(PikngIssueRequest request) {
        return issue(request, true);
    }

    private PikngIssueResponse issue(PikngIssueRequest request, boolean additional) {
        List<Long> wavIds = distinct(request.getWavIds());
        if (wavIds.isEmpty()) {
            throw new IllegalArgumentException("발행할 웨이브를 선택하세요.");
        }
        // 도착지는 발행에서 한 번 확인한다 — 실행 시점에 처음 찾으면 작업자가 창고를 다 돈 뒤에야 롤백된다
        if (locRepository.findByLocCd(SHIP_STAGING_LOC_CD).isEmpty()) {
            throw new IllegalStateException("출고 스테이징 로케이션(SHIP-STAGE)이 없어 피킹지시를 발행할 수 없습니다 — 로케이션을 먼저 등록하세요.");
        }
        List<PikngIssueResponse.WaveResult> results = new ArrayList<>();
        int total = 0;
        for (Long wavId : wavIds) {
            // 웨이브 행 락 — 할당 실행·해제, 편성 변경, 취소, 피킹 실행과의 직렬화 지점.
            // 락 순서는 웨이브(오름차순) → 재고 한 방향이고, 발행은 재고를 건드리지 않아 앞 단계뿐이다.
            OutbWave wave = lockWave(wavId);
            requireIssuable(wave, additional);

            // 출고확정된 주문은 빼고 본다 — 전량 미출고로 확정된 주문은 할당 0건인 채 SHIPPED라,
            // 안 빼면 그 웨이브의 추가 발행이 「할당 0건 주문」에 영영 막힌다
            List<OutbOrder> orders = outbOrderRepository.findByWaveId(wavId).stream()
                    .filter(order -> order.getStatus() != OutbStatus.SHIPPED)
                    .toList();
            if (orders.isEmpty()) {
                throw new IllegalArgumentException("웨이브에 편성된 주문이 없습니다: " + wave.getWavNo());
            }
            List<OutbAlloc> allocs = outbAllocRepository
                    .findIssuableByWaveId(wavId, PikngTaskStatus.CANCELLED);
            if (allocs.isEmpty()) {
                throw new IllegalStateException(additional
                        ? "추가로 발행할 할당이 없습니다 — 먼저 할당하세요: " + wave.getWavNo()
                        : "발행할 할당이 없습니다: " + wave.getWavNo());
            }

            // 발행 가드 — 할당 0건 주문이 있으면 웨이브째 차단. 부분할당(라인 잔량)은 막지 않는다.
            // 판정 재료는 발행 대상이 아니라 웨이브의 전 할당이다 — 추가 발행에서 이미 지시가 나간
            // 주문을 「할당 0건」으로 오판하지 않기 위해서다.
            Set<Long> allocOrderIds = new HashSet<>(outbAllocRepository.findAllocatedOrderIdsByWaveId(wavId));
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

            // 추가분은 기존 순번 뒤에 붙는다 — 「1차 동선을 다 돈 뒤 추가분」이라 현장 동선과 맞는다
            int seq = additional ? pikngTaskRepository.findMaxSrtSeqByWaveId(wavId) : 0;
            List<PikngTask> tasks = new ArrayList<>(sorted.size());
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
            if (!additional) {
                wave.issue();
            }

            results.add(new PikngIssueResponse.WaveResult(wave.getWavNo(), tasks.size()));
            total += tasks.size();
        }
        return new PikngIssueResponse(results.size(), total, results);
    }

    /**
     * 최초 발행은 PLANNED에서만, 추가 발행은 ISSUED에서만. 두 진입이 서로의 자리를 침범하지
     * 않게 반대 방향으로 잠근다 — 「발행이 두 번 일어나는 것」과 「추가분이 최초 발행을 대신하는 것」은
     * 다른 조작이고, 웨이브 상태 전이(PLANNED → ISSUED)는 여전히 최초 발행 한 번뿐이다.
     */
    private static void requireIssuable(OutbWave wave, boolean additional) {
        if (additional && wave.getStatus() != WaveStatus.ISSUED) {
            throw new IllegalStateException("아직 피킹지시가 발행되지 않은 웨이브입니다 — 먼저 발행하세요: "
                    + wave.getWavNo());
        }
        if (!additional && wave.getStatus() != WaveStatus.PLANNED) {
            throw new IllegalStateException("이미 피킹지시가 발행된 웨이브입니다 — 추가 발행을 쓰세요: "
                    + wave.getWavNo());
        }
    }

    // ── 지시취소 ──────────────────────────────────────────────────────────────

    /**
     * 지시취소 — 재고 무변동(발행이 재고에 손대지 않았으므로)이고 삭제가 아니라 CANCELLED 전이다.
     * 요청이 채운 쪽으로 갈린다: {@code wavIds}면 웨이브 단위, {@code taskIds}면 지시 단위.
     */
    @Transactional
    public PikngCancelResponse cancel(PikngCancelRequest request) {
        List<Long> wavIds = distinct(request.getWavIds());
        List<Long> taskIds = distinct(request.getTaskIds());
        if (wavIds.isEmpty() == taskIds.isEmpty()) {
            throw new IllegalArgumentException(wavIds.isEmpty()
                    ? "취소할 웨이브 또는 지시를 선택하세요."
                    : "웨이브와 지시를 함께 지정할 수 없습니다 — 취소 단위를 하나만 고르세요.");
        }
        return wavIds.isEmpty() ? cancelTasks(taskIds) : cancelWaves(wavIds);
    }

    /** 웨이브 단위 — 발행의 역조작이라 전부-아니면-전무다. 실적이 하나라도 있으면 지시 단위로 보낸다 */
    private PikngCancelResponse cancelWaves(List<Long> wavIds) {
        List<PikngCancelResponse.WaveResult> results = new ArrayList<>();
        int total = 0;
        for (Long wavId : wavIds) {
            OutbWave wave = lockWave(wavId);
            requireIssued(wave);
            List<PikngTask> live = liveTasks(wavId);
            if (live.isEmpty()) {
                throw new IllegalStateException("취소할 지시가 없습니다: " + wave.getWavNo());
            }
            long picked = live.stream().mapToLong(PikngTask::getCmplQty).sum();
            if (picked > 0) {
                throw new IllegalStateException("피킹이 시작된 웨이브는 발행을 통째로 취소할 수 없습니다"
                        + " (피킹 " + picked + ") — 아직 한 개도 집지 않은 지시만 골라 취소하세요: "
                        + wave.getWavNo());
            }
            live.forEach(PikngTask::cancel);
            wave.cancelIssue();

            results.add(new PikngCancelResponse.WaveResult(wave.getWavNo(), live.size()));
            total += live.size();
        }
        return new PikngCancelResponse(results.size(), total, results);
    }

    /**
     * 지시 단위 — 실적 판정은 {@link PikngTask#cancel()}이 대상 지시 자신에 대해 한다.
     * 취소 후 살아 있는 지시가 0건인 웨이브만 PLANNED로 돌린다(완료 지시가 남으면 그대로 ISSUED) —
     * 「살아 있는 지시 없는 ISSUED 웨이브」를 만들지 않는 것은 두 진입의 공통 책임이다.
     */
    private PikngCancelResponse cancelTasks(List<Long> taskIds) {
        // ① 웨이브 행 락을 지시보다 먼저 잡는다 — 순서(웨이브 오름차순)뿐 아니라 「락 뒤에 읽는다」가
        //    함께 필요하다. 먼저 읽으면 락 대기 중 커밋된 실적이 반영되지 않아 낡은 cmplQty 0이
        //    취소 가드를 통과하고, flush가 그 0을 되써서 항등식이 깨진다 (실행·결품 종결과 같은 순서)
        Map<Long, OutbWave> waves = new LinkedHashMap<>();
        for (Long wavId : pikngTaskRepository.findWaveIdsByTaskIds(taskIds).stream().sorted().toList()) {
            OutbWave wave = lockWave(wavId);
            requireIssued(wave);
            waves.put(wavId, wave);
        }

        List<PikngTask> tasks = pikngTaskRepository.findAllWithDetailsByIds(taskIds);
        if (tasks.size() != taskIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 지시가 포함돼 있습니다.");
        }
        Map<Long, Integer> cancelledByWave = new LinkedHashMap<>();
        for (PikngTask task : tasks) {
            task.cancel();
            cancelledByWave.merge(task.getWave().getId(), 1, Integer::sum);
        }

        // ② 남은 지시를 세기 전에 전이를 반영한다 — 안 하면 방금 취소한 행까지 살아 있는 것으로 센다
        pikngTaskRepository.flush();

        List<PikngCancelResponse.WaveResult> results = new ArrayList<>();
        for (Map.Entry<Long, OutbWave> entry : waves.entrySet()) {
            OutbWave wave = entry.getValue();
            if (liveTasks(entry.getKey()).isEmpty()) {
                wave.cancelIssue();
            }
            results.add(new PikngCancelResponse.WaveResult(
                    wave.getWavNo(), cancelledByWave.get(entry.getKey())));
        }
        return new PikngCancelResponse(results.size(), taskIds.size(), results);
    }

    private List<PikngTask> liveTasks(Long wavId) {
        return pikngTaskRepository.findByWaveIdAndStatusNot(wavId, PikngTaskStatus.CANCELLED);
    }

    private static void requireIssued(OutbWave wave) {
        if (wave.getStatus() != WaveStatus.ISSUED) {
            throw new IllegalStateException("피킹지시가 발행되지 않은 웨이브입니다: " + wave.getWavNo());
        }
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
