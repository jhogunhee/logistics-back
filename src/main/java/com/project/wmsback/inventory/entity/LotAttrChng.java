package com.project.wmsback.inventory.entity;

import com.project.common.entity.BaseEntity;
import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.warehouse.entity.Lot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.LocalDate;

/**
 * Lot 속성 정정 이력 (append-only 자기완결 로그 — 재고 속성변경 화면).
 *
 * 수량 변동이 없어 inv_hist에 실을 수 없으므로 이 테이블이 정정의 유일한 원장이다
 * (보류 실적 2테이블과 같은 예외 성격). 변경 이벤트당 1행이고, 바뀌지 않은 필드도
 * 전/후에 같은 값이 들어간다 — 「그 시점에 Lot이 어떤 모습에서 어떤 모습이 됐나」가
 * 한 행으로 읽히는 편이 정정 이력의 용도에 맞는다. 취소 경로는 없고, 되돌리는 정정도 새 행이다.
 *
 * Lot 엔티티는 warehouse가 갖지만 이 클래스는 inventory에 둔다 — Lot을 쓰는 코드는 이미
 * warehouse 밖에 있고(생성은 inbound의 ReceivingService), warehouse는 wmsback 안에서
 * 아무 패키지도 import하지 않는 잎이라 그 상태를 깨지 않는다. 쓰는 주체는 업무 프로세스다.
 *
 */
@Entity
@Table(name = "lot_attr_chng")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LotAttrChng extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lot_attr_chng_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    /** 대상 Lot의 상품. Lot이 상품을 함의하지만 단독 조회를 가능하게 담는다 (InvHld와 같은 형태) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Prod prod;

    /** Lot 번호 스냅샷. 정정 대상이 아니라 지금은 lot과 항상 같지만, 로그를 자기완결로 두는 원칙을 따른다 */
    @Column(name = "lot_no", nullable = false, length = 30)
    private String lotNo;

    @Column(name = "bfr_mfg_dt")
    private LocalDate bfrMfgDt;

    @Column(name = "aft_mfg_dt")
    private LocalDate aftMfgDt;

    @Column(name = "bfr_expiry_dt")
    private LocalDate bfrExpiryDt;

    @Column(name = "aft_expiry_dt")
    private LocalDate aftExpiryDt;

    /** 정정 사유 코드 (공통코드 LOT_ATTR_RSN). ETC(기타)일 때만 rsnDscr 필수 */
    @Column(name = "rsn_cd", nullable = false, length = 10)
    private String rsnCd;

    @Column(name = "rsn_dscr", length = 200)
    private String rsnDscr;

    @Builder
    private LotAttrChng(Lot lot, Prod prod, String lotNo,
                        LocalDate bfrMfgDt, LocalDate aftMfgDt,
                        LocalDate bfrExpiryDt, LocalDate aftExpiryDt,
                        String rsnCd, String rsnDscr) {
        this.lot = lot;
        this.prod = prod;
        this.lotNo = lotNo;
        this.bfrMfgDt = bfrMfgDt;
        this.aftMfgDt = aftMfgDt;
        this.bfrExpiryDt = bfrExpiryDt;
        this.aftExpiryDt = aftExpiryDt;
        this.rsnCd = rsnCd;
        this.rsnDscr = rsnDscr;
    }
}
