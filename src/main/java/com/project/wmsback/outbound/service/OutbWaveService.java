package com.project.wmsback.outbound.service;

import com.project.wmsback.outbound.dto.OutbWaveOrdersRequest;
import com.project.wmsback.outbound.dto.OutbWaveResponse;
import com.project.wmsback.outbound.dto.OutbWaveSearchCond;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.repository.OutbOrderRepository;
import com.project.wmsback.outbound.repository.OutbWaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 출고 웨이브 편성. 여러 출고 주문을 하나의 할당 단위로 묶는다.
 * 릴리즈(=재고 할당)는 별도(할당 서비스)에서 다룬다 — 여기까지는 편성만.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutbWaveService {

    private final OutbWaveRepository outbWaveRepository;
    private final OutbOrderRepository outbOrderRepository;

    public List<OutbWaveResponse> list(OutbWaveSearchCond cond) {
        return outbWaveRepository.search(cond).stream()
                .map(OutbWaveResponse::from)
                .toList();
    }

    public OutbWaveResponse detail(Long waveId) {
        OutbWave wave = outbWaveRepository.findById(waveId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 웨이브입니다: " + waveId));
        return OutbWaveResponse.from(wave);
    }

    /** 웨이브 생성. 웨이브번호는 생성일 + 시퀀스로 채번 (예: WV-20260718-001). 초기 주문 목록은 선택 */
    @Transactional
    public Long create(OutbWaveOrdersRequest req) {
        String waveNo = String.format("WV-%s-%03d",
                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
                outbWaveRepository.nextWaveNoSeq());

        OutbWave wave = outbWaveRepository.save(OutbWave.builder().waveNo(waveNo).build());
        assignOrders(wave, req.getOrderIds());
        return wave.getId();
    }

    /** 주문 추가 편성 */
    @Transactional
    public void addOrders(Long waveId, OutbWaveOrdersRequest req) {
        OutbWave wave = outbWaveRepository.findById(waveId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 웨이브입니다: " + waveId));
        wave.assertPlanned();
        assignOrders(wave, req.getOrderIds());
    }

    /** 주문 1건 편성 해제 */
    @Transactional
    public void removeOrder(Long waveId, Long outbOrderId) {
        OutbWave wave = outbWaveRepository.findById(waveId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 웨이브입니다: " + waveId));
        wave.assertPlanned();
        OutbOrder order = outbOrderRepository.findById(outbOrderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 출고 주문입니다: " + outbOrderId));
        if (order.getWave() == null || !order.getWave().getId().equals(waveId)) {
            throw new IllegalArgumentException("이 웨이브에 편성된 주문이 아닙니다: " + outbOrderId);
        }
        order.unassignWave();
    }

    /** 웨이브 해체. 소속 주문을 모두 편성 해제하고 웨이브를 삭제한다 (PLANNED만) */
    @Transactional
    public void disband(Long waveId) {
        OutbWave wave = outbWaveRepository.findById(waveId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 웨이브입니다: " + waveId));
        wave.assertPlanned();
        outbOrderRepository.findByWaveId(waveId).forEach(OutbOrder::unassignWave);
        outbWaveRepository.delete(wave);
    }

    private void assignOrders(OutbWave wave, List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return;
        }
        for (Long orderId : orderIds) {
            OutbOrder order = outbOrderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 출고 주문입니다: " + orderId));
            order.assignWave(wave); // 상태(CREATED)·중복편성 검증은 엔티티가 한다
        }
    }
}
