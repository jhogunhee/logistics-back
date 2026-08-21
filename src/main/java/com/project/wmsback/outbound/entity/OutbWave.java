package com.project.wmsback.outbound.entity;

import com.project.common.entity.BaseEntity;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 출고 웨이브. <b>피킹지시의 발행 단위</b> — 여러 주문의 집품을 한 번에 지시하기 위한 그룹이다.
 * 지시 발행 이후 진행(피킹/확정)은 주문 단위라 웨이브는 여기서 역할이 끝난다.
 *
 * <p>할당은 웨이브를 <b>대상으로 실행</b>하지만(design.md 「웨이브」절) 웨이브는 실행 파라미터일 뿐,
 * 할당 결과는 전부 주문 상태·라인 수량·재고 예약에 남는다. 그래서 이 엔티티에는 할당과 관련된
 * 상태도 시각도 없다 — 상태 기계는 PLANNED → ISSUED 둘뿐이고 할당이 건드리지 않는다.
 *
 * 편성은 주문 쪽에서 관리한다(OutbOrder.assignWave). orders 컬렉션은 편성 현황
 * 집계(주문 수)용 읽기 전용 매핑으로, cascade/orphanRemoval을 두지 않는다 —
 * 주문은 웨이브보다 오래 살고 재편성될 수 있는 독립 애그리거트다.
 */
@Entity
@Table(name = "outb_wave")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutbWave extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outb_wave_id")
    private Long id;

    /** 웨이브 번호 (업무 식별자, 예: WV-20260718-001) */
    @Column(name = "wav_no", nullable = false, length = 30, unique = true)
    private String wavNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private WaveStatus status;

    /** 피킹지시 발행 시각. 사전의 일시 접미는 _dt다 — _at은 감사 컬럼 4종 전용 */
    @Column(name = "issued_dt")
    private LocalDateTime issuedDt;

    /**
     * 이 웨이브를 만든 웨이브 전략 (느슨한 참조 — FK 없음). NULL = 화면에서 수동 생성.
     * rvsnNo와 짝이다 (ck_outb_wave_stgy) — 「전략 id 있음 = 전략이 실제로 실행함」을 보장한다.
     */
    @Column(name = "wav_stgy_id")
    private Long wavStgyId;

    /** 생성에 사용된 전략 리비전. stgy_rvsn과 조합해 "그때의 조건"을 재구성한다 */
    @Column(name = "rvsn_no")
    private Long rvsnNo;

    @OneToMany(mappedBy = "wave")
    private List<OutbOrder> orders = new ArrayList<>();

    @Builder
    private OutbWave(String wavNo, Long wavStgyId, Long rvsnNo) {
        this.wavNo = wavNo;
        this.wavStgyId = wavStgyId;
        this.rvsnNo = rvsnNo;
        this.status = WaveStatus.PLANNED;
    }

    /** 편성 변경(주문 담기/빼기/해체)은 피킹지시 발행 전(PLANNED)에만 허용 */
    public void assertPlanned() {
        if (status != WaveStatus.PLANNED) {
            throw new IllegalStateException("이미 피킹지시가 발행된 웨이브는 편성을 변경할 수 없습니다: " + wavNo);
        }
    }

    /** 피킹지시 발행 — PLANNED → ISSUED. 지시 행(pikng_task) 생성은 서비스가 함께 한다 */
    public void issue() {
        if (status != WaveStatus.PLANNED) {
            throw new IllegalStateException("이미 피킹지시가 발행된 웨이브입니다: " + wavNo);
        }
        this.status = WaveStatus.ISSUED;
        this.issuedDt = LocalDateTime.now();
    }

    /**
     * 지시취소 — ISSUED → PLANNED 복귀. <b>호출 근거는 「살아 있는 지시가 0건」</b>이고 그것을 세는
     * 것은 서비스다 — 지시 행이 이 엔티티에 매달려 있지 않아 스스로 셀 수 없다.
     *
     * <p>부르는 자리는 둘이다. 웨이브 단위 취소는 실적 0을 먼저 확인하고 살아 있는 지시 전량을
     * 취소한 뒤 부르고, 지시 단위 취소는 고른 지시만 취소한 뒤 <b>남은 지시가 0건일 때만</b> 부른다
     * — 완료(DONE) 지시가 남아 있으면 부르지 않는다.
     */
    public void cancelIssue() {
        if (status != WaveStatus.ISSUED) {
            throw new IllegalStateException("피킹지시가 발행되지 않은 웨이브입니다: " + wavNo);
        }
        this.status = WaveStatus.PLANNED;
        this.issuedDt = null;
    }
}
