package com.project.omsback.inbound.service;

import com.project.common.batch.BatchExecutor;
import com.project.common.batch.BatchResult;
import com.project.mdm.prod.dto.ProdVndrSearchCond;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.ProdVndr;
import com.project.mdm.prod.repository.ProdVndrRepository;
import com.project.omsback.inbound.dto.AtoOdrIssueRequest;
import com.project.omsback.inbound.dto.AtoOdrProposalResponse;
import com.project.omsback.inbound.dto.AtoOdrSearchCond;
import com.project.omsback.inbound.dto.OmsIbLineSaveRequest;
import com.project.omsback.inbound.dto.OmsIbOrderSaveRequest;
import com.project.omsback.inbound.entity.OmsIbOrder;
import com.project.omsback.inbound.repository.OmsIbLineRepository;
import com.project.wmsback.inventory.service.ProdStockPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 자동발주 — 순재고가 발주점 아래인 상품을 벤더별 입고주문(작성)으로 제안하고 발행한다.
 * <p>
 * 산정(plan)과 발행(issue)을 나눈 것은 정기보충과 같은 틀이다 — 스케줄러가 본선이고 화면은 임의 시점
 * 재계산·수량 보정용이라, 둘이 같은 경로를 지나야 검증이 갈리지 않는다.
 * <p>
 * <b>확정(→ASN)까지 가지 않는다.</b> 자동으로 입고예정을 만들면 기준값이 틀렸을 때 창고가 오지 않을
 * 물건을 기다린다. 이동지시를 바로 내는 정기보충과 다른 이유는, 이동은 창고 안의 일이라 되돌리기 쉽고
 * 발주는 바깥(벤더)과의 약속이기 때문이다. 잘못된 제안은 작성 상태에서 지우면 그만이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AtoOdrService {

    private static final Logger log = LoggerFactory.getLogger(AtoOdrService.class);
    private static final DateTimeFormatter RMK_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ProdVndrRepository prodVndrRepository;
    /** 창고 재고를 읽는 유일한 통로 — 쓰기는 없다 (의존 방향은 omsback → wmsback 한쪽) */
    private final ProdStockPort prodStockPort;
    private final OmsIbLineRepository omsIbLineRepository;
    private final OmsIbOrderService omsIbOrderService;
    private final BatchExecutor batchExecutor;

    /**
     * 발주 제안 산정. 상품마다 대표 벤더 하나(prty 최소, 동률이면 등록 순)를 골라 순재고를 재고 · 입고예정 ·
     * 미확정 발주 세 항으로 세고, 발주점 미달이면 상한까지 채울 수량을 입고단위로 올림해 담는다.
     */
    public List<AtoOdrProposalResponse> plan(AtoOdrSearchCond cond) {
        List<ProdVndr> primary = primaryByProd(prodVndrRepository.search(toProdVndrCond(cond)));
        if (primary.isEmpty()) {
            return List.of();
        }

        Set<Long> prodIds = new LinkedHashSet<>();
        primary.forEach(pv -> prodIds.add(pv.getProd().getId()));
        Map<Long, ProdStockPort.ProdStock> stocks = prodStockPort.stockByProd(prodIds);
        Map<Long, Long> openOdrByProd = omsIbLineRepository.openOdrQtyByProd(prodIds);

        Map<Long, List<AtoOdrProposalResponse.Line>> linesByVendor = new LinkedHashMap<>();
        Map<Long, ProdVndr> vendorSample = new LinkedHashMap<>();
        Map<Long, Integer> maxLeadByVendor = new LinkedHashMap<>();

        for (ProdVndr pv : primary) {
            AtoOdrProposalResponse.Line line = toLine(pv, stocks, openOdrByProd);
            if (line == null) {
                continue;
            }
            Long vendorId = pv.getVendor().getId();
            linesByVendor.computeIfAbsent(vendorId, id -> new ArrayList<>()).add(line);
            vendorSample.putIfAbsent(vendorId, pv);
            maxLeadByVendor.merge(vendorId, pv.getLeadDays(), Math::max);
        }

        LocalDate today = LocalDate.now();
        List<AtoOdrProposalResponse> result = new ArrayList<>();
        linesByVendor.forEach((vendorId, lines) -> {
            ProdVndr sample = vendorSample.get(vendorId);
            result.add(new AtoOdrProposalResponse(
                    vendorId,
                    sample.getVendor().getVndrCd(),
                    sample.getVendor().getVndrNm(),
                    today.plusDays(maxLeadByVendor.get(vendorId)),
                    lines));
        });
        return result;
    }

    /**
     * 발주 발행 — 벤더 1곳이 입고주문 1건이 된다. 채번 · 라인 저장은 {@link OmsIbOrderService#create}가
     * 그대로 하고 여기서는 자동발주 고유 검증만 한다.
     * <p>
     * 순재고를 다시 계산하지는 않는다(정기보충의 2회 검증과 다른 점) — 작성 상태로 남아 사람이 확인하고,
     * 산정이 미확정 발주를 순재고에 세므로 이미 낸 제안은 다음 조회에서 저절로 사라진다.
     */
    @Transactional
    public Long issue(AtoOdrIssueRequest request) {
        validate(request);

        OmsIbOrderSaveRequest req = new OmsIbOrderSaveRequest();
        req.setVendorId(request.getVendorId());
        req.setExpctDe(request.getExpctDe());
        req.setOdrDvsn(OmsIbOrder.ATO);
        // 담당자는 비운다 — 사람이 낸 발주가 아니라는 표시는 발주구분(ATO)이 맡는다
        req.setRmk("자동발주 %s 산정분".formatted(LocalDate.now().format(RMK_DATE)));
        req.setLines(request.getItems().stream().map(item -> {
            OmsIbLineSaveRequest line = new OmsIbLineSaveRequest();
            line.setProdId(item.getProdId());
            line.setOdrQty(item.getOdrQty());
            return line;
        }).toList());

        return omsIbOrderService.create(req);
    }

    /**
     * 여러 벤더분 일괄 발행. 트랜잭션은 벤더 단위다 — 한 벤더가 걸려도 나머지는 나간다.
     * 이 메서드 자체는 트랜잭션 밖에서 돈다(클래스의 readOnly 트랜잭션이 배치 전체를 붙들지 않게).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BatchResult issueAll(List<AtoOdrIssueRequest> requests) {
        return batchExecutor.run(requests, AtoOdrIssueRequest::getVendorId, this::issue);
    }

    /** 상품마다 첫 행만 남긴다 — 조회 정렬이 (상품, prty, id)라 첫 행이 곧 대표 벤더다 */
    private static List<ProdVndr> primaryByProd(List<ProdVndr> rows) {
        Map<Long, ProdVndr> byProd = new LinkedHashMap<>();
        for (ProdVndr row : rows) {
            byProd.putIfAbsent(row.getProd().getId(), row);
        }
        return List.copyOf(byProd.values());
    }

    /** 발주 대상이 아니면 null */
    private static AtoOdrProposalResponse.Line toLine(ProdVndr pv,
                                                      Map<Long, ProdStockPort.ProdStock> stocks,
                                                      Map<Long, Long> openOdrByProd) {
        Prod prod = pv.getProd();
        long eaPerUom;
        try {
            eaPerUom = prod.eaQtyOf(prod.getInbUomCd());
        } catch (IllegalStateException e) {
            // 입고단위 포장이 사라진 상품 — 등록 시점엔 있었다. 이 상품만 건너뛴다(밤 작업 전체를 세우지 않는다)
            log.warn("[ATO] 입고단위 포장이 없어 산정에서 제외: {} / {}", prod.getProdCd(), prod.getInbUomCd());
            return null;
        }

        ProdStockPort.ProdStock stock = stocks.get(prod.getId());
        long avalQty = stock != null ? stock.avalQty() : 0;
        long openAsnQty = stock != null ? stock.openAsnQty() : 0;
        long openOdrQty = openOdrByProd.getOrDefault(prod.getId(), 0L);
        long openOdrEaQty = prod.toEaQty(openOdrQty, prod.getInbUomCd());

        long net = AtoOdrQtyCalc.net(avalQty, openAsnQty, openOdrEaQty);
        if (!AtoOdrQtyCalc.isShort(net, pv.getMinQty())) {
            return null;
        }
        long shortEaQty = pv.getMaxQty() - net;
        long odrQty = AtoOdrQtyCalc.proposedQty(shortEaQty, eaPerUom, pv.getMinOdrQty());

        return new AtoOdrProposalResponse.Line(
                pv.getId(), prod.getId(), prod.getProdCd(), prod.getProdNm(),
                prod.getInbUomCd(), eaPerUom,
                avalQty, openAsnQty, openOdrQty, openOdrEaQty,
                net, pv.getMinQty(), pv.getMaxQty(), shortEaQty,
                pv.getMinOdrQty(), pv.getLeadDays(), odrQty);
    }

    private void validate(AtoOdrIssueRequest request) {
        if (request.getVendorId() == null) {
            throw new IllegalArgumentException("거래처는 필수입니다.");
        }
        if (request.getExpctDe() == null) {
            throw new IllegalArgumentException("입고 예정일은 필수입니다.");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("발주할 상품이 없습니다.");
        }
        for (AtoOdrIssueRequest.Item item : request.getItems()) {
            if (item.getProdId() == null) {
                throw new IllegalArgumentException("상품은 필수입니다.");
            }
            if (item.getOdrQty() == null || item.getOdrQty() < 1) {
                throw new IllegalArgumentException("발주 수량은 1 이상이어야 합니다: " + item.getProdId());
            }
        }
        // 자동발주는 상품 거래처 마스터가 근거다 — 등재되지 않은 짝으로 들어오면 산정이 낸 제안이 아니다.
        // 일반 발주 창구(입고주문 등록)가 따로 있으므로 여기서 막아도 막다른 길이 되지 않는다
        Set<Long> registered = prodVndrRepository.findProdIdsByVendorId(request.getVendorId());
        for (AtoOdrIssueRequest.Item item : request.getItems()) {
            if (!registered.contains(item.getProdId())) {
                throw new IllegalArgumentException(
                        "이 거래처의 상품 거래처 마스터에 없는 상품입니다: " + item.getProdId());
            }
        }
    }

    private static ProdVndrSearchCond toProdVndrCond(AtoOdrSearchCond cond) {
        ProdVndrSearchCond target = new ProdVndrSearchCond();
        if (cond != null) {
            target.setProdCd(cond.getProdCd());
            target.setProdNm(cond.getProdNm());
            target.setVndrCd(cond.getVndrCd());
        }
        return target;
    }
}
