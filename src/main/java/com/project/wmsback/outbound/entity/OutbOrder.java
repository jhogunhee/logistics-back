package com.project.wmsback.outbound.entity;

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
 * 출고 주문 헤더 (B2B 점포 출고). 피킹 시작 이후 취소는 v1 미지원.
 *
 * <p>OMS 출고주문 확정으로만 생성된다 — WMS에는 등록 엔드포인트가 없다 (입고예정 ASN과 같은 구조).
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

    /**
     * 이 출고주문을 발생시킨 OMS 출고주문.
     * <p>
     * <b>연관관계가 아니라 스칼라 Long이다</b> — 패키지 의존을 omsback → wmsback 한 방향으로
     * 유지하기 위해서다(wmsback은 omsback을 모른다). ib_order.omsIbOrderId와 같은 형태이고,
     * 이 규칙을 실제로 떠받치는 지점이므로 타입을 바꾸지 말 것.
     */
    @Column(name = "oms_outb_order_id", nullable = false)
    private Long omsOutbOrderId;

    /** 워크플로 상태 (부분할당 상태 없음 — 할당 수량에서 파생) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private OutbStatus status;

    /**
     * 출고유형 (공통코드 OUTB_TYP — NRML 일반출고 / RTNGS 반품출고).
     * 웨이브 편성 조건의 기준값이다 — 값 목록은 공통코드가 소유하므로 enum으로 두지 않는다.
     */
    @Column(name = "outb_typ", nullable = false, length = 10)
    private String outbTyp;

    /** 차량편수 (공통코드 VHCL_FLTNO — 1편·2편…). NULL = 배차 미정 */
    @Column(name = "vhcl_fltno", length = 10)
    private String vhclFltno;

    /** 출고처 점포. 할당 시 이 점포의 잔여수명 허용률로 Lot 필터 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    /** 편성된 출고 웨이브. NULL = 아직 미편성. 주문은 웨이브에 편성돼야 피킹지시를 받는다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wav_id")
    private OutbWave wave;

    /** 편입 출처 (전략 실행 / 수동 편성). wave와 짝이라 항상 함께 채우고 함께 비운다 — ck_outb_order_wav_reg */
    @Enumerated(EnumType.STRING)
    @Column(name = "wav_reg_typ", length = 10)
    private WavRegTyp wavRegTyp;

    /** 주문일 = 상위 OMS 출고주문이 등록된 날. 「언제 들어온 주문인가」를 보는 값이다 */
    @Column(name = "odr_de", nullable = false)
    private LocalDate odrDe;

    /**
     * 출고 예정일. 출고번호 채번(OB-YYYYMMDD-NNN) 기준일이자 웨이브 편성 대상 기간의 기준이다 —
     * 웨이브는 「같은 날 나갈 주문」을 묶는 단위라 주문일이 아니라 이쪽을 본다.
     */
    @Column(name = "expct_de", nullable = false)
    private LocalDate expctDe;

    /** 출고 확정 시각 */
    @Column(name = "shmt_dt")
    private LocalDateTime shmtDt;

    @OneToMany(mappedBy = "outbOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OutbLine> lines = new ArrayList<>();

    @Builder
    private OutbOrder(String outbNo, Long omsOutbOrderId, Store store, LocalDate odrDe,
                      LocalDate expctDe, String outbTyp, String vhclFltno) {
        this.outbNo = outbNo;
        this.omsOutbOrderId = omsOutbOrderId;
        this.store = store;
        this.odrDe = odrDe;
        this.expctDe = expctDe;
        this.outbTyp = outbTyp != null ? outbTyp : DFLT_OUTB_TYP;
        this.vhclFltno = vhclFltno;
        this.status = OutbStatus.CREATED;
    }

    /** 출고유형 기본값 — 컬럼 DEFAULT('NRML')와 같은 값이어야 한다 */
    public static final String DFLT_OUTB_TYP = "NRML";

    public void addLine(OutbLine line) {
        lines.add(line);
        line.assignOrder(this);
    }

    /**
     * 웨이브 편성. 아직 할당 전(CREATED)이고 다른 웨이브에 속하지 않은 주문만 담을 수 있다.
     * 웨이브가 PLANNED인지는 호출 전 OutbWave.assertPlanned()로 검증한다.
     * 「이미 편성된 주문은 거부」가 곧 전략 실행의 선점 규칙이다 — 먼저 실행된 전략이 주문을 가져간다.
     */
    public void assignWave(OutbWave wave, WavRegTyp regTyp) {
        if (status != OutbStatus.CREATED) {
            throw new IllegalStateException("할당 전(CREATED) 주문만 웨이브에 담을 수 있습니다: " + outbNo);
        }
        if (this.wave != null) {
            throw new IllegalStateException("이미 웨이브에 편성된 주문입니다: " + outbNo);
        }
        this.wave = wave;
        this.wavRegTyp = regTyp;
    }

    /**
     * 웨이브에서 제외 (주문 빼기/웨이브 해체/취소 시). 출처도 함께 비운다 — 짝 제약 유지.
     *
     * <p>담기와 마찬가지로 <b>할당 전(CREATED)</b>만 허용한다. 할당이 시작된 주문을 웨이브에서 빼면
     * 그 주문의 할당 레코드가 어느 웨이브의 피킹지시에도 속하지 않는 미아가 된다 —
     * 되돌리려면 할당 해제가 먼저다.
     */
    public void unassignWave() {
        if (status != OutbStatus.CREATED) {
            throw new IllegalStateException("할당이 시작된 주문은 웨이브에서 뺄 수 없습니다: " + outbNo);
        }
        this.wave = null;
        this.wavRegTyp = null;
    }

    /**
     * 첫 할당 시 CREATED → ALLOCATED 전이. 이미 ALLOCATED면 그대로 둔다 —
     * 부분할당 뒤 재할당이 같은 메서드를 여러 번 호출하기 때문이다.
     *
     * <p><b>부분할당도 ALLOCATED다.</b> 헤더 상태는 워크플로 단계만 표현하고, 부분인지 전량인지는
     * {@code odr_qty} 와 할당 합계 비교로 파생시킨다 — {@code PARTIALLY_*} 를 두지 않는 원칙
     * (「상태와 수량의 분담」). 그래서 이 메서드는 수량을 보지 않는다.
     */
    public void allocate() {
        if (status == OutbStatus.CREATED) {
            this.status = OutbStatus.ALLOCATED;
            return;
        }
        if (status != OutbStatus.ALLOCATED) {
            throw new IllegalStateException("할당할 수 없는 상태입니다 (" + status.getLabel() + "): " + outbNo);
        }
    }

    /**
     * 할당이 한 건도 남지 않았을 때 ALLOCATED → CREATED 복귀. 이미 CREATED면 그대로 둔다.
     *
     * <p>이 복귀가 없으면 <b>상태는 ALLOCATED인데 할당 레코드가 0건인 주문</b>이 남는다.
     * 그러면 {@link #requireRevertible()}(확정취소)도 {@link #unassignWave()}(웨이브에서 빼기)도
     * 영영 열리지 않아, 되돌릴 방법이 없는 상태로 고착된다.
     *
     * <p><b>할당 잔존 여부는 서비스가 판단해 호출한다</b> — {@code outb_line} 에 할당 수량 컬럼이
     * 없어서(할당은 {@code outb_alloc} 집계로 파생시킨다) 엔티티 안에서는 셀 수 없다.
     * 입고의 {@code reopenIfNoLongerFullyReceived()} 가 라인 수량으로 스스로 판정하는 것과
     * 갈리는 지점이고, 이유는 그쪽 {@code ib_line} 에는 검수 수량 컬럼이 있기 때문이다.
     */
    public void revertToCreated() {
        if (status == OutbStatus.CREATED) {
            return;
        }
        if (status != OutbStatus.ALLOCATED) {
            throw new IllegalStateException(
                    "피킹이 시작된 출고주문은 할당 이전으로 되돌릴 수 없습니다 ("
                            + status.getLabel() + "): " + outbNo);
        }
        this.status = OutbStatus.CREATED;
    }

    /**
     * 상위 주문의 확정취소로 이 문서를 물릴 수 있는지. 할당 전(CREATED)이고 <b>웨이브에 편성되기 전</b>만 가능하다.
     * <p>
     * 확정취소는 이 행을 삭제한다. 웨이브에 담긴 뒤에 지우면 그 웨이브의 피킹지시가 존재하지 않는
     * 주문을 가리키게 되고, 편성 화면·실행 로그에 남은 편입 이력도 근거를 잃는다 — 되돌리려면
     * 웨이브에서 빼는 것이 먼저다. (ASN의 requireRevertible()이 검수 시작을 막는 것과 같은 자리)
     */
    public void requireRevertible() {
        if (status != OutbStatus.CREATED) {
            throw new IllegalStateException("할당이 시작된 출고주문은 취소할 수 없습니다: " + outbNo);
        }
        if (wave != null) {
            throw new IllegalStateException(
                    "웨이브에 편성된 출고주문은 취소할 수 없습니다. 웨이브에서 먼저 빼세요: " + outbNo);
        }
    }

    // 취소(cancel) 메서드는 없다. 없앨 출고주문은 상위 OMS 주문의 확정취소가 행째로 지운다 —
    // 「지운 것도 아니고 쓰는 것도 아닌」 CANCELLED 상태를 두지 않기 위해서다 (OutbStatus 참고).
}
