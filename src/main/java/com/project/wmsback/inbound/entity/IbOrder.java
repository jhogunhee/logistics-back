package com.project.wmsback.inbound.entity;

import com.project.common.entity.BaseEntity;
import com.project.mdm.vendor.entity.Vendor;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 입고예정(ASN) 헤더. 부분입고 여부는 상태가 아니라 라인 수량(expct vs rcvd)에서 파생.
 * OMS 입고주문 확정 시에만 생성된다 (직접 등록 경로 없음).
 */
@Entity
@Table(name = "ib_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IbOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ib_order_id")
    private Long id;

    /** 입고 번호 (업무 식별자, 예: IB-20260714-001) */
    @Column(name = "ib_no", nullable = false, length = 30, unique = true)
    private String ibNo;

    /**
     * 이 ASN을 발생시킨 OMS 입고주문 ID. DB FK는 걸지 않는다(무결성은 애플리케이션이 보증) —
     * JPA로도 연관관계가 아니라 스칼라로 매핑한다. @ManyToOne OmsIbOrder로 두면 wmsback이
     * omsback을 import하게 되어 패키지 의존이 양방향이 되고, 나중에 OMS를 떼어낼 때
     * 이 지점이 그대로 걸림돌이 된다. 의존은 omsback → wmsback 한 방향만 허용한다.
     */
    @Column(name = "oms_ib_order_id", nullable = false, updatable = false)
    private Long omsIbOrderId;

    /** 워크플로 상태 (부분입고 상태 없음 — 라인 수량에서 파생) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private IbStatus status;

    /** 납품 벤더. 상위 입고주문의 벤더가 확정 시 그대로 넘어온다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    /** 입고 예정일 */
    @Column(name = "expct_de", nullable = false)
    private LocalDate expctDe;

    /**
     * 발주구분 (공통코드 ODR_DVSN: NRML 정상 / URGT 긴급 / RTNGS 반품입고).
     * 상위 입고주문(oms_ib_order.odr_dvsn)의 값이 확정 시 복사된다 — wmsback은 omsback을
     * import할 수 없어(의존 한 방향) 조회 대신 복사로 가져온다. 적치 전략 선택의 기준.
     */
    @Column(name = "odr_dvsn", nullable = false, length = 10, updatable = false)
    private String odrDvsn;

    /**
     * 입고확정 시각 — 사람이 입고확정 버튼을 누른 시각. {@link #confirm()}만 채운다.
     * {@code oms_ib_order.cfm_dt}(발주 확정 = ASN 생성 시각)와는 다른 사건이다.
     */
    @Column(name = "cfm_dt")
    private LocalDateTime cfmDt;

    @OneToMany(mappedBy = "ibOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IbLine> lines = new ArrayList<>();

    @Builder
    private IbOrder(String ibNo, Long omsIbOrderId, Vendor vendor, LocalDate expctDe, String odrDvsn) {
        this.ibNo = ibNo;
        this.omsIbOrderId = omsIbOrderId;
        this.vendor = vendor;
        this.expctDe = expctDe;
        this.odrDvsn = odrDvsn;
        this.status = IbStatus.SCHEDULED;
    }

    public void addLine(IbLine line) {
        lines.add(line);
        line.assignOrder(this);
    }

    /** 확정취소(삭제) 가능 검증. 검수가 시작되면(SCHEDULED 이후) 불가 — 삭제 자체는 서비스가 한다 */
    public void requireRevertible() {
        if (status != IbStatus.SCHEDULED) {
            throw new IllegalStateException("검수가 시작된 입고는 취소할 수 없습니다: " + ibNo);
        }
    }

    /** 검수 가능 상태 검증 + 첫 검수 시 SCHEDULED → RECEIVING 전이 */
    public void startReceiving() {
        if (status == IbStatus.SCHEDULED) {
            this.status = IbStatus.RECEIVING;
            return;
        }
        if (status != IbStatus.RECEIVING) {
            throw new IllegalStateException("검수할 수 없는 상태입니다 (" + status.getLabel() + "): " + ibNo);
        }
    }

    /**
     * 입고확정 — 유일한 종결 액션. 온 것은 전부 적치 완료된 뒤 사람이 눌러
     * 결품(예정-검수)을 못박으며 입고건을 닫는다. 자동 전이는 없다.
     * <p>
     * 전량 검수취소로 {@code rcvdQty}가 전부 0이 된 입고도 확정할 수 있다(전량 결품 확정) —
     * 이 입고의 유일한 종결 경로가 confirm이라(OMS 확정취소는 SCHEDULED만 통과) 막으면 영구 고착된다.
     * <p>
     * 미완료 적치지시 존재는 따로 검사하지 않는다 — DIRECTED 잔량 r&gt;0이면 그 배치의
     * 스테이징 예약 r이 남아 있고 예약분은 검수취소가 못 빼가므로, 그 라인은 반드시
     * {@code ptawyQty < rcvdQty}다. 아래 전제조건이 잔량 있는 미완료 지시를 논리적으로 배제한다.
     */
    public void confirm() {
        if (status != IbStatus.RECEIVING) {
            throw new IllegalStateException("검수가 시작된 입고만 확정할 수 있습니다 (" + status.getLabel() + "): " + ibNo);
        }
        if (!allLinesFullyPutaway()) {
            throw new IllegalStateException("검수된 수량이 전부 적치되어야 확정할 수 있습니다: " + ibNo);
        }
        this.status = IbStatus.CONFIRMED;
        this.cfmDt = LocalDateTime.now();
    }

    private boolean allLinesFullyPutaway() {
        return lines.stream().allMatch(l -> l.getPtawyQty().equals(l.getRcvdQty()));
    }
}
