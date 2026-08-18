package com.project.wmsback.outbound.service;

import com.project.mdm.nbr.service.NbrService;
import com.project.wmsback.outbound.dto.OutbWaveOrdersRequest;
import com.project.wmsback.outbound.dto.OutbWaveResponse;
import com.project.wmsback.outbound.dto.OutbWaveSearchCond;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbStatus;
import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.entity.WavRegTyp;
import com.project.wmsback.outbound.repository.OutbOrderRepository;
import com.project.wmsback.outbound.repository.OutbWaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 출고 웨이브 편성. 여러 출고 주문을 <b>피킹지시 발행 단위</b>인 웨이브로 묶는다 — 여기까지는 편성만.
 * 할당은 웨이브를 대상으로 실행하되 웨이브 상태를 바꾸지 않으며 별도(할당 서비스)에서 다룬다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutbWaveService {

    private final OutbWaveRepository outbWaveRepository;
    private final OutbOrderRepository outbOrderRepository;
    private final NbrService nbrService;

    public List<OutbWaveResponse> list(OutbWaveSearchCond cond) {
        return outbWaveRepository.search(cond).stream()
                .map(OutbWaveResponse::from)
                .toList();
    }

    public OutbWaveResponse detail(Long wavId) {
        OutbWave wave = outbWaveRepository.findById(wavId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 웨이브입니다: " + wavId));
        return OutbWaveResponse.from(wave);
    }

    /** 웨이브 생성. 웨이브번호는 생성일 + 시퀀스로 채번 (예: WV-20260718-001). 초기 주문 목록은 선택 */
    @Transactional
    public Long create(OutbWaveOrdersRequest req) {
        String wavNo = nbrService.issue("OUTB_WAV_NO", LocalDate.now());

        OutbWave wave = outbWaveRepository.save(OutbWave.builder().wavNo(wavNo).build());
        assignOrders(wave, req.getOrderIds());
        return wave.getId();
    }

    /** 주문 추가 편성. 웨이브 행 락 — 해체·할당 실행과 직렬화해 삭제된 웨이브에 담기는 것을 막는다 */
    @Transactional
    public void addOrders(Long wavId, OutbWaveOrdersRequest req) {
        OutbWave wave = lockWave(wavId);
        wave.assertPlanned();
        assignOrders(wave, req.getOrderIds());
    }

    /**
     * 편성 해제. 담기(addOrders)와 마찬가지로 목록을 받아 한 트랜잭션에서 처리한다 —
     * 화면이 여러 주문을 체크해 한 번에 빼는데 1건씩 호출하면 중간 실패가 부분 해제로 남는다.
     * 주문 자체는 지워지지 않고 미편성(CREATED)으로 되돌아간다.
     */
    @Transactional
    public void unassignOrders(Long wavId, OutbWaveOrdersRequest req) {
        // 웨이브 행 락 — 같은 웨이브의 편성 변경·할당 실행과 직렬화 (락 순서: 웨이브 → 주문)
        OutbWave wave = lockWave(wavId);
        wave.assertPlanned();
        if (req.getOrderIds() == null || req.getOrderIds().isEmpty()) {
            throw new IllegalArgumentException("편성 해제할 주문을 선택하세요.");
        }
        for (Long orderId : req.getOrderIds()) {
            OutbOrder order = outbOrderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 출고 주문입니다: " + orderId));
            if (order.getWave() == null || !order.getWave().getId().equals(wavId)) {
                throw new IllegalArgumentException("이 웨이브에 편성된 주문이 아닙니다: " + order.getOutbNo());
            }
            order.unassignWave();
        }
    }

    /** 웨이브 해체. 소속 주문을 모두 편성 해제하고 웨이브를 삭제한다 (PLANNED만) */
    @Transactional
    public void disband(Long wavId) {
        // 웨이브 행 락 — 담기(addOrders)와 직렬화해 삭제 중인 웨이브에 주문이 들어오는 것을 막는다
        OutbWave wave = lockWave(wavId);
        wave.assertPlanned();
        List<OutbOrder> orders = outbOrderRepository.findByWaveId(wavId);
        // 해제 가드는 주문 단위라 메시지도 주문을 가리킨다 — 해체는 웨이브 단위 조작이므로 여기서 먼저
        // 웨이브 관점으로 걸러낸다. 안 그러면 "왜 이 주문 얘기가 나오지"가 된다.
        long started = orders.stream().filter(o -> o.getStatus() != OutbStatus.CREATED).count();
        if (started > 0) {
            throw new IllegalArgumentException(
                    "할당이 시작된 주문이 " + started + "건 있어 해체할 수 없습니다 — 할당을 먼저 해제하세요: " + wave.getWavNo());
        }
        orders.forEach(OutbOrder::unassignWave);
        outbWaveRepository.delete(wave);
    }

    /**
     * 주문 행을 <b>id 오름차순으로 잠그며 처음 읽고</b> 편입한다. assignWave의 「이미 편성됨」
     * 가드가 신선한 행 위에서 판정되게 하기 위함 — 락 없이는 전략 실행·다른 수동 편성과
     * 동시에 같은 주문을 잡았을 때 마지막 커밋이 조용히 이긴다 (@Version 없음).
     * 오름차순은 전략 실행(searchIds)의 잠금 순서와 같아 교착이 없다.
     */
    private void assignOrders(OutbWave wave, List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return;
        }
        List<Long> sorted = orderIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        for (Long orderId : sorted) {
            OutbOrder order = outbOrderRepository.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 출고 주문입니다: " + orderId));
            // 화면에서 직접 담은 편성이라 출처는 MANUAL — 전략 실행분과 구분해 표시하기 위함
            order.assignWave(wave, WavRegTyp.MANUAL); // 상태(CREATED)·중복편성 검증은 엔티티가 한다
        }
    }

    private OutbWave lockWave(Long wavId) {
        return outbWaveRepository.findByIdForUpdate(wavId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 웨이브입니다: " + wavId));
    }
}
