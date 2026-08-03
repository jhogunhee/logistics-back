package com.project.omsback.outbound.entity;

import com.project.common.entity.BaseEntity;
import com.project.mdm.store.entity.Store;
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
 * 출고주문(점포 수주) 헤더. OMS 주문 원장이며, WMS 출고주문의 유일한 발생지다.
 *
 * 확정(confirm)하면 같은 트랜잭션에서 WMS 출고주문이 자동 생성되고, 그 이후의 창고 작업
 * (웨이브 편성 · 할당 · 피킹 · 출고확정)은 전부 그쪽에서 진행된다. 주문은 수주 내용의
 * 원본으로만 남는다. 입고주문({@link com.project.omsback.inbound.entity.OmsIbOrder})과
 * 같은 구조이고, 다른 것은 상대가 벤더가 아니라 납품처(점포)라는 점뿐이다.
 */
@Entity
@Table(name = "oms_outb_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OmsOutbOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "oms_outb_order_id")
    private Long id;

    /** 출고주문 번호 (업무 식별자, 예: SO-20260803-001). 확정 후 생기는 출고번호(OB-)와는 별개 */
    @Column(name = "oms_outb_no", nullable = false, length = 30, unique = true)
    private String omsOutbNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private OmsOutbStatus status;

    /** 납품처 점포. 확정 시 WMS 출고주문으로 그대로 넘어간다 (할당의 잔여수명 필터 기준) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    /**
     * 출고유형 (공통코드 {@code OUTB_TYP}: NRML 일반출고 / RTNGS 반품출고).
     * 웨이브 편성 조건의 기준값이라 주문 시점에 정해져 확정 때 창고로 복사된다 —
     * 값 목록은 공통코드가 소유하므로 enum으로 두지 않는다.
     */
    @Column(name = "outb_typ", nullable = false, length = 10)
    private String outbTyp;

    /** 차량편수 (공통코드 {@code VHCL_FLTNO}: 1편·2편…). null = 배차 미정. 확정 시 함께 복사된다 */
    @Column(name = "vhcl_fltno", length = 10)
    private String vhclFltno;

    /** 출고 예정일. WMS 출고주문의 출고번호 채번(OB-YYYYMMDD-NNN) 기준일이자 웨이브 편성 기간의 기준 */
    @Column(name = "expct_de", nullable = false)
    private LocalDate expctDe;

    /** 수주 담당자명. 감사 컬럼 createdBy(로그인 계정)와는 별개다 */
    @Column(name = "pic_nm", length = 30)
    private String picNm;

    /** 비고. 점포 전달사항 등 자유 입력. 확정 시 WMS로 넘기지 않는다 */
    @Column(name = "rmk", length = 200)
    private String rmk;

    /** 확정(WMS 출고주문 생성) 시각. 확정취소하면 다시 null이 된다 */
    @Column(name = "cfm_dt")
    private LocalDateTime cfmDt;

    @OneToMany(mappedBy = "omsOutbOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OmsOutbLine> lines = new ArrayList<>();

    /** 출고유형 기본값 — 컬럼 DEFAULT('NRML')와 같은 값이어야 한다 */
    public static final String DFLT_OUTB_TYP = "NRML";

    @Builder
    private OmsOutbOrder(String omsOutbNo, Store store, String outbTyp, String vhclFltno,
                         LocalDate expctDe, String picNm, String rmk) {
        this.omsOutbNo = omsOutbNo;
        this.store = store;
        this.outbTyp = outbTyp != null ? outbTyp : DFLT_OUTB_TYP;
        this.vhclFltno = vhclFltno;
        this.expctDe = expctDe;
        this.picNm = picNm;
        this.rmk = rmk;
        this.status = OmsOutbStatus.CREATED;
    }

    public void addLine(OmsOutbLine line) {
        lines.add(line);
        line.assignOrder(this);
    }

    /**
     * 주문 내용 수정. 작성(CREATED) 상태만 가능하다.
     * <p>
     * 확정된 주문을 고치면 이미 나간 WMS 출고주문의 수량·납품처와 어긋난다 — 창고는 옛 내용으로
     * 할당·피킹을 준비한 상태이고, 주문만 바꿔도 그 작업지시가 따라오지 않는다. 고치려면
     * 확정취소가 먼저다.
     * <p>
     * 라인은 통째로 갈아끼운다. 어느 라인이 남고 어느 라인이 바뀌었는지를 클라이언트가
     * 알려주지 않아도 되게 하려는 것이고, orphanRemoval이 빠진 라인을 지운다.
     */
    public void update(Store store, String outbTyp, String vhclFltno, LocalDate expctDe,
                       String picNm, String rmk, List<OmsOutbLine> newLines) {
        if (status != OmsOutbStatus.CREATED) {
            throw new IllegalStateException(
                    "작성 상태의 주문만 수정할 수 있습니다. 확정된 주문은 확정취소가 먼저입니다 ("
                            + status.getLabel() + "): " + omsOutbNo);
        }
        this.store = store;
        this.outbTyp = outbTyp;
        this.vhclFltno = vhclFltno;
        this.expctDe = expctDe;
        this.picNm = picNm;
        this.rmk = rmk;
        lines.clear();
        newLines.forEach(this::addLine);
    }

    /**
     * WMS 출고주문 생성을 동반하는 확정. 생성 자체는 서비스가 이어서 수행한다 (엔티티는 상태 전이만 책임).
     * 재확정을 막는 게 핵심 — 통과시키면 같은 주문으로 창고 문서가 여러 건 생겨 출고수량이 부풀려진다.
     * (동시 요청은 상태 검사만으로 못 막으므로 outb_order의 유니크 인덱스가 최후 방어선이다)
     */
    public void confirm() {
        if (status != OmsOutbStatus.CREATED) {
            throw new IllegalStateException("작성 상태의 주문만 확정할 수 있습니다 (" + status.getLabel() + "): " + omsOutbNo);
        }
        this.status = OmsOutbStatus.CONFIRMED;
        this.cfmDt = LocalDateTime.now();
    }

    /**
     * 확정취소. 생성된 WMS 출고주문을 되돌리고 주문을 작성 상태로 원복해 재확정이 가능해진다.
     * 물릴 수 있는 상태인지(웨이브 편성·할당 시작 전)는 WMS 엔티티가 판정한다.
     */
    public void revertConfirm() {
        if (status != OmsOutbStatus.CONFIRMED) {
            throw new IllegalStateException("확정된 주문만 확정취소할 수 있습니다 (" + status.getLabel() + "): " + omsOutbNo);
        }
        this.status = OmsOutbStatus.CREATED;
        this.cfmDt = null;
    }

    /**
     * 지울 수 있는 주문인지. 확정 전(CREATED)만 가능하다.
     * 확정 뒤에는 이미 창고 문서가 나가 웨이브·할당이 걸릴 수 있는 상태라, 주문만 없애면
     * 원장 없는 출고가 남는다. 확정취소를 먼저 거쳐야 한다.
     */
    public void requireDeletable() {
        if (status != OmsOutbStatus.CREATED) {
            throw new IllegalStateException(
                    "작성 상태의 주문만 삭제할 수 있습니다. 확정된 주문은 확정취소가 먼저입니다 ("
                            + status.getLabel() + "): " + omsOutbNo);
        }
    }
}
