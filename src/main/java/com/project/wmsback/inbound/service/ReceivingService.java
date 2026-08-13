package com.project.wmsback.inbound.service;

import com.project.wmsback.inbound.dto.ReceiptResponse;
import com.project.wmsback.inbound.dto.ReceiveRequest;
import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.entity.IbStatus;
import com.project.wmsback.inbound.repository.IbLineRepository;
import com.project.wmsback.inbound.repository.IbOrderRepository;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvHist;
import com.project.wmsback.inventory.entity.RefDocTyp;
import com.project.wmsback.inventory.entity.TxTyp;
import com.project.wmsback.inventory.repository.InvHistRepository;
import com.project.wmsback.inventory.service.InvDocRef;
import com.project.wmsback.inventory.service.InvKey;
import com.project.wmsback.inventory.service.InvStore;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.warehouse.repository.LocRepository;
import com.project.wmsback.warehouse.repository.LotRepository;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.wmsback.strategy.inspection.service.InspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 입고 검수/마감. 검수 저장은 라인 수량 누계 + Lot 생성 + 스테이징 재고 증가 + 재고 이력이
 * 한 트랜잭션으로 묶인다 (불변식: 이력 합계 = 스냅샷).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceivingService {

    /** 검수 합격분이 들어가는 입고 스테이징. 온도대 검증은 여기선 하지 않는다 (적치 때 수행) */
    private static final String STAGING_LOC_CD = "RCV-STAGE";

    /** Lot 번호의 입고일자 조각 형식 — 채번 근거는 nextLotNo 참고 */
    private static final DateTimeFormatter LOT_NO_DT_FMT = DateTimeFormatter.ofPattern("yyMMdd");

    private final IbOrderRepository ibOrderRepository;
    private final IbLineRepository ibLineRepository;
    private final LotRepository lotRepository;
    private final LocRepository locRepository;
    private final InvHistRepository invHistRepository;
    private final InvStore invStore;
    private final ProdRepository prodRepository;
    private final InspectionService inspectionService;

    /** 검수 저장 (증분). 요청 라인 중 한 건이라도 실패하면 전체 롤백 */
    @Transactional
    public void receive(Long ibOrderId, ReceiveRequest req) {
        if (req.getLines() == null || req.getLines().isEmpty()) {
            throw new IllegalArgumentException("검수할 라인이 없습니다.");
        }
        IbOrder order = ibOrderRepository.findById(ibOrderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고예정입니다: " + ibOrderId));

        // 상품 락이 먼저다 — 아래 lockProds 참고. 라인을 읽는 것은 전부 이 뒤로 온다
        lockProds(order, req.getLines());

        // 검수 제약 (전략): 위반이 하나라도 있으면 예외로 저장 전체 거부 — 전 위반을 한 번에 반환.
        // 실행 로그는 REQUIRES_NEW라 이 트랜잭션이 롤백돼도 남는다
        inspectionService.checkReceive(order, req.getLines());

        order.startReceiving();

        Loc staging = locRepository.findByLocCd(STAGING_LOC_CD)
                .orElseThrow(() -> new IllegalStateException("입고 스테이징 로케이션(RCV-STAGE)이 없습니다."));

        for (ReceiveRequest.Line line : req.getLines()) {
            receiveLine(order, staging, line);
        }

        order.checkAndAutoReceive(); // 전 라인 전량 검수됐으면 마감 없이 바로 RECEIVED(→ 적치까지 끝났다면 COMPLETED)로 전이
    }

    /**
     * 요청 라인이 가리키는 상품을 <b>id 오름차순으로 한 건씩</b> 잠근다. 라인을 읽는 어떤 코드보다도
     * 앞서야 하고(검수 제약 포함), 그 대가로 둘을 얻는다.
     *
     * <p><b>① 교착 회피.</b> 락은 커밋까지 유지되므로 요청이 보낸 순서대로 잠그면 상품이 겹치는 두
     * 검수가 서로 반대 순서로 잡아 교착이 난다(A: 상품1→상품2 / B: 상품2→상품1). 라인 id로 정렬해서는
     * 안 된다 — 라인 id 순서와 상품 id 순서는 입고마다 다르다. 락 순서를 하나로 고정하는 것은
     * 재고 행 락이 표준 순서(재고 키 오름차순, InvStore)를 따르는 것과 같은 원칙이다.
     *
     * <p><b>② 잔량 검사와 누계 갱신의 직렬화.</b> 같은 라인을 동시에 검수한 둘이 같은 잔량을 보고
     * 통과하면, 각자 자기 스냅샷에 더한 {@code rcvd_qty}를 절대값으로 덮어써 한쪽 검수가 증발한다
     * (이력엔 두 건이 남아 라인 누계와 어긋난다). {@code ib_line}에는 {@code @Version}이 없고
     * {@code ck_ib_line_qty}도 {@code rcvd_qty ≤ expct_qty}를 보지 않아 아무도 막지 않는다.
     *
     * <p>그래서 잠글 상품을 라인 엔티티가 아니라 <b>스칼라 조회</b>로 고른다. 정렬하려고 라인을 미리
     * 읽으면 영속성 컨텍스트에 올라가 락 뒤에도 값이 갱신되지 않아 ②가 그대로 되살아난다.
     */
    private void lockProds(IbOrder order, List<ReceiveRequest.Line> lines) {
        // 라인 id가 비어 온 요청은 여기서 거르고 findLine이 거부하게 둔다 (조회에 null을 넘기지 않는다)
        List<Long> ibLineIds = lines.stream()
                .map(ReceiveRequest.Line::getIbLineId)
                .filter(Objects::nonNull)
                .toList();
        if (ibLineIds.isEmpty()) {
            return;
        }
        List<Long> prodIds = ibLineRepository.findProdIdsByOrderIdAndIdIn(order.getId(), ibLineIds);
        for (Long prodId : prodIds.stream().sorted().toList()) {
            prodRepository.findByIdForUpdate(prodId);
        }
    }

    private IbLine findLine(IbOrder order, Long ibLineId) {
        IbLine ibLine = ibLineRepository.findById(ibLineId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고 라인입니다: " + ibLineId));
        if (!ibLine.getIbOrder().getId().equals(order.getId())) {
            throw new IllegalArgumentException("다른 입고의 라인입니다: " + ibLineId);
        }
        return ibLine;
    }

    private void receiveLine(IbOrder order, Loc staging, ReceiveRequest.Line line) {
        IbLine ibLine = findLine(order, line.getIbLineId());
        Prod prod = ibLine.getProd();
        long inspectUomQty = line.getInspectQty() != null ? line.getInspectQty() : 0;
        if (inspectUomQty < 1) {
            throw new IllegalArgumentException("검수수량은 1 이상이어야 합니다: " + prod.getProdCd());
        }

        // 검수는 입고단위(발주단위) 개수로 세고, 저장은 재고 저장 단위인 낱개(EA)로 환산한다.
        // 입고단위 정수만 받으므로 부분 박스는 표현할 수 없다 — 딱 안 떨어지는 잔량은 마감으로 미입고 확정
        long inspect = prod.toEaQty(inspectUomQty, prod.getInbUomCd());

        // 과입고 차단: 예정 잔량을 넘는 검수는 거부 (프론트도 같은 검증을 하지만 서버가 최종 방어선)
        long remaining = ibLine.getExpctQty() - ibLine.getRcvdQty();
        if (inspect > remaining) {
            throw new IllegalArgumentException("검수수량이 예정 잔량을 초과합니다: " + prod.getProdCd()
                    + " (잔량 " + remaining + ", 검수 환산 " + inspect + ")");
        }

        ibLine.receive(inspect);

        // 입고일자: 소급 등록 대비 라인별 입력 (비우면 오늘 = 실시간 등록)
        LocalDate receiptDt = line.getReceiptDt() != null ? line.getReceiptDt() : LocalDate.now();

        // 검수분: Lot 확보 → 스테이징 스냅샷 증가 → 재고 이력. 셋이 항상 한 트랜잭션
        Lot lot = findOrCreateLot(prod, line.getMfgDt(), receiptDt);
        invStore.increase(prod, staging, lot, inspect, TxTyp.RECEIVE,
                InvDocRef.ofIbLine(RefDocTyp.INBOUND, order.getIbNo(), ibLine.getId()));
    }

    /**
     * 같은 배치(상품+입고일자+제조일자)는 같은 Lot을 재사용한다
     * (증분 검수로 같은 라인을 여러 번 나눠 검수해도 Lot이 쪼개지지 않도록).
     * 유통기한 미관리 상품은 제조일자가 항상 null이라 사실상 상품+입고일자로만 구분된다.
     * 동시 검수의 "재사용 조회 → 채번 → 저장"이 겹치지 않는 것은 receive()가 미리 잡아 둔 상품 로우 락 덕이다.
     */
    private Lot findOrCreateLot(Prod prod, LocalDate mfgDt, LocalDate receiptDt) {
        LocalDate effectiveMfgDt = validateMfgDt(prod, mfgDt, receiptDt);
        return lotRepository.findByProdIdAndReceiptDtAndMfgDt(prod.getId(), receiptDt, effectiveMfgDt)
                .orElseGet(() -> createLot(prod, receiptDt, effectiveMfgDt));
    }

    /**
     * 유통기한 관리 상품만 제조일자를 검증해 그대로 쓰고, 미관리 상품은 입력값을 버리고 null로 통일한다
     * (미관리 상품의 Lot은 두 날짜가 항상 null인 것이 정의 — LotAttrChngService의 정정 차단과 같은 원칙).
     */
    private LocalDate validateMfgDt(Prod prod, LocalDate mfgDt, LocalDate receiptDt) {
        if (prod.getShelfLifeDays() == null) {
            return null;
        }
        if (mfgDt == null) {
            throw new IllegalArgumentException("제조일자는 필수입니다: " + prod.getProdCd());
        }
        if (mfgDt.isAfter(receiptDt)) {
            throw new IllegalArgumentException("제조일자가 입고일자보다 미래일 수 없습니다: " + prod.getProdCd());
        }
        return mfgDt;
    }

    /** 상품 로우 락(receive의 lockProds) 안에서만 부를 것 — 채번(nextLotNo)이 그 락에 얹혀 직렬화된다 */
    private Lot createLot(Prod prod, LocalDate receiptDt, LocalDate mfgDt) {
        LocalDate expiryDt = mfgDt != null ? mfgDt.plusDays(prod.getShelfLifeDays()) : null;
        return lotRepository.save(Lot.builder()
                .prod(prod)
                .lotNo(nextLotNo(prod, receiptDt))
                .receiptDt(receiptDt)
                .mfgDt(mfgDt)
                .expiryDt(expiryDt)
                .build());
    }

    /**
     * Lot 번호 채번: LOT-{입고일자}-{순번}. 순번은 상품별·입고일자별로 1부터 —
     * "이 상품이 그날 몇 번째 배치인가"라는 뜻이고, 유일성 단위도 uq_lot(prod_id, lot_no)라 이걸로 충분하다.
     * <p>
     * NbrService를 쓰지 않는 이유: 채번 규칙의 리셋 단위는 날짜뿐이라 상품별 리셋을 표현할 수 없고
     * (순번이 그날 창고 전체 통번이 되어 위 의미가 사라진다), nbr_seq 카운터 행이 상품이 달라도
     * 부딪히는 전역 직렬화 지점이 된다. 여기 채번은 receive가 잡아 둔 상품 로우 락에 얹혀
     * 상품 단위로만 직렬화된다. 건수+1이 안전한 것은 Lot이 삭제되지 않아 단조 증가이기 때문이다 —
     * 삭제가 생기면 번호가 재사용되니 그때는 기존 번호의 최대 순번 파싱으로 바꿀 것(uq_lot이 최후 방어).
     */
    private String nextLotNo(Prod prod, LocalDate receiptDt) {
        long seq = lotRepository.countByProdIdAndReceiptDt(prod.getId(), receiptDt) + 1;
        return String.format("LOT-%s-%03d", receiptDt.format(LOT_NO_DT_FMT), seq);
    }

    /** 입고 마감 — 상태 검증/전이는 엔티티가 한다. 잔량(예정-검수)은 미입고로 확정 */
    @Transactional
    public void close(Long ibOrderId) {
        IbOrder order = ibOrderRepository.findById(ibOrderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고예정입니다: " + ibOrderId));
        order.close();
    }

    /**
     * 입고건 전체의 검수 이력(RECEIVE 건) 목록. 최근 순 — 검수 화면의 「검수 이력」 탭이 쓴다.
     * <p>
     * 라인마다 부르지 않고 한 번에 받는다. 라인이 20개면 조회도 20번이 되기 때문이다.
     * 취소 판정(ADJUST가 가리키는 원본)도 전 라인분을 한 번에 모아 대조한다.
     */
    public List<ReceiptResponse> receipts(Long ibOrderId) {
        IbOrder order = ibOrderRepository.findById(ibOrderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고예정입니다: " + ibOrderId));
        List<Long> ibLineIds = order.getLines().stream().map(IbLine::getId).toList();

        List<InvHist> receiveRows = invHistRepository
                .findAllByIbLineIdInAndTxTypeOrderByCreatedAtDesc(ibLineIds, TxTyp.RECEIVE);
        Set<Long> cancelledIds = invHistRepository
                .findAllByIbLineIdInAndTxTypeOrderByCreatedAtDesc(ibLineIds, TxTyp.ADJUST)
                .stream()
                .map(InvHist::getCnclInvHistId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return receiveRows.stream()
                .map(r -> ReceiptResponse.from(r, cancelledIds.contains(r.getId())))
                .toList();
    }

    /** 특정 라인의 검수 이력(RECEIVE 건) 목록. 최근 순 — 검수 취소 대상 선택용. 이미 취소된 건은 cancelled=true로 표시 */
    public List<ReceiptResponse> receipts(Long ibOrderId, Long ibLineId) {
        IbLine ibLine = ibLineRepository.findById(ibLineId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고 라인입니다: " + ibLineId));
        if (!ibLine.getIbOrder().getId().equals(ibOrderId)) {
            throw new IllegalArgumentException("다른 입고의 라인입니다: " + ibLineId);
        }
        List<InvHist> receiveRows = invHistRepository.findAllByIbLineIdAndTxTypeOrderByCreatedAtDesc(ibLineId, TxTyp.RECEIVE);
        Set<Long> cancelledIds = invHistRepository.findAllByIbLineIdAndTxTypeOrderByCreatedAtDesc(ibLineId, TxTyp.ADJUST)
                .stream()
                .map(InvHist::getCnclInvHistId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return receiveRows.stream()
                .map(r -> ReceiptResponse.from(r, cancelledIds.contains(r.getId())))
                .toList();
    }

    /**
     * 검수 취소. 검수 건 하나(inv_hist RECEIVE 1건)를 되돌린다.
     * 원 이력은 그대로 두고 ADJUST(-수량)를 추가한다 (append-only 원장 원칙).
     */
    @Transactional
    public void cancelReceipt(Long ibOrderId, Long invHistId) {
        InvHist receipt = invHistRepository.findById(invHistId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 검수 이력입니다: " + invHistId));
        if (receipt.getTxTyp() != TxTyp.RECEIVE) {
            throw new IllegalArgumentException("검수 이력이 아닙니다: " + invHistId);
        }
        boolean alreadyCancelled = invHistRepository.findAllByIbLineIdAndTxTypeOrderByCreatedAtDesc(receipt.getIbLineId(), TxTyp.ADJUST)
                .stream()
                .anyMatch(a -> invHistId.equals(a.getCnclInvHistId()));
        if (alreadyCancelled) {
            throw new IllegalStateException("이미 취소된 검수 이력입니다: " + invHistId);
        }
        IbLine ibLine = ibLineRepository.findById(receipt.getIbLineId())
                .orElseThrow(() -> new IllegalStateException("검수 이력의 입고 라인을 찾을 수 없습니다: " + invHistId));
        IbOrder order = ibLine.getIbOrder();
        if (!order.getId().equals(ibOrderId)) {
            throw new IllegalArgumentException("다른 입고의 검수 이력입니다: " + invHistId);
        }
        // 전량 검수 시 자동으로 RECEIVED로 전이하므로, 적치 전이면 RECEIVED까지도 취소를 허용한다.
        // COMPLETED는 막아야 함 — 같은 Lot을 공유하는 다른 주문이 있으면 이 주문이 끝났어도
        // 스테이징 잔량이 남아있을 수 있어, 잔량 체크만으로는 완료된 주문의 수량이 되돌아갈 수 있다.
        if (order.getStatus() != IbStatus.RECEIVING && order.getStatus() != IbStatus.RECEIVED) {
            throw new IllegalStateException("적치가 완료되지 않은 입고만 검수를 취소할 수 있습니다 (" + order.getStatus().getLabel() + "): " + order.getIbNo());
        }

        Prod prod = receipt.getProd();
        long qty = receipt.getQty();
        // 스테이징 행 락 — 락 없이 읽고 줄이면 같은 행의 동시 적치·검수와 서로 덮어쓴다.
        // 예약(적치지시)과도 경합한다 — 검증과 차감 사이에 지시가 끼어들면 예약분을 밑에서 빼가게 된다
        Inv inv = invStore.lock(new InvKey(prod.getId(), receipt.getLoc().getId(), receipt.getLot().getId()))
                .orElseThrow(() -> new IllegalStateException("스테이징 재고를 찾을 수 없습니다: " + prod.getProdCd()));
        // 가용재고 기준 — 적치지시가 예약한 몫은 취소로 빼갈 수 없다.
        // 「미완료 적치지시가 있으면 차단」 특례를 따로 두지 않고 이 한 줄로 처리한다 (docs/design.md 「검수 취소」)
        if (inv.avalQty() < qty) {
            throw new IllegalStateException("이미 적치됐거나 적치지시가 예약한 수량이 있어 검수를 취소할 수 없습니다 (가용 "
                    + inv.avalQty() + "): " + prod.getProdCd());
        }

        ibLine.cancelReceive(qty);
        invStore.decrease(inv, qty, TxTyp.ADJUST,
                InvDocRef.ofIbLine(RefDocTyp.INBOUND, order.getIbNo(), ibLine.getId()).cancelling(receipt.getId()));
        order.reopenIfNoLongerFullyReceived(); // 전량검수로 자동 마감됐던 게 이 취소로 깨졌으면 RECEIVING으로 되돌림
    }
}
