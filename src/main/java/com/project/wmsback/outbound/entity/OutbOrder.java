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

    /** 편성된 출고 웨이브. NULL = 아직 미편성. 할당은 이 웨이브의 릴리즈로만 일어난다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wav_id")
    private OutbWave wave;

    /** 편입 출처 (전략 실행 / 수동 편성). wave와 짝이라 항상 함께 채우고 함께 비운다 — ck_outb_order_wav_reg */
    @Enumerated(EnumType.STRING)
    @Column(name = "wav_reg_typ", length = 10)
    private WavRegTyp wavRegTyp;

    /** 주문일 */
    @Column(name = "odr_de", nullable = false)
    private LocalDate odrDe;

    /** 출고 확정 시각 */
    @Column(name = "shmt_dt")
    private LocalDateTime shmtDt;

    @OneToMany(mappedBy = "outbOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OutbLine> lines = new ArrayList<>();

    @Builder
    private OutbOrder(String outbNo, Store store, LocalDate odrDe, String outbTyp, String vhclFltno) {
        this.outbNo = outbNo;
        this.store = store;
        this.odrDe = odrDe;
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

    /** 웨이브에서 제외 (주문 빼기/웨이브 해체/취소 시). 출처도 함께 비운다 — 짝 제약 유지 */
    public void unassignWave() {
        this.wave = null;
        this.wavRegTyp = null;
    }

    /** 취소. 할당 전(CREATED)만 가능 — 편성돼 있었다면 함께 웨이브에서 빠진다 */
    public void cancel() {
        if (status != OutbStatus.CREATED) {
            throw new IllegalStateException("할당 전(CREATED) 주문만 취소할 수 있습니다: " + outbNo);
        }
        this.status = OutbStatus.CANCELLED;
        unassignWave();
    }
}
