package com.project.wmsback.outbound.entity;

import com.project.wmsback.inventory.entity.Inv;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 재고 할당 레코드. 어떤 주문라인이 어떤 재고(SKU+Loc+Lot)를 몇 개 예약했는지.
 * FEFO(+ 납품기한 필터) 결과가 기록된다. 할당 취소 시 삭제 + Inv.allocQty 복원.
 */
@Entity
@Table(name = "outb_alloc")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class OutbAlloc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outb_alloc_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outb_line_id", nullable = false)
    private OutbLine outbLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inv_id", nullable = false)
    private Inv inv;

    /** 할당 수량 (부분할당 허용: 라인 orderQty보다 합계가 작을 수 있음) */
    @Column(name = "alloc_qty", nullable = false)
    private Long allocQty;

    /** 피킹 완료 수량. 피킹 리스트는 할당을 로케이션 순으로 정렬해 생성 */
    @Column(name = "picked_qty", nullable = false)
    private Long pickedQty;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private OutbAlloc(OutbLine outbLine, Inv inv, Long allocQty) {
        this.outbLine = outbLine;
        this.inv = inv;
        this.allocQty = allocQty;
        this.pickedQty = 0L;
    }
}
