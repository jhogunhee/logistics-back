package com.project.wmsback.inbound.entity;

import com.project.wmsback.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    /** 워크플로 상태 (부분입고 상태 없음 — 라인 수량에서 파생) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private IbStatus status;

    /** 납품 벤더명 (v1은 벤더 마스터 없이 텍스트 보관) */
    @Column(name = "vndr_nm", nullable = false, length = 100)
    private String vndrNm;

    /** 입고 예정일 */
    @Column(name = "expct_dt", nullable = false)
    private LocalDate expctDt;

    /** 입고 마감(close) 시각. 마감은 미입고 잔량을 확정하는 명시적 액션 */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @OneToMany(mappedBy = "ibOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IbLine> lines = new ArrayList<>();

    @Builder
    private IbOrder(String ibNo, String vndrNm, LocalDate expctDt) {
        this.ibNo = ibNo;
        this.vndrNm = vndrNm;
        this.expctDt = expctDt;
        this.status = IbStatus.SCHEDULED;
    }

    public void addLine(IbLine line) {
        lines.add(line);
        line.assignOrder(this);
    }
}
