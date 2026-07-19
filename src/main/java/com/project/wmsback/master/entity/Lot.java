package com.project.wmsback.master.entity;

import com.project.wmsback.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Lot(입고 단위 묶음). 입고 처리 시 생성. 유통기한이 FEFO 할당과 납품기한 필터의 기준.
 */
@Entity
@Table(name = "lot", uniqueConstraints = @UniqueConstraint(name = "uq_lot", columnNames = {"sku_id", "lot_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lot_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", nullable = false)
    private Sku sku;

    /** Lot 번호 (SKU 내 유일. 벤더 표기 또는 입고일 기반 채번) */
    @Column(name = "lot_no", nullable = false, length = 30)
    private String lotNo;

    /** 입고일자. SKU+입고일자+제조일자가 같으면 기존 Lot을 재사용한다 (증분 검수 시 배치 중복 생성 방지) */
    @Column(name = "receipt_dt")
    private LocalDate receiptDt;

    /** 제조일자. 유통기한 미관리 SKU의 Lot(NOLOT)은 NULL */
    @Column(name = "mfg_dt")
    private LocalDate mfgDt;

    /** 유통기한. 생성 시점의 SKU.shelfLifeDays로 계산해 저장한 스냅샷 (이후 SKU 마스터 변경에 소급 영향 없음). NULL = 미관리 SKU의 Lot (FEFO 맨 뒤 정렬, 잔여수명 필터 대상 아님) */
    @Column(name = "expiry_dt")
    private LocalDate expiryDt;

    @Builder
    private Lot(Sku sku, String lotNo, LocalDate receiptDt, LocalDate mfgDt, LocalDate expiryDt) {
        this.sku = sku;
        this.lotNo = lotNo;
        this.receiptDt = receiptDt;
        this.mfgDt = mfgDt;
        this.expiryDt = expiryDt;
    }
}
