package com.project.omsback.inbound.service;

import com.project.omsback.inbound.dto.AsnRef;
import com.project.omsback.inbound.dto.OmsIbLineSaveRequest;
import com.project.omsback.inbound.dto.OmsIbLineResponse;
import com.project.omsback.inbound.dto.OmsIbOrderSaveRequest;
import com.project.omsback.inbound.dto.OmsIbOrderResponse;
import com.project.omsback.inbound.dto.OmsIbOrderSearchCond;
import com.project.omsback.inbound.entity.OmsIbLine;
import com.project.omsback.inbound.entity.OmsIbOrder;
import com.project.omsback.inbound.repository.OmsIbLineRepository;
import com.project.omsback.inbound.repository.OmsIbOrderRepository;
import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.repository.IbOrderRepository;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.vendor.entity.Vendor;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.mdm.vendor.repository.VendorRepository;
import com.project.mdm.nbr.service.NbrService;
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
public class OmsIbOrderService {

    private final OmsIbOrderRepository omsIbOrderRepository;
    private final OmsIbLineRepository omsIbLineRepository;
    private final ProdRepository prodRepository;
    private final VendorRepository vendorRepository;
    /** 확정 시 ASN을 만들기 위한 WMS 쪽 의존. 방향은 omsback → wmsback 한쪽만 */
    private final IbOrderRepository ibOrderRepository;
    private final NbrService nbrService;

    public List<OmsIbOrderResponse> list(OmsIbOrderSearchCond cond) {
        List<OmsIbOrder> orders = omsIbOrderRepository.search(cond);
        Map<Long, AsnRef> asnByOrderId = omsIbOrderRepository
                .findAsnRefs(orders.stream().map(OmsIbOrder::getId).toList()).stream()
                .collect(Collectors.toMap(AsnRef::omsIbOrderId, Function.identity()));

        return orders.stream()
                .map(o -> OmsIbOrderResponse.from(o, asnByOrderId.get(o.getId())))
                .toList();
    }

    /** 단건 조회. 수정 화면 진입용 — 목록과 같은 응답 형태(ASN 요약 포함)로 내려준다 */
    public OmsIbOrderResponse get(Long omsIbOrderId) {
        OmsIbOrder order = omsIbOrderRepository.findById(omsIbOrderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고주문입니다: " + omsIbOrderId));
        AsnRef asn = omsIbOrderRepository.findAsnRefs(List.of(omsIbOrderId)).stream()
                .findFirst().orElse(null);
        return OmsIbOrderResponse.from(order, asn);
    }

    public List<OmsIbLineResponse> lines(Long omsIbOrderId) {
        if (!omsIbOrderRepository.existsById(omsIbOrderId)) {
            throw new IllegalArgumentException("존재하지 않는 입고주문입니다: " + omsIbOrderId);
        }
        return omsIbLineRepository.findAllByOrderIdWithProd(omsIbOrderId).stream()
                .map(OmsIbLineResponse::from)
                .toList();
    }

    /** 입고주문 등록. 주문번호는 예정일 + 시퀀스로 채번 (예: PO-20260723-001) */
    @Transactional
    public Long create(OmsIbOrderSaveRequest req) {
        validate(req);
        Vendor vendor = findVendor(req.getVendorId());

        String omsIbNo = nbrService.issue("OMS_IB_NO", req.getExpctDe());

        OmsIbOrder order = OmsIbOrder.builder()
                .omsIbNo(omsIbNo)
                .vendor(vendor)
                .expctDe(req.getExpctDe())
                .odrDvsn(odrDvsnOf(req))
                .picNm(req.getPicNm())
                .rmk(req.getRmk())
                .build();
        toLines(req).forEach(order::addLine);
        omsIbOrderRepository.save(order); // cascade로 라인까지 함께 저장
        return order.getId();
    }

    /**
     * 입고주문 수정. 벤더 · 예정일 · 라인을 통째로 갈아끼운다.
     * <p>
     * 작성(CREATED) 상태만 가능하고 그 판정은 엔티티가 한다 — 확정된 주문을 고치면 이미 나간
     * ASN의 예정수량과 어긋나기 때문이다(고치려면 확정취소가 먼저).
     * <p>
     * 주문번호는 바꾸지 않는다. 예정일을 고쳐도 마찬가지다 — 번호는 채번 시점의 식별자이지
     * 예정일을 따라다니는 값이 아니고, 이미 그 번호로 주고받은 이력이 어긋난다.
     */
    @Transactional
    public void update(Long omsIbOrderId, OmsIbOrderSaveRequest req) {
        validate(req);
        OmsIbOrder order = omsIbOrderRepository.findById(omsIbOrderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고주문입니다: " + omsIbOrderId));
        order.update(findVendor(req.getVendorId()), req.getExpctDe(),
                odrDvsnOf(req), req.getPicNm(), req.getRmk(), toLines(req));
    }

    /**
     * 발주구분 기본값. 비워 보내면 정상(NRML)이다 — 컬럼 DEFAULT와 같은 값이지만
     * JPA는 null을 그대로 INSERT하므로 DEFAULT가 걸리지 않아 여기서 채운다.
     * 값이 공통코드에 실재하는지는 확인하지 않는다 — 화면 콤보박스로만 들어오기 때문이다
     * (ProdService가 단위 코드를 다루는 방식과 같다).
     */
    private String odrDvsnOf(OmsIbOrderSaveRequest req) {
        String dvsn = req.getOdrDvsn();
        return (dvsn == null || dvsn.isBlank()) ? "NRML" : dvsn;
    }

    private Vendor findVendor(Long vendorId) {
        return vendorRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 벤더입니다: " + vendorId));
    }

    private List<OmsIbLine> toLines(OmsIbOrderSaveRequest req) {
        List<OmsIbLine> lines = new ArrayList<>();
        for (OmsIbLineSaveRequest line : req.getLines()) {
            Prod prod = prodRepository.findById(line.getProdId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다: " + line.getProdId()));
            lines.add(OmsIbLine.builder()
                    .prod(prod)
                    .odrQty(line.getOdrQty())
                    .build());
        }
        return lines;
    }

    /**
     * WMS 작업문서(ASN) 생성을 동반하는 확정. 주문 상태 전이와 ASN 생성이 한 트랜잭션에서 끝난다 —
     * 둘이 갈라지면 "확정됐는데 창고엔 예정이 없는" 주문이 남는다.
     * 입고번호는 ASN 쪽 규칙(IB-YYYYMMDD-NNN, 예정일 기준)을 그대로 따른다.
     *
     * ASN 생성 경로는 여기 하나뿐이다 (WMS에는 등록 엔드포인트가 없다).
     *
     * @return 생성된 ASN의 ib_order_id
     */
    @Transactional
    public Long confirm(Long omsIbOrderId) {
        OmsIbOrder order = omsIbOrderRepository.findById(omsIbOrderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고주문입니다: " + omsIbOrderId));
        order.confirm(); // 재확정 차단은 엔티티가 한다

        String ibNo = nbrService.issue("IB_NO", order.getExpctDe());

        IbOrder asn = IbOrder.builder()
                .ibNo(ibNo)
                .omsIbOrderId(order.getId())
                .vendor(order.getVendor())
                .expctDe(order.getExpctDe())
                .odrDvsn(order.getOdrDvsn())
                .build();
        for (OmsIbLine line : order.getLines()) {
            // 발주 수량은 입고단위(벤더 납품 단위), ASN부터 창고의 모든 수량은 낱개(EA)다.
            // 단위가 갈리는 경계가 여기라서 환산도 여기서 한 번만 한다.
            Prod prod = line.getProd();
            asn.addLine(IbLine.builder()
                    .prod(prod)
                    .expctQty(prod.toEaQty(line.getOdrQty(), prod.getInbUomCd()))
                    .build());
        }
        ibOrderRepository.save(asn); // cascade로 라인까지 함께 저장
        return asn.getId();
    }

    /**
     * 확정취소. 생성된 ASN을 삭제하고 주문을 작성 상태로 원복한다 — 고치고 다시 확정할 수 있다.
     * CANCELLED 상태로 남기지 않는다: 검수 전의 예정은 아직 아무 일도 안 한 문서라 흔적 가치가 없고,
     * 남겨두면 입고예정 목록마다 취소분을 빼는 필터가 따라붙는다(OMS 주문 쪽과 같은 판단).
     * 검수가 시작된 ASN(inv_hist가 라인을 참조하기 시작한 뒤)은 requireRevertible()이 막는다.
     *
     * ASN 소멸 경로도 여기 하나뿐이다. WMS에 취소 엔드포인트를 두면 주문 상태를 모르는 채로
     * ASN만 죽어서, 주문은 '확정'인데 예정이 없는 상태로 고착된다.
     */
    @Transactional
    public void cancelConfirm(Long omsIbOrderId) {
        OmsIbOrder order = omsIbOrderRepository.findById(omsIbOrderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고주문입니다: " + omsIbOrderId));

        IbOrder asn = ibOrderRepository.findByOmsIbOrderId(omsIbOrderId)
                .orElseThrow(() -> new IllegalStateException(
                        "확정취소할 입고예정이 없습니다: " + order.getOmsIbNo()));

        asn.requireRevertible();       // 검수 시작 후면 여기서 막힌다
        ibOrderRepository.delete(asn); // cascade로 라인까지 함께 삭제
        order.revertConfirm();         // ASN을 물린 뒤에야 주문을 되돌린다
    }

    /**
     * 주문 삭제. 확정 전(CREATED)만 가능 — 상태 검증은 엔티티가 한다.
     * 라인은 cascade + orphanRemoval이 함께 지운다.
     * <p>
     * 취소 상태를 두지 않고 지우는 이유는 {@link com.project.omsback.inbound.entity.OmsIbStatus} 참고.
     * 확정된 적 있는 주문이라도 확정취소를 거치면 지울 수 있다 — ASN은 확정취소 시점에 이미 삭제됐다.
     */
    @Transactional
    public void delete(Long omsIbOrderId) {
        OmsIbOrder order = omsIbOrderRepository.findById(omsIbOrderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고주문입니다: " + omsIbOrderId));
        order.requireDeletable();
        omsIbOrderRepository.delete(order);
    }

    private void validate(OmsIbOrderSaveRequest req) {
        if (req.getVendorId() == null) {
            throw new IllegalArgumentException("벤더는 필수입니다.");
        }
        if (req.getExpctDe() == null) {
            throw new IllegalArgumentException("입고 예정일은 필수입니다.");
        }
        if (req.getLines() == null || req.getLines().isEmpty()) {
            throw new IllegalArgumentException("주문 라인은 최소 1건 필요합니다.");
        }
        for (OmsIbLineSaveRequest line : req.getLines()) {
            if (line.getProdId() == null) {
                throw new IllegalArgumentException("라인의 상품은 필수입니다.");
            }
            if (line.getOdrQty() == null || line.getOdrQty() < 1) {
                throw new IllegalArgumentException("발주 수량은 1 이상이어야 합니다.");
            }
        }
    }
}
