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
import com.project.wmsback.inbound.entity.IbStatus;
import com.project.wmsback.inbound.repository.IbOrderRepository;
import com.project.wmsback.master.entity.Prod;
import com.project.wmsback.master.entity.Vendor;
import com.project.wmsback.master.repository.ProdRepository;
import com.project.wmsback.master.repository.VendorRepository;
import com.project.wmsback.master.service.NbrService;
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
     * 작성(CREATED) 상태만 가능하고 그 판정은 엔티티가 한다 — 변환된 주문을 고치면 이미 나간
     * ASN의 예정수량과 어긋나기 때문이다(고치려면 변환취소가 먼저).
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
     * WMS 작업문서(ASN)로 변환. 주문 상태 전이와 ASN 생성이 한 트랜잭션에서 끝난다 —
     * 둘이 갈라지면 "변환됐는데 창고엔 예정이 없는" 주문이 남는다.
     * 입고번호는 ASN 쪽 규칙(IB-YYYYMMDD-NNN, 예정일 기준)을 그대로 따른다.
     *
     * ASN 생성 경로는 여기 하나뿐이다 (WMS에는 등록 엔드포인트가 없다).
     *
     * @return 생성된 ASN의 ib_order_id
     */
    @Transactional
    public Long convert(Long omsIbOrderId) {
        OmsIbOrder order = omsIbOrderRepository.findById(omsIbOrderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고주문입니다: " + omsIbOrderId));
        order.convert(); // 재변환 차단은 엔티티가 한다

        String ibNo = nbrService.issue("IB_NO", order.getExpctDe());

        IbOrder asn = IbOrder.builder()
                .ibNo(ibNo)
                .omsIbOrderId(order.getId())
                .vendor(order.getVendor())
                .expctDe(order.getExpctDe())
                .build();
        for (OmsIbLine line : order.getLines()) {
            // 발주 수량은 입고단위(벤더 납품 단위), ASN 이후의 모든 수량은 출고단위(재고 저장 단위)다.
            // 단위가 갈리는 경계가 여기라서 환산도 여기서 한 번만 한다.
            Prod prod = line.getProd();
            asn.addLine(IbLine.builder()
                    .prod(prod)
                    .expctQty(prod.toOutbQty(line.getOdrQty()))
                    .build());
        }
        ibOrderRepository.save(asn); // cascade로 라인까지 함께 저장
        return asn.getId();
    }

    /**
     * 변환취소. 생성된 ASN을 취소하고 주문을 작성 상태로 원복한다 — 고치고 다시 변환할 수 있다.
     *
     * ASN 취소 경로도 여기 하나뿐이다. WMS에 취소 엔드포인트를 두면 주문 상태를 모르는 채로
     * ASN만 죽어서, 주문은 '변환완료'인데 유효한 예정이 없는 상태로 고착된다.
     * 검수가 시작된 ASN인지는 IbOrder.cancel()이 판정해 막는다.
     */
    @Transactional
    public void cancelConvert(Long omsIbOrderId) {
        OmsIbOrder order = omsIbOrderRepository.findById(omsIbOrderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고주문입니다: " + omsIbOrderId));

        IbOrder asn = ibOrderRepository
                .findByOmsIbOrderIdAndStatusNot(omsIbOrderId, IbStatus.CANCELLED)
                .orElseThrow(() -> new IllegalStateException(
                        "변환취소할 입고예정이 없습니다: " + order.getOmsIbNo()));

        asn.cancel();          // 검수 시작 후면 여기서 막힌다
        order.revertConvert(); // ASN을 물린 뒤에야 주문을 되돌린다
    }

    /** 주문 취소. 변환 전(CREATED)만 가능 — 상태 검증은 엔티티가 한다 */
    @Transactional
    public void cancel(Long omsIbOrderId) {
        OmsIbOrder order = omsIbOrderRepository.findById(omsIbOrderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고주문입니다: " + omsIbOrderId));
        order.cancel();
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
