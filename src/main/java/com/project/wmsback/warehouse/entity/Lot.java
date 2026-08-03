package com.project.wmsback.warehouse.entity;

import com.project.mdm.prod.entity.Prod;
import com.project.common.entity.BaseEntity;
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
@Table(name = "lot", uniqueConstraints = @UniqueConstraint(name = "uq_lot", columnNames = {"prod_id", "lot_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lot_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Prod prod;

    /** Lot 번호 (상품 내 유일. 벤더 표기 또는 입고일 기반 채번) */
    @Column(name = "lot_no", nullable = false, length = 30)
    private String lotNo;

    /** 입고일자. 상품+입고일자+제조일자가 같으면 기존 Lot을 재사용한다 (증분 검수 시 배치 중복 생성 방지) */
    @Column(name = "receipt_dt")
    private LocalDate receiptDt;

    /** 제조일자. 유통기한 미관리 상품의 Lot(NOLOT)은 NULL */
    @Column(name = "mfg_dt")
    private LocalDate mfgDt;

    /** 유통기한. 생성 시점의 Prod.shelfLifeDays로 계산해 저장한 스냅샷 (이후 상품 마스터 변경에 소급 영향 없음). NULL = 미관리 상품의 Lot (FEFO 맨 뒤 정렬, 잔여수명 필터 대상 아님) */
    @Column(name = "expiry_dt")
    private LocalDate expiryDt;

    @Builder
    private Lot(Prod prod, String lotNo, LocalDate receiptDt, LocalDate mfgDt, LocalDate expiryDt) {
        this.prod = prod;
        this.lotNo = lotNo;
        this.receiptDt = receiptDt;
        this.mfgDt = mfgDt;
        this.expiryDt = expiryDt;
    }

    /**
     * 속성 정정 (재고 속성변경 화면 — 제조일자·유통기한 오입력 정정).
     * 정정 가능한 것은 이 두 날짜뿐이다: prod·lotNo·receiptDt는 Lot의 정체성이라 바뀌지 않는다
     * (receiptDt는 배치 재사용 키의 일부이자 lotNo의 근거 — 바꾸면 번호가 입고일과 어긋난다).
     *
     * 업무 검증(관리 상품 여부 · 날짜 순서 · 배치 재사용 키 충돌)은 LotAttrChngService가 한다 —
     * 키 충돌 판정에 다른 Lot 조회가 필요해 엔티티 안에서 끝나지 않는다.
     * 수량과 무관하므로 이 정정은 inv·inv_hist를 건드리지 않는다.
     */
    public void correctAttr(LocalDate mfgDt, LocalDate expiryDt) {
        this.mfgDt = mfgDt;
        this.expiryDt = expiryDt;
    }
}
