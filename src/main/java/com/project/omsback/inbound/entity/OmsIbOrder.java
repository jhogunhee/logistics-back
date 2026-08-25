package com.project.omsback.inbound.entity;

import com.project.common.entity.BaseEntity;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.store.entity.Store;
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

    /** 납품 벤더. 반품입고(odr_dvsn=RTNGS)는 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    /** 반품 점포. 반품입고만 — 벤더와 둘 중 정확히 하나 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    /** 원 출고번호 (느슨한 참조, 선택). 반품입고만 — 라인 미리채움의 출처를 남긴다 */
    @Column(name = "ref_outb_no", length = 30)
    private String refOutbNo;

    /** 입고 예정일. ASN의 입고번호 채번(IB-YYYYMMDD-NNN) 기준일이기도 하다 */
    @Column(name = "expct_de", nullable = false)
    private LocalDate expctDe;

    /** 발주구분 (공통코드 ODR_DVSN: NRML 정상 / URGT 긴급 / RTNGS 반품입고). 반품은 상대처(점포)·수량 단위(출고단위)·검수 판정(양품/불량)을 가른다 */
    @Column(name = "odr_dvsn", nullable = false, length = 10)
    private String odrDvsn;

    /** 발주 담당자명. 감사 컬럼 createdBy(로그인 계정)와는 별개다 */
    @Column(name = "pic_nm", length = 30)
    private String picNm;

    /** 비고. 벤더 전달사항 등 자유 입력. 확정 시 ASN으로 넘기지 않는다 */
    @Column(name = "rmk", length = 200)
    private String rmk;

    /** 확정(ASN 생성) 시각. 확정취소하면 다시 null이 된다 */
    @Column(name = "cfm_dt")
    private LocalDateTime cfmDt;

    @OneToMany(mappedBy = "omsIbOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OmsIbLine> lines = new ArrayList<>();

    public static final String RTNGS = "RTNGS";

    @Builder
    private OmsIbOrder(String omsIbNo, Vendor vendor, Store store, String refOutbNo, LocalDate expctDe,
                       String odrDvsn, String picNm, String rmk) {
        requirePartnerMatches(odrDvsn, vendor, store, omsIbNo);
        this.omsIbNo = omsIbNo;
        this.vendor = vendor;
        this.store = store;
        this.refOutbNo = RTNGS.equals(odrDvsn) ? refOutbNo : null;
        this.expctDe = expctDe;
        this.odrDvsn = odrDvsn;
        this.picNm = picNm;
        this.rmk = rmk;
        this.status = OmsIbStatus.CREATED;
    }

    private static void requirePartnerMatches(String odrDvsn, Vendor vendor, Store store, String no) {
        boolean rtngs = RTNGS.equals(odrDvsn);
        if (rtngs && (store == null || vendor != null)) {
            throw new IllegalArgumentException("반품입고는 점포만 상대처로 둘 수 있습니다: " + no);
        }
        if (!rtngs && (vendor == null || store != null)) {
            throw new IllegalArgumentException("정상 입고는 벤더만 상대처로 둘 수 있습니다: " + no);
        }
    }

    public boolean isRtngs() {
        return RTNGS.equals(odrDvsn);
    }

    /** 발주 수량의 단위 — 정상은 입고단위, 반품은 출고단위(점포가 받은 단위로 돌아온다) */
    public String odrUomCd(Prod prod) {
        return isRtngs() ? prod.getOutbUomCd() : prod.getInbUomCd();
    }

    public void addLines(List<OmsIbLine> newLines) {
        for (OmsIbLine line : newLines) {
            addLine(line);
        }
    }

    private void addLine(OmsIbLine line) {
        lines.add(line);
        line.assignOrder(this);
    }

    /**
     * 주문 내용 수정. 작성(CREATED) 상태만 가능하다.
     * <p>
     * 확정된 주문을 고치면 이미 나간 ASN의 예정수량과 어긋난다 — 창고는 옛 수량으로 물건을
     * 받을 준비를 한 상태이고, 주문만 바꿔도 그 예정이 따라오지 않는다. 고치려면 확정취소가
     * 먼저다. 취소된 주문은 되살리는 개념이 없으므로 역시 막는다.
     * <p>
     * 라인은 통째로 갈아끼운다. 어느 라인이 남고 어느 라인이 바뀌었는지를 클라이언트가
     * 알려주지 않아도 되게 하려는 것이고, orphanRemoval이 빠진 라인을 지운다.
     */
    public void update(Vendor vendor, Store store, String refOutbNo, LocalDate expctDe, String odrDvsn,
                       String picNm, String rmk, List<OmsIbLine> newLines) {
        if (status != OmsIbStatus.CREATED) {
            throw new IllegalStateException(
                    "작성 상태의 주문만 수정할 수 있습니다. 확정된 주문은 확정취소가 먼저입니다 ("
                            + status.getLabel() + "): " + omsIbNo);
        }
        requirePartnerMatches(odrDvsn, vendor, store, omsIbNo);
        this.vendor = vendor;
        this.store = store;
        this.refOutbNo = RTNGS.equals(odrDvsn) ? refOutbNo : null;
        this.expctDe = expctDe;
        this.odrDvsn = odrDvsn;
        this.picNm = picNm;
        this.rmk = rmk;
        lines.clear();
        addLines(newLines);
    }

    /**
     * WMS 작업문서(ASN) 생성을 동반하는 확정. ASN 생성 자체는 서비스가 이어서 수행한다 (엔티티는 상태 전이만 책임).
     * 재확정을 막는 게 핵심 — 통과시키면 같은 주문으로 ASN이 여러 건 생겨 예정수량이 부풀려진다.
     * (동시 요청은 상태 검사만으로 못 막으므로 ib_order의 유니크 인덱스가 최후 방어선이다)
     */
    public void confirm() {
        if (status != OmsIbStatus.CREATED) {
            throw new IllegalStateException("작성 상태의 주문만 확정할 수 있습니다 (" + status.getLabel() + "): " + omsIbNo);
        }
        this.status = OmsIbStatus.CONFIRMED;
        this.cfmDt = LocalDateTime.now();
    }

    /**
     * 확정취소. 생성된 ASN을 되돌리고 주문을 작성 상태로 원복해 재확정이 가능해진다.
     * ASN을 물릴 수 있는 상태인지(검수 시작 전)는 ASN 엔티티가 판정한다.
     */
    public void revertConfirm() {
        if (status != OmsIbStatus.CONFIRMED) {
            throw new IllegalStateException("확정된 주문만 확정취소할 수 있습니다 (" + status.getLabel() + "): " + omsIbNo);
        }
        this.status = OmsIbStatus.CREATED;
        this.cfmDt = null;
    }

    /**
     * 지울 수 있는 주문인지. 확정 전(CREATED)만 가능하다.
     * 확정 뒤에는 이미 ASN이 나가 창고가 물건을 받을 준비를 한 상태라, 주문만 없애면
     * 예정 없는 입고가 남는다. 확정취소를 먼저 거쳐야 한다.
     */
    public void requireDeletable() {
        if (status != OmsIbStatus.CREATED) {
            throw new IllegalStateException(
                    "작성 상태의 주문만 삭제할 수 있습니다. 확정된 주문은 확정취소가 먼저입니다 ("
                            + status.getLabel() + "): " + omsIbNo);
        }
    }
}
