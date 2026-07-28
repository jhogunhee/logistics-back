package com.project.omsback.inbound.entity;

import com.project.wmsback.common.entity.BaseEntity;
import com.project.wmsback.master.entity.Vendor;
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
 * 입고주문(벤더 발주) 헤더. OMS 주문 원장이며, WMS 입고예정(ASN)의 유일한 발생지다.
 *
 * 확정(confirm)하면 같은 트랜잭션에서 ASN이 자동 생성되고, 그 이후의 창고 작업
 * (검수 · 적치 · 마감)은 전부 ASN 쪽에서 진행된다. 주문은 발주 내용의 원본으로만 남는다.
 */
@Entity
@Table(name = "oms_ib_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OmsIbOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "oms_ib_order_id")
    private Long id;

    /** 입고주문 번호 (업무 식별자, 예: PO-20260723-001). 확정 후 생기는 입고번호(IB-)와는 별개 */
    @Column(name = "oms_ib_no", nullable = false, length = 30, unique = true)
    private String omsIbNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private OmsIbStatus status;

    /** 납품 벤더. 확정 시 ASN으로 그대로 넘어간다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    /** 입고 예정일. ASN의 입고번호 채번(IB-YYYYMMDD-NNN) 기준일이기도 하다 */
    @Column(name = "expct_de", nullable = false)
    private LocalDate expctDe;

    /** 변환(ASN 생성) 시각. 변환취소하면 다시 null이 된다 */
    @Column(name = "converted_at")
    private LocalDateTime convertedAt;

    @OneToMany(mappedBy = "omsIbOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OmsIbLine> lines = new ArrayList<>();

    @Builder
    private OmsIbOrder(String omsIbNo, Vendor vendor, LocalDate expctDe) {
        this.omsIbNo = omsIbNo;
        this.vendor = vendor;
        this.expctDe = expctDe;
        this.status = OmsIbStatus.CREATED;
    }

    public void addLine(OmsIbLine line) {
        lines.add(line);
        line.assignOrder(this);
    }

    /**
     * WMS 작업문서(ASN)로 변환. ASN 생성 자체는 서비스가 이어서 수행한다 (엔티티는 상태 전이만 책임).
     * 재변환을 막는 게 핵심 — 통과시키면 같은 주문으로 ASN이 여러 건 생겨 예정수량이 부풀려진다.
     * (동시 요청은 상태 검사만으로 못 막으므로 ib_order의 유니크 인덱스가 최후 방어선이다)
     */
    public void convert() {
        if (status != OmsIbStatus.CREATED) {
            throw new IllegalStateException("작성 상태의 주문만 변환할 수 있습니다 (" + status.getLabel() + "): " + omsIbNo);
        }
        this.status = OmsIbStatus.CONVERTED;
        this.convertedAt = LocalDateTime.now();
    }

    /**
     * 변환취소. 생성된 ASN을 되돌리고 주문을 작성 상태로 원복해 재변환이 가능해진다.
     * ASN을 물릴 수 있는 상태인지(검수 시작 전)는 ASN 엔티티가 판정한다.
     */
    public void revertConvert() {
        if (status != OmsIbStatus.CONVERTED) {
            throw new IllegalStateException("변환된 주문만 변환취소할 수 있습니다 (" + status.getLabel() + "): " + omsIbNo);
        }
        this.status = OmsIbStatus.CREATED;
        this.convertedAt = null;
    }

    /**
     * 주문 취소. 변환 전(CREATED)만 가능.
     * 변환 뒤에는 이미 ASN이 나가 창고가 물건을 받을 준비를 한 상태라, 주문만 무르면
     * 예정 없는 입고가 남는다. 변환취소를 먼저 거쳐야 한다.
     */
    public void cancel() {
        if (status != OmsIbStatus.CREATED) {
            throw new IllegalStateException(
                    "작성 상태의 주문만 취소할 수 있습니다. 변환된 주문은 변환취소가 먼저입니다 ("
                            + status.getLabel() + "): " + omsIbNo);
        }
        this.status = OmsIbStatus.CANCELLED;
    }
}
