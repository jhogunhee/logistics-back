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

    /** 출고 확정 시각 — 재고가 창고를 떠난 것으로 확정된 시점. SHIPPED와 짝이다 */
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
     * 주문 상태 재산출 — <b>사실에서 상태를 다시 계산한다.</b> 전이 메서드 넷(첫 할당 · 첫 실적 ·
     * 전량 소진 · 전 할당 해제)이 나눠 하던 일을 한 자리로 모은 것이다 (2026-08-21).
     *
     * <p>넷으로 흩어져 있을 때 생긴 문제는 <b>「없애는 쪽」의 물음이 반쪽이었다</b>는 것이다 —
     * 할당해제가 「0건이 됐나」만 묻고 「남은 것이 다 집혔나」는 묻지 않아, 집품 완료된 할당 하나만
     * 남은 주문이 PICKING에 영구히 고였다(집을 것도 결품 종결할 것도 없다). 자리를 하나 더
     * 늘리는 대신 물음을 하나로 모은다.
     *
     * <p><b>판정 재료 셋은 전부 사실이고 현재 상태를 쓰지 않는다.</b> 실적이 붙은 할당 수가
     * 「집히기 시작했나」를 답하므로 ALLOCATED와 PICKING을 가르는 데 과거 상태가 필요 없고,
     * {@code ck_aloc_qty(aloc_qty > 0)} 덕에 「수량 0짜리 할당」이 없어 {@code unpickedCount == 0}이
     * 곧 전량 소진을 뜻한다. 판정 기준이 주문수량({@code odr_qty})이 아니라 <b>할당수량</b>인 것은
     * 그대로다 — 부분할당 주문은 할당분만 집품되면 PICKED가 되고 미할당 잔량은 부족 출고로 간다
     * (백오더 없음).
     *
     * <p><b>재료는 서비스가 집계해 넘긴다</b> — {@code outb_line}에 할당 수량 컬럼이 없어
     * ({@code outb_alloc} 집계로 파생시킨다) 엔티티 안에서는 셀 수 없다. 입고의
     * {@code IbOrder#confirm()}이 라인 수량으로 스스로 판정하는 것과 갈리는 지점이고,
     * 이유는 그쪽엔 수량 컬럼이 라인에 있기 때문이다.
     *
     * <p>부르는 자리는 <b>할당이 바뀌는 곳 전부</b>다 — 자동할당 · 수동할당 · 피킹 실행 ·
     * 결품 종결 · 할당해제. 지시취소는 할당을 건드리지 않으므로 부르지 않는다.
     *
     * <p><b>SHIPPED는 재산출하지 않는다.</b> 출고확정된 주문의 상태를 사실로 되계산하면 확정을
     * 무르는 셈이 된다. 재할당 진입이 SHIPPED 주문 라인을 먼저 걸러야 하고, 이 예외는 최후 방어다.
     *
     * @param allocCount    이 주문의 할당 건수
     * @param unpickedCount 그중 아직 소진되지 않은 것 ({@code pikng_qty < aloc_qty})
     * @param pickedCount   그중 실적이 붙은 것 ({@code pikng_qty > 0})
     */
    public void recalcStatus(long allocCount, long unpickedCount, long pickedCount) {
        if (status == OutbStatus.SHIPPED) {
            throw new IllegalStateException("출고확정된 주문의 상태는 되돌릴 수 없습니다: " + outbNo);
        }
        if (allocCount == 0) {
            // 할당 0건이면 되돌릴 수 있는 구간(확정취소 · 웨이브 빼기)이 다시 열려야 한다.
            // 실적이 붙은 할당은 해제가 막으므로 여기에 PICKING 이상이 올 수 없다.
            this.status = OutbStatus.CREATED;
        } else if (pickedCount == 0) {
            this.status = OutbStatus.ALLOCATED;
        } else if (unpickedCount > 0) {
            this.status = OutbStatus.PICKING;
        } else {
            this.status = OutbStatus.PICKED;
        }
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

    /**
     * 출고확정 — 유일한 종결 전이. 들어올 수 있는 상태는 둘뿐이다.
     * <ul>
     *   <li><b>PICKED</b> — 정상 확정. 할당 전량이 집품돼 SHIP-STAGE에 있다. 재고 반출은 서비스가
     *       할당마다 한다(이 엔티티는 할당을 모른다).</li>
     *   <li><b>CREATED</b> — 전량 미출고 확정. 할당이 0건이라 선점한 재고가 없다 — 지시취소 → 할당해제로
     *       비워졌거나 재고가 없어 한 번도 할당되지 못한 주문이다. 웨이브에서 빼지 않고도 닫히는
     *       유일한 문이라, 발행된 웨이브에 갇힌 주문의 마지막 출구다.</li>
     * </ul>
     * ALLOCATED · PICKING은 「출고작업중」이라 거부한다 — 집품을 끝내거나(피킹 · 결품 종결) 포기하려면
     * 지시취소 → 할당해제로 CREATED에 보낸 뒤 다시 와야 한다. 입고의 {@code IbOrder#confirm()}과
     * 같은 자리(사람이 눌러 닫는 종결, 자동 전이 없음)다.
     *
     * <p>웨이브 밖의 주문은 거부한다 — 그쪽의 출구는 OMS 확정취소이고, 웨이브 종료 판정이 이 전이를
     * 따라오기 때문이다.
     */
    public void ship() {
        if (wave == null) {
            throw new IllegalStateException("웨이브에 편성되지 않은 주문은 출고확정할 수 없습니다: " + outbNo);
        }
        if (status == OutbStatus.SHIPPED) {
            throw new IllegalStateException("이미 출고확정된 주문입니다: " + outbNo);
        }
        if (status != OutbStatus.PICKED && status != OutbStatus.CREATED) {
            throw new IllegalStateException("출고작업중인 주문은 출고확정할 수 없습니다 ("
                    + status.getLabel() + ") — 집품을 끝내거나, 포기하려면 지시취소 후 할당해제하세요: " + outbNo);
        }
        this.status = OutbStatus.SHIPPED;
        this.shmtDt = LocalDateTime.now();
    }

    // 취소(cancel) 메서드는 없다. 없앨 출고주문은 상위 OMS 주문의 확정취소가 행째로 지운다 —
    // 「지운 것도 아니고 쓰는 것도 아닌」 CANCELLED 상태를 두지 않기 위해서다 (OutbStatus 참고).
}
