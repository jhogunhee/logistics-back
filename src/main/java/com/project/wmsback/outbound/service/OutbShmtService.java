package com.project.wmsback.outbound.service;

import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.RefDocTyp;
import com.project.wmsback.inventory.service.InvDocRef;
import com.project.wmsback.inventory.service.InvKey;
import com.project.wmsback.inventory.service.InvStore;
import com.project.wmsback.outbound.dto.ShmtConfirmRequest;
import com.project.wmsback.outbound.dto.ShmtConfirmResponse;
import com.project.wmsback.outbound.dto.ShmtOrderResponse;
import com.project.wmsback.outbound.dto.ShmtSearchCond;
import com.project.wmsback.outbound.dto.ShmtWaveResponse;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbStatus;
import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.entity.PikngTask;
import com.project.wmsback.outbound.entity.PikngTaskStatus;
import com.project.wmsback.outbound.entity.WaveStatus;
import com.project.wmsback.outbound.repository.OutbOrderRepository;
import com.project.wmsback.outbound.repository.OutbWaveRepository;
import com.project.wmsback.outbound.repository.PikngTaskRepository;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.repository.LocRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 출고확정 — <b>피킹이 끝난 주문을 닫고 SHIP-STAGE의 실물과 예약을 함께 소진한다</b>(tx_typ = SHIP, 1행).
 * 출고 흐름에서 재고가 창고 밖으로 나가는 유일한 지점이고, 주문(SHIPPED)과 웨이브(CLOSED)가 종료되는
 * 자리다. 입고의 {@code ReceivingService.confirm()}과 같은 위치 — 사람이 눌러 닫는 종결이고 자동 전이가 없다.
 *
 * <p>대상은 주문 단위이고 통과하는 상태는 둘이다. <b>PICKED</b>는 정상 확정이라 할당마다
 * {@code pikng_qty}만큼 스테이징에서 반출하고, <b>CREATED</b>는 전량 미출고 확정이라 재고 처리 없이
 * 닫기만 한다 — 할당이 0건이라 선점한 것이 없다. 후자가 발행된 웨이브에 갇힌 주문의 마지막 출구다
 * (지시취소 → 할당해제로 비워졌는데 재고가 없어 재할당이 안 되는 주문).
 *
 * <p>실적은 따로 쌓지 않는다 — 「실적 = inv_hist」 원칙의 본령이다(예외는 보류·피킹 둘).
 * 주문별 출하는 {@code outb_order.shmt_dt} + {@code inv_hist}의 SHIP 행(rfn_doc_no = 출고번호)이고,
 * 결품은 {@code odr_qty − Σpikng_qty}로 언제든 파생된다.
 *
 * <p>요청은 <b>한 트랜잭션</b>이다 — 여러 주문을 골라도 하나가 걸리면 전부 롤백(피킹 실행과 같은 판단).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutbShmtService {

    /**
     * 출고 스테이징 로케이션 코드 — {@code PikngService}와 같은 상수. 피킹이 쌓는 곳을 여기서 비운다.
     * 바꿀 때 CLAUDE.md 「RCV-STAGE」 문단의 목록과 함께 볼 것.
     */
    private static final String SHIP_STAGING_LOC_CD = "SHIP-STAGE";

    private final OutbOrderRepository outbOrderRepository;
    private final PikngTaskRepository pikngTaskRepository;
    private final OutbWaveRepository outbWaveRepository;
    private final LocRepository locRepository;
    private final InvStore invStore;

    // ── 조회 ─────────────────────────────────────────────────────────────────

    public List<ShmtWaveResponse> searchWaves(ShmtSearchCond cond) {
        return outbOrderRepository.searchShmtWaves(cond);
    }

    public List<ShmtOrderResponse> orders(Long wavId) {
        return outbOrderRepository.shmtOrders(wavId);
    }

    // ── 확정 ─────────────────────────────────────────────────────────────────

    @Transactional
    public ShmtConfirmResponse confirm(ShmtConfirmRequest request) {
        List<Long> orderIds = distinct(request.getOutbOrderIds());
        if (orderIds.isEmpty()) {
            throw new IllegalArgumentException("출고확정할 주문을 선택하세요.");
        }

        // ① 주문 로드(웨이브 포함) → 웨이브 행 락. 주문을 바꾸는 모든 조작(할당·발행·피킹·종결·해제)이
        //    웨이브 락을 첫 락으로 잡으므로 주문 행 락은 따로 잡지 않는다. 락 순서는
        //    웨이브(오름차순) → 재고(재고 키 오름차순, InvStore) 한 방향 — 기존 계층 그대로다
        List<OutbOrder> orders = outbOrderRepository.findAllWithWaveByIds(orderIds);
        if (orders.size() != orderIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 주문이 포함돼 있습니다.");
        }
        Map<Long, OutbWave> waves = new LinkedHashMap<>();
        for (Long wavId : waveIdsAscending(orders)) {
            OutbWave wave = outbWaveRepository.findByIdForUpdate(wavId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 웨이브입니다: " + wavId));
            if (wave.getStatus() != WaveStatus.ISSUED) {
                throw new IllegalStateException("피킹지시가 발행된 웨이브의 주문만 출고확정할 수 있습니다 ("
                        + wave.getStatus().getLabel() + "): " + wave.getWavNo());
            }
            waves.put(wavId, wave);
        }

        // ② PICKED 주문의 살아 있는 지시 → 스테이징 키(상품 · SHIP-STAGE · Lot) → 재고 락.
        //    할당이 아니라 지시에서 읽는다 — 할당의 보관 inv 행은 전량 집품으로 지워졌을 수 있고,
        //    지시는 재고 키를 발행 시점 스냅샷으로 들고 있다. 수량은 cmpl_qty(= outb_alloc.pikng_qty)
        List<Long> pickedOrderIds = orders.stream()
                .filter(order -> order.getStatus() == OutbStatus.PICKED)
                .map(OutbOrder::getId).toList();
        List<PikngTask> tasks = pickedOrderIds.isEmpty() ? List.of()
                : pikngTaskRepository.findLiveWithDetailsByOrderIds(pickedOrderIds, PikngTaskStatus.CANCELLED);
        Loc shipStage = locRepository.findByLocCd(SHIP_STAGING_LOC_CD)
                .orElseThrow(() -> new IllegalStateException("출고 스테이징 로케이션(SHIP-STAGE)이 없습니다."));
        Map<InvKey, Inv> staging = invStore.lockAll(tasks.stream()
                .map(task -> stagingKey(task, shipStage)).toList());

        // ③ 반출 — 지시마다 집품 수량만큼 실물·예약을 함께 소진 (SHIP 1행)
        long shmtQty = 0;
        for (PikngTask task : tasks) {
            long qty = task.getCmplQty();
            if (qty == 0) {
                // PICKED 주문에 실적 0 지시는 없다(미소진 할당이 남으면 PICKING이다) — 있다면 정합성 오류
                throw new IllegalStateException("피킹완료 주문에 집품되지 않은 지시가 있습니다 (정합성 오류): "
                        + rowName(task));
            }
            Inv inv = staging.get(stagingKey(task, shipStage));
            if (inv == null) {
                throw new IllegalStateException("출고 스테이징에 재고가 없습니다 (피킹과 재고가 어긋났습니다): "
                        + rowName(task));
            }
            if (inv.getAlocQty() < qty || inv.getOnHandQty() < qty) {
                throw new IllegalStateException("출고 스테이징의 재고·예약이 출하 수량보다 적습니다 (정합성 오류 — 실물 "
                        + inv.getOnHandQty() + " / 예약 " + inv.getAlocQty() + " / 출하 " + qty + "): " + rowName(task));
            }
            invStore.ship(inv, qty, InvDocRef.of(RefDocTyp.OUTBOUND, orderOf(task).getOutbNo()));
            shmtQty += qty;
        }

        // ④ 주문 종결 — 가드(PICKED · CREATED만 통과)는 전이 메서드가 갖고 있다. 결품 = 주문수량 − 집품수량
        Map<Long, Long> pickedByOrder = new LinkedHashMap<>();
        for (PikngTask task : tasks) {
            pickedByOrder.merge(orderOf(task).getId(), task.getCmplQty(), Long::sum);
        }
        long shotgeQty = 0;
        int noStockCount = 0;
        for (OutbOrder order : orders) {
            if (order.getStatus() == OutbStatus.CREATED) {
                noStockCount++;
            }
            long odrQty = order.getLines().stream().mapToLong(line -> line.getOdrQty()).sum();
            shotgeQty += odrQty - pickedByOrder.getOrDefault(order.getId(), 0L);
            order.ship();
        }

        // ⑤ 웨이브 종료 — 소속 주문이 전부 SHIPPED면 CLOSED. 방금 바꾼 상태가 세어지도록 먼저 flush한다
        //    (할당해제가 countBy 전에 flush하는 것과 같은 이유)
        outbOrderRepository.flush();
        List<String> closedWavNos = new ArrayList<>();
        for (OutbWave wave : waves.values()) {
            boolean allShipped = outbOrderRepository.findByWaveId(wave.getId()).stream()
                    .allMatch(order -> order.getStatus() == OutbStatus.SHIPPED);
            if (allShipped) {
                wave.close();
                closedWavNos.add(wave.getWavNo());
            }
        }
        return new ShmtConfirmResponse(orders.size(), shmtQty, shotgeQty, noStockCount, closedWavNos);
    }

    /** 스테이징 키 — 지시의 재고 키 스냅샷(상품 · Lot) + SHIP-STAGE */
    private static InvKey stagingKey(PikngTask task, Loc shipStage) {
        return new InvKey(task.getProd().getId(), shipStage.getId(), task.getLot().getId());
    }

    private static OutbOrder orderOf(PikngTask task) {
        return task.getOutbAlloc().getOutbLine().getOutbOrder();
    }

    private static Set<Long> waveIdsAscending(List<OutbOrder> orders) {
        Set<Long> ids = new TreeSet<>();
        for (OutbOrder order : orders) {
            if (order.getWave() != null) {
                ids.add(order.getWave().getId());
            }
        }
        return ids;
    }

    private static String rowName(PikngTask task) {
        return orderOf(task).getOutbNo() + " / " + task.getProd().getProdCd();
    }

    private static List<Long> distinct(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        Set<Long> unique = new LinkedHashSet<>(ids);
        unique.remove(null);
        return new ArrayList<>(unique);
    }
}
