package com.project.wmsback.outbound.service;

import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.RefDocTyp;
import com.project.wmsback.inventory.service.InvDocRef;
import com.project.wmsback.inventory.service.InvStore;
import com.project.wmsback.inventory.service.RsnValidator;
import com.project.wmsback.outbound.dto.PickingSearchCond;
import com.project.wmsback.outbound.dto.PickingWaveResponse;
import com.project.wmsback.outbound.dto.PikngCloseShortRequest;
import com.project.wmsback.outbound.dto.PikngCloseShortResponse;
import com.project.wmsback.outbound.dto.PikngExecuteRequest;
import com.project.wmsback.outbound.dto.PikngExecuteResponse;
import com.project.wmsback.outbound.entity.OutbAlloc;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbStatus;
import com.project.wmsback.outbound.entity.PikngAcrst;
import com.project.wmsback.outbound.entity.PikngTask;
import com.project.wmsback.outbound.entity.PikngTaskStatus;
import com.project.wmsback.outbound.repository.OutbAllocRepository;
import com.project.wmsback.outbound.repository.OutbWaveRepository;
import com.project.wmsback.outbound.repository.PikngAcrstRepository;
import com.project.wmsback.outbound.repository.PikngTaskRepository;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.repository.LocRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 피킹 실행 — <b>발행된 지시에 실적 수량을 입력해 재고를 보관 → SHIP-STAGE로 실제 이동시킨다.</b>
 * 출고 흐름에서 재고가 물리적으로 움직이는 첫 지점이다 (tx_typ = PICK, 이력 2행).
 *
 * <p>행마다 부분 수량을 허용하고(잔량은 재피킹으로 소진) 요청은 <b>한 트랜잭션</b>이다 —
 * 한 행이라도 걸리면 전량 롤백된다 (이동확정과 같은 판단).
 *
 * <p><b>장부상 잔량은 항상 예약돼 있지만, 선반에 실물이 있다는 보장은 아니다.</b>
 * {@code ck_inv_qty(aloc + hld <= on_hand)}가 지키는 것은 「장부에 그렇게 적혀 있다」이고,
 * 장부와 실물이 어긋나는 것 자체가 창고의 일상이다. 그래서 잔량을 실재고와 비교해 줄여
 * 보여주지는 않되(예약이 이미 그 자리를 잡고 있다), 끝내 못 집은 잔량을 닫는 출구로
 * {@link #closeShort}를 둔다.
 *
 * <p>실행 1회마다 세 곳을 함께 갱신한다 — 항등식
 * {@code pikng_task.cmpl_qty = outb_alloc.pikng_qty = SUM(pikng_acrst.pikng_qty)}
 * (지시 문서의 진행 / 주문 도메인의 진행 / 실행 원장).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PikngService {

    /**
     * 출고 스테이징 로케이션 코드 — RCV-STAGE가 4곳에 중복된 것과 같은 성격의 상수.
     * 바꿀 때 CLAUDE.md 「RCV-STAGE」 문단의 목록과 함께 볼 것.
     */
    private static final String SHIP_STAGING_LOC_CD = "SHIP-STAGE";

    /** 결품사유 공통코드 그룹. 보류·조정 사유와 같은 형태(ETC일 때만 자유 텍스트) */
    private static final String SHOTGE_RSN_GRP_CD = "SHOTGE_RSN";

    private final PikngTaskRepository pikngTaskRepository;
    private final PikngAcrstRepository pikngAcrstRepository;
    private final OutbAllocRepository outbAllocRepository;
    private final OutbWaveRepository outbWaveRepository;
    private final LocRepository locRepository;
    private final InvStore invStore;
    private final RsnValidator rsnValidator;

    // ── 조회 ─────────────────────────────────────────────────────────────────

    public List<PickingWaveResponse> searchWaves(PickingSearchCond cond) {
        return pikngTaskRepository.searchPickingWaves(cond);
    }

    // ── 실행 ─────────────────────────────────────────────────────────────────

    @Transactional
    public PikngExecuteResponse execute(PikngExecuteRequest request) {
        Map<Long, Long> qtyByTaskId = validated(request);

        // ① 웨이브 행 락 — 지시취소(실적 0 검증)·발행과의 직렬화. 락 순서는
        //    웨이브(오름차순) → 재고(재고 키 오름차순, InvStore) 한 방향이다.
        pikngTaskRepository.findWaveIdsByTaskIds(qtyByTaskId.keySet()).stream().sorted()
                .forEach(this::lockWave);

        // ② 지시 로드 + 검증 — 상태·잔량. 잔량 초과는 DB(ck_pikng_task_qty·ck_aloc_qty)가
        //    최후 방어하지만 메시지를 주려면 여기서 먼저 잡아야 한다
        List<PikngTask> tasks = pikngTaskRepository.findAllWithDetailsByIds(qtyByTaskId.keySet());
        if (tasks.size() != qtyByTaskId.size()) {
            throw new IllegalArgumentException("존재하지 않는 지시가 포함돼 있습니다.");
        }
        for (PikngTask task : tasks) {
            long qty = qtyByTaskId.get(task.getId());
            if (task.getStatus() != PikngTaskStatus.DIRECTED) {
                throw new IllegalArgumentException("취소되었거나 완료된 지시입니다 ("
                        + task.getStatus().getLabel() + "): " + rowName(task));
            }
            if (qty > task.remainingQty()) {
                throw new IllegalArgumentException("지시 잔량을 초과했습니다 (잔량 " + task.remainingQty()
                        + ", 요청 " + qty + "): " + rowName(task));
            }
        }

        // ③ 재고 락 — 할당이 예약해 둔 행이라 잔량 > 0이면 반드시 존재한다.
        //    지연 프록시의 id는 DB를 타지 않으므로 락 창구가 이 id로 처음 읽는다 (낡은 수량 없음)
        Set<Long> invIds = new LinkedHashSet<>();
        tasks.forEach(task -> invIds.add(task.getOutbAlloc().getInv().getId()));
        Map<Long, Inv> locked = invStore.lockAllByIds(invIds);
        for (Long invId : invIds) {
            if (!locked.containsKey(invId)) {
                throw new IllegalStateException("피킹할 재고가 없습니다 (할당 예약과 재고가 어긋났습니다): " + invId);
            }
        }

        Loc shipStage = locRepository.findByLocCd(SHIP_STAGING_LOC_CD)
                .orElseThrow(() -> new IllegalStateException("출고 스테이징 로케이션(SHIP-STAGE)이 없습니다."));

        // ④ 실행 — 실물 이동(PICK 2행 + 예약 소진) + 지시·할당·실적 세 곳 동시 갱신 (항등식)
        Map<Long, OutbStatus> beforeByOrder = new HashMap<>();
        Set<OutbOrder> touched = new LinkedHashSet<>();
        long totalQty = 0;
        int doneCount = 0;
        for (PikngTask task : tasks) {
            long qty = qtyByTaskId.get(task.getId());
            OutbAlloc alloc = task.getOutbAlloc();
            OutbOrder order = alloc.getOutbLine().getOutbOrder();
            beforeByOrder.putIfAbsent(order.getId(), order.getStatus());

            invStore.pick(locked.get(alloc.getInv().getId()), shipStage, qty,
                    InvDocRef.of(RefDocTyp.OUTBOUND, order.getOutbNo()));
            task.execute(qty);
            alloc.addPikngQty(qty);
            pikngAcrstRepository.save(PikngAcrst.builder()
                    .pikngTask(task).prod(task.getProd()).fromLoc(task.getFromLoc()).lot(task.getLot())
                    .pikngQty(qty).build());

            touched.add(order);
            totalQty += qty;
            if (task.getStatus() == PikngTaskStatus.DONE) {
                doneCount++;
            }
        }

        // ⑤ 주문 상태 재산출 — 첫 실적이면 PICKING, 전 할당 소진이면 PICKED.
        //    판정 기준은 주문수량이 아니라 할당수량이다 — 부분할당 주문은 할당분만 집품되면
        //    PICKED가 되고 미할당 잔량은 부족 출고로 진행한다 (백오더 없음)
        List<PikngExecuteResponse.OrderChange> changes = new ArrayList<>();
        for (OutbOrder order : touched) {
            outbAllocRepository.recalcStatus(order);
            if (order.getStatus() != beforeByOrder.get(order.getId())) {
                changes.add(new PikngExecuteResponse.OrderChange(order.getOutbNo(),
                        order.getStatus().name(), order.getStatus().getLabel()));
            }
        }
        return new PikngExecuteResponse(tasks.size(), totalQty, doneCount, changes);
    }

    // ── 결품 종결 ─────────────────────────────────────────────────────────────

    /**
     * 결품 종결 — <b>시킨 만큼 실물이 없어 끝내 못 집은 잔량을 결품으로 닫고, 그만큼의 예약을 푼다.</b>
     *
     * <p>이 경로가 없으면 「30 지시 / 25 집품 / 5는 영영 안 나옴」이 빠져나갈 문이 하나도 없다 —
     * 지시는 {@code cmpl != drct}라 DONE이 못 되고, 실적이 있어 취소도 안 되고, 할당해제는
     * {@code pikng_qty = 0}만 열리고, 재고조사는 예약을 먼저 풀라고 하고, 주문은 전 할당이
     * 소진되지 않아 PICKED가 못 된다. 실물 없는 예약이 영구히 남아 다른 주문도 그 재고를 못 쓴다.
     *
     * <p>한 트랜잭션에서 네 곳이 함께 움직인다 — {@code inv.aloc_qty} 반환 ·
     * {@code outb_alloc.aloc_qty} 하향 · {@code pikng_task.drct_qty} 하향 + DONE ·
     * 주문 상태 재산출. 항등식({@code drct_qty = aloc_qty} / {@code cmpl_qty = pikng_qty})은
     * 양쪽을 같은 값으로 낮추므로 그대로 유지된다.
     *
     * <p><b>실물 없는 장부 수량은 여기서 건드리지 않는다.</b> 예약이 풀리면 가용이 그만큼 늘어
     * 다음 할당이 그 수량을 다시 집어갈 수 있지만, 장부 수량을 줄이는 경로는 재고조사 하나라는
     * 원칙을 여기서 열지 않는다 — 종결로 예약이 풀린 시점부터 그 재고조사가 정상 동작한다
     * (실사 확정의 {@code 실사수량 >= aloc + hld} 검사를 막고 있던 것이 바로 이 예약이었다).
     */
    @Transactional
    public PikngCloseShortResponse closeShort(PikngCloseShortRequest request) {
        Map<Long, PikngCloseShortRequest.Item> itemByTaskId = validatedCloseShort(request);
        // 사유코드도 락 전에 본다 — 사유 미선택 하나로 잡아 둔 락을 들고 롤백할 일이 아니다
        Map<Long, String> rsnDscrByTaskId = new HashMap<>();
        itemByTaskId.forEach((taskId, item) -> rsnDscrByTaskId.put(taskId,
                rsnValidator.validate(SHOTGE_RSN_GRP_CD, "결품사유", item.getRsnCd(), item.getRsnDscr())));

        // ① 웨이브 행 락 — 피킹 실행과 같은 순서(웨이브 오름차순 → 재고 키 오름차순)로 잡는다.
        //    InvMovService의 취소가 재고를 먼저 잠그는 것과 갈리는 지점이고, 이유는 출고에는
        //    문서 헤더(웨이브)가 있어 전역 락 계층의 앞자리를 웨이브가 차지하기 때문이다
        pikngTaskRepository.findWaveIdsByTaskIds(itemByTaskId.keySet()).stream().sorted()
                .forEach(this::lockWave);

        // ② 지시 로드 + 상태 검증. 실적 0은 이 경로가 아니라 지시 단위 취소가 덮는다
        List<PikngTask> tasks = pikngTaskRepository.findAllWithDetailsByIds(itemByTaskId.keySet());
        if (tasks.size() != itemByTaskId.size()) {
            throw new IllegalArgumentException("존재하지 않는 지시가 포함돼 있습니다.");
        }
        for (PikngTask task : tasks) {
            if (task.getStatus() != PikngTaskStatus.DIRECTED) {
                throw new IllegalArgumentException("취소되었거나 완료된 지시입니다 ("
                        + task.getStatus().getLabel() + "): " + rowName(task));
            }
            if (task.getCmplQty() == 0L) {
                throw new IllegalArgumentException("피킹 실적이 없는 지시입니다 — 피킹지시 화면의 지시취소로 되돌리세요: "
                        + rowName(task));
            }
        }

        // ③ 재고 락 — 예약을 되돌릴 행이다. 실행과 같은 창구로 잠가 순서가 하나로 유지된다
        Set<Long> invIds = new LinkedHashSet<>();
        tasks.forEach(task -> invIds.add(task.getOutbAlloc().getInv().getId()));
        Map<Long, Inv> locked = invStore.lockAllByIds(invIds);

        // ④ 종결 — 예약 반환 + 지시·할당 하향
        Map<Long, OutbStatus> beforeByOrder = new HashMap<>();
        Set<OutbOrder> touched = new LinkedHashSet<>();
        long totalShotge = 0;
        for (PikngTask task : tasks) {
            PikngCloseShortRequest.Item item = itemByTaskId.get(task.getId());
            String rsnDscr = rsnDscrByTaskId.get(task.getId());
            OutbAlloc alloc = task.getOutbAlloc();
            OutbOrder order = alloc.getOutbLine().getOutbOrder();
            long remaining = task.remainingQty();

            Inv inv = locked.get(alloc.getInv().getId());
            if (inv == null) {
                throw new IllegalStateException("결품 종결할 재고가 없습니다 (할당 예약과 재고가 어긋났습니다): "
                        + rowName(task));
            }
            if (inv.getAlocQty() < remaining) {
                throw new IllegalStateException("예약 잔량보다 재고의 예약 수량이 적습니다 (정합성 오류 — 예약 "
                        + inv.getAlocQty() + " / 잔여 " + remaining + "): " + rowName(task));
            }

            beforeByOrder.putIfAbsent(order.getId(), order.getStatus());
            invStore.release(inv, remaining);
            task.closeShort(item.getRsnCd(), rsnDscr);
            alloc.closeShort();
            touched.add(order);
            totalShotge += remaining;
        }

        // ⑤ 주문 상태 재산출 — 실행 ⑤와 같은 함수를 같은 재료로 부른다
        List<PikngExecuteResponse.OrderChange> changes = new ArrayList<>();
        for (OutbOrder order : touched) {
            outbAllocRepository.recalcStatus(order);
            if (order.getStatus() != beforeByOrder.get(order.getId())) {
                changes.add(new PikngExecuteResponse.OrderChange(order.getOutbNo(),
                        order.getStatus().name(), order.getStatus().getLabel()));
            }
        }
        return new PikngCloseShortResponse(tasks.size(), totalShotge, changes);
    }

    /** 결품 종결 요청 검증 — 지시 지정·중복 없음. 사유코드는 그룹 대조가 필요해 {@link #closeShort}가 락 전에 본다 */
    private static Map<Long, PikngCloseShortRequest.Item> validatedCloseShort(PikngCloseShortRequest request) {
        List<PikngCloseShortRequest.Item> items = request.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("결품 종결할 지시를 선택하세요.");
        }
        Map<Long, PikngCloseShortRequest.Item> itemByTaskId = new LinkedHashMap<>();
        for (PikngCloseShortRequest.Item item : items) {
            if (item.getPikngTaskId() == null) {
                throw new IllegalArgumentException("결품 종결할 지시를 지정하세요.");
            }
            if (itemByTaskId.putIfAbsent(item.getPikngTaskId(), item) != null) {
                throw new IllegalArgumentException("같은 지시가 중복으로 지정됐습니다: " + item.getPikngTaskId());
            }
        }
        return itemByTaskId;
    }

    /** 요청 형식 검증 — 지시 지정·수량 1 이상·중복 없음. 순서를 지키는 맵으로 돌려준다 */
    private static Map<Long, Long> validated(PikngExecuteRequest request) {
        List<PikngExecuteRequest.Item> items = request.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("피킹할 지시를 선택하세요.");
        }
        Map<Long, Long> qtyByTaskId = new LinkedHashMap<>();
        for (PikngExecuteRequest.Item item : items) {
            if (item.getPikngTaskId() == null) {
                throw new IllegalArgumentException("피킹할 지시를 지정하세요.");
            }
            if (item.getQty() == null || item.getQty() < 1) {
                throw new IllegalArgumentException("피킹수량은 1 이상이어야 합니다.");
            }
            if (qtyByTaskId.putIfAbsent(item.getPikngTaskId(), item.getQty()) != null) {
                throw new IllegalArgumentException("같은 지시가 중복으로 지정됐습니다: " + item.getPikngTaskId());
            }
        }
        return qtyByTaskId;
    }

    /** 오류 메시지에 쓸 행 이름 — 사용자가 화면에서 찾을 수 있는 값(출고번호/상품)으로 짚어준다 */
    private static String rowName(PikngTask task) {
        return task.getOutbAlloc().getOutbLine().getOutbOrder().getOutbNo()
                + " / " + task.getProd().getProdCd();
    }

    private void lockWave(Long wavId) {
        outbWaveRepository.findByIdForUpdate(wavId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 웨이브입니다: " + wavId));
    }
}
