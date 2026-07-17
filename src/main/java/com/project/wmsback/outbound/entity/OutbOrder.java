package com.project.wmsback.outbound.entity;

import com.project.wmsback.common.entity.BaseEntity;
import com.project.wmsback.master.entity.Store;
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
 * 출고 주문 헤더 (B2B 점포 출고). 피킹 시작 이후 취소는 v1 미지원.
 */
@Entity
@Table(name = "outb_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutbOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outb_order_id")
    private Long id;

    /** 출고 번호 (업무 식별자, 예: OB-20260714-001) */
    @Column(name = "outb_no", nullable = false, length = 30, unique = true)
    private String outbNo;

    /** 워크플로 상태 (부분할당 상태 없음 — 할당 수량에서 파생) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private OutbStatus status;

    /** 출고처 점포. 할당 시 이 점포의 잔여수명 허용률로 Lot 필터 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    /** 주문일 */
    @Column(name = "order_dt", nullable = false)
    private LocalDate orderDt;

    /** 출고 확정 시각 */
    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @OneToMany(mappedBy = "outbOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OutbLine> lines = new ArrayList<>();

    @Builder
    private OutbOrder(String outbNo, Store store, LocalDate orderDt) {
        this.outbNo = outbNo;
        this.store = store;
        this.orderDt = orderDt;
        this.status = OutbStatus.CREATED;
    }

    public void addLine(OutbLine line) {
        lines.add(line);
        line.assignOrder(this);
    }
}
