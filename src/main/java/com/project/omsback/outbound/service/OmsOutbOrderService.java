package com.project.omsback.outbound.service;

import com.project.mdm.code.entity.CodeDetailId;
import com.project.mdm.code.repository.CodeDetailRepository;
import com.project.mdm.nbr.service.NbrService;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.mdm.store.entity.Store;
import com.project.mdm.store.repository.StoreRepository;
import com.project.omsback.outbound.dto.OmsOutbLineResponse;
import com.project.omsback.outbound.dto.OmsOutbLineSaveRequest;
import com.project.omsback.outbound.dto.OmsOutbOrderResponse;
import com.project.omsback.outbound.dto.OmsOutbOrderSaveRequest;
import com.project.omsback.outbound.dto.OmsOutbOrderSearchCond;
import com.project.omsback.outbound.dto.OutbOrderRef;
import com.project.omsback.outbound.entity.OmsOutbLine;
import com.project.omsback.outbound.entity.OmsOutbOrder;
import com.project.omsback.outbound.repository.OmsOutbLineRepository;
import com.project.omsback.outbound.repository.OmsOutbOrderRepository;
import com.project.wmsback.outbound.entity.OutbLine;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.repository.OutbOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OmsOutbOrderService {

    private final OmsOutbOrderRepository omsOutbOrderRepository;
    private final OmsOutbLineRepository omsOutbLineRepository;
    private final ProdRepository prodRepository;
    private final StoreRepository storeRepository;
    private final CodeDetailRepository codeDetailRepository;
    /** 확정 시 WMS 출고주문을 만들기 위한 의존. 방향은 omsback → wmsback 한쪽만 */
    private final OutbOrderRepository outbOrderRepository;
    private final NbrService nbrService;

    public List<OmsOutbOrderResponse> list(OmsOutbOrderSearchCond cond) {
        List<OmsOutbOrder> orders = omsOutbOrderRepository.search(cond);
        Map<Long, OutbOrderRef> refByOrderId = omsOutbOrderRepository
                .findOutbOrderRefs(orders.stream().map(OmsOutbOrder::getId).toList()).stream()
                .collect(Collectors.toMap(OutbOrderRef::omsOutbOrderId, Function.identity()));

        return orders.stream()
                .map(o -> OmsOutbOrderResponse.from(o, refByOrderId.get(o.getId())))
                .toList();
    }

    public List<OmsOutbLineResponse> lines(Long omsOutbOrderId) {
        if (!omsOutbOrderRepository.existsById(omsOutbOrderId)) {
            throw new IllegalArgumentException("존재하지 않는 출고주문입니다: " + omsOutbOrderId);
        }
        return omsOutbLineRepository.findAllByOrderIdWithProd(omsOutbOrderId).stream()
                .map(OmsOutbLineResponse::from)
                .toList();
    }

    /** 출고주문 등록. 주문번호는 예정일 + 시퀀스로 채번 (예: SO-20260803-001) */
    @Transactional
    public Long create(OmsOutbOrderSaveRequest req) {
        validate(req);
        Store store = findStore(req.getStoreId());

        String omsOutbNo = nbrService.issue("OMS_OUTB_NO", req.getExpctDe());

        OmsOutbOrder order = OmsOutbOrder.builder()
                .omsOutbNo(omsOutbNo)
                .store(store)
                .outbTyp(outbTypOf(req))
                .vhclFltno(blankToNull(req.getVhclFltno()))
                .expctDe(req.getExpctDe())
                .picNm(req.getPicNm())
                .rmk(req.getRmk())
                .build();
        toLines(req).forEach(order::addLine);
        omsOutbOrderRepository.save(order); // cascade로 라인까지 함께 저장
        return order.getId();
    }

    /**
     * 출고주문 수정. 납품처 · 출고유형 · 편수 · 예정일 · 라인을 통째로 갈아끼운다.
     * <p>
     * 작성(CREATED) 상태만 가능하고 그 판정은 엔티티가 한다 — 확정된 주문을 고치면 이미 나간
     * WMS 출고주문의 내용과 어긋나기 때문이다(고치려면 확정취소가 먼저).
     * <p>
     * 주문번호는 바꾸지 않는다. 예정일을 고쳐도 마찬가지다 — 번호는 채번 시점의 식별자이지
     * 예정일을 따라다니는 값이 아니고, 이미 그 번호로 주고받은 이력이 어긋난다.
     */
    @Transactional
    public void update(Long omsOutbOrderId, OmsOutbOrderSaveRequest req) {
        validate(req);
        OmsOutbOrder order = findOrder(omsOutbOrderId);
        order.update(findStore(req.getStoreId()), outbTypOf(req), blankToNull(req.getVhclFltno()),
                req.getExpctDe(), req.getPicNm(), req.getRmk(), toLines(req));
    }

    /**
     * WMS 작업문서(출고주문) 생성을 동반하는 확정. 주문 상태 전이와 문서 생성이 한 트랜잭션에서
     * 끝난다 — 둘이 갈라지면 "확정됐는데 창고엔 나갈 물건이 없는" 주문이 남는다.
     * 출고번호는 WMS 쪽 규칙(OB-YYYYMMDD-NNN, 출고예정일 기준)을 그대로 따른다.
     *
     * <p>WMS 출고주문 생성 경로는 여기 하나뿐이다 (WMS에는 등록 엔드포인트가 없다).
     *
     * <p>수량은 환산하지 않는다 — 주문 수량이 이미 출고단위다. 입고(발주 수량이 입고단위라
     * ASN 생성 때 환산)와 갈리는 지점이라 여기 적어둔다.
     *
     * @return 생성된 WMS 출고주문의 outb_order_id
     */
    @Transactional
    public Long confirm(Long omsOutbOrderId) {
        OmsOutbOrder order = findOrder(omsOutbOrderId);
        order.confirm(); // 재확정 차단은 엔티티가 한다

        String outbNo = nbrService.issue("OUTB_NO", order.getExpctDe());

        OutbOrder wmsOrder = OutbOrder.builder()
                .outbNo(outbNo)
                .omsOutbOrderId(order.getId())
                .store(order.getStore())
                .outbTyp(order.getOutbTyp())
                .vhclFltno(order.getVhclFltno())
                // 주문일은 주문이 등록된 날이다. 확정일이 아니다 — 오늘 확정해도 어제 들어온 주문이다.
                .odrDe(order.getCreatedAt().toLocalDate())
                .expctDe(order.getExpctDe())
                .build();
        for (OmsOutbLine line : order.getLines()) {
            wmsOrder.addLine(OutbLine.builder()
                    .prod(line.getProd())
                    .odrQty(line.getOdrQty())
                    .build());
        }
        outbOrderRepository.save(wmsOrder); // cascade로 라인까지 함께 저장
        return wmsOrder.getId();
    }

    /**
     * 확정취소. 생성된 WMS 출고주문을 삭제하고 주문을 작성 상태로 원복한다 — 고치고 다시 확정할 수 있다.
     * CANCELLED로 남기지 않는다: 웨이브에 담기지도 않은 출고주문은 아직 아무 일도 안 한 문서라
     * 흔적 가치가 없고, 남겨두면 목록마다 취소분을 빼는 필터가 따라붙는다(입고 쪽과 같은 판단).
     * 웨이브 편성·할당이 시작된 문서는 requireRevertible()이 막는다.
     *
     * <p>WMS 출고주문 소멸 경로도 여기 하나뿐이다. WMS에 삭제 엔드포인트를 두면 주문 상태를
     * 모르는 채로 문서만 죽어서, 주문은 '확정'인데 창고엔 아무것도 없는 상태로 고착된다.
     */
    @Transactional
    public void cancelConfirm(Long omsOutbOrderId) {
        OmsOutbOrder order = findOrder(omsOutbOrderId);

        OutbOrder wmsOrder = outbOrderRepository.findByOmsOutbOrderId(omsOutbOrderId)
                .orElseThrow(() -> new IllegalStateException(
                        "확정취소할 출고주문이 없습니다: " + order.getOmsOutbNo()));

        wmsOrder.requireRevertible();       // 웨이브 편성·할당 후면 여기서 막힌다
        outbOrderRepository.delete(wmsOrder); // cascade로 라인까지 함께 삭제
        order.revertConfirm();              // 창고 문서를 물린 뒤에야 주문을 되돌린다
    }

    /**
     * 주문 삭제. 확정 전(CREATED)만 가능 — 상태 검증은 엔티티가 한다.
     * 라인은 cascade + orphanRemoval이 함께 지운다.
     * <p>
     * 취소 상태를 두지 않고 지우는 이유는 {@link com.project.omsback.outbound.entity.OmsOutbStatus} 참고.
     * 확정된 적 있는 주문이라도 확정취소를 거치면 지울 수 있다 — 창고 문서는 그때 이미 삭제됐다.
     */
    @Transactional
    public void delete(Long omsOutbOrderId) {
        OmsOutbOrder order = findOrder(omsOutbOrderId);
        order.requireDeletable();
        omsOutbOrderRepository.delete(order);
    }

    private OmsOutbOrder findOrder(Long omsOutbOrderId) {
        return omsOutbOrderRepository.findById(omsOutbOrderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 출고주문입니다: " + omsOutbOrderId));
    }

    private Store findStore(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 점포입니다: " + storeId));
    }

    /**
     * 출고유형 기본값. 비워 보내면 일반출고(NRML)다 — 컬럼 DEFAULT와 같은 값이지만
     * JPA는 null을 그대로 INSERT하므로 DEFAULT가 걸리지 않아 여기서 채운다.
     */
    private String outbTypOf(OmsOutbOrderSaveRequest req) {
        String typ = req.getOutbTyp();
        return (typ == null || typ.isBlank()) ? OmsOutbOrder.DFLT_OUTB_TYP : typ;
    }

    /** 편수는 「선택 안 함 = 배차 미정」이다. 빈 문자열이 그대로 들어오면 어떤 편수와도 다른 값이 된다 */
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private List<OmsOutbLine> toLines(OmsOutbOrderSaveRequest req) {
        List<OmsOutbLine> lines = new ArrayList<>();
        for (OmsOutbLineSaveRequest line : req.getLines()) {
            Prod prod = prodRepository.findById(line.getProdId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다: " + line.getProdId()));
            lines.add(OmsOutbLine.builder()
                    .prod(prod)
                    .odrQty(line.getOdrQty())
                    .build());
        }
        return lines;
    }

    private void validate(OmsOutbOrderSaveRequest req) {
        if (req.getStoreId() == null) {
            throw new IllegalArgumentException("납품처는 필수입니다.");
        }
        if (req.getExpctDe() == null) {
            throw new IllegalArgumentException("출고 예정일은 필수입니다.");
        }
        // 공통코드 값은 존재만 확인한다 — 값 목록의 주인은 코드관리 화면이라 컬럼 CHECK를 걸지 않았다
        requireCode("OUTB_TYP", req.getOutbTyp(), "출고유형");
        requireCode("VHCL_FLTNO", req.getVhclFltno(), "차량편수");
        if (req.getLines() == null || req.getLines().isEmpty()) {
            throw new IllegalArgumentException("주문 라인은 최소 1건 필요합니다.");
        }
        for (OmsOutbLineSaveRequest line : req.getLines()) {
            if (line.getProdId() == null) {
                throw new IllegalArgumentException("라인의 상품은 필수입니다.");
            }
            if (line.getOdrQty() == null || line.getOdrQty() < 1) {
                throw new IllegalArgumentException("주문 수량은 1 이상이어야 합니다.");
            }
        }
    }

    /** 비어 있으면 통과(선택 항목이거나 엔티티 기본값이 채운다), 값이 있으면 그 코드가 실존해야 한다 */
    private void requireCode(String grpCd, String codeCd, String label) {
        if (codeCd == null || codeCd.isBlank()) {
            return;
        }
        if (!codeDetailRepository.existsById(new CodeDetailId(grpCd, codeCd))) {
            throw new IllegalArgumentException("없는 " + label + " 코드입니다: " + codeCd);
        }
    }
}
