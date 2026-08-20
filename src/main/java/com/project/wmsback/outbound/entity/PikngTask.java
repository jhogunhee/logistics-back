package com.project.wmsback.outbound.entity;

import com.project.common.entity.BaseEntity;
import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.Lot;
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 피킹지시. {@code putaway_task}(적치지시) · {@code inv_mov_task}(이동지시)와 동등한 위치의
 * 작업지시 문서다 — 웨이브 발행 시 할당({@code outb_alloc})과 <b>1:1</b>로 생성된다
 * (상품별 집약 없음 — 지시 = 할당이고, 웨이브가 주는 이점은 로케이션 순 정렬까지다).
 *
 * <p><b>등록이 예약을 만들지 않는 점이 이동지시와 다르다</b> — 예약은 할당이 이미 잡았고,
 * 실행(PICK)이 그 예약을 소진한다. 발행·취소는 웨이브 단위다 (웨이브가 발행 문서).
 *
 * <p>prod · fromLoc · lot은 <b>재고 키 스냅샷</b>이다. alloc → inv 조인으로도 얻을 수 있지만
 * inv 행은 수량이 0이 되면 삭제된다 — 마지막 피킹이 출발지 행을 비우면 조인 경로가 끊기므로,
 * 완료된 지시의 표시는 이 스냅샷이 담당한다 (inv_hist·inv_mov_task와 같은 형태).
 *
 * <p>항등식: {@code cmpl_qty = outb_alloc.pikng_qty = SUM(pikng_acrst.pikng_qty)} —
 * 지시 문서의 진행 / 주문 도메인의 진행 / 실행 원장이 같은 사실의 세 관점이고,
 * 실행 서비스가 한 자리에서 함께 갱신한다.
 */
@Entity
@Table(name = "pikng_task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PikngTask extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pikng_task_id")
    private Long id;

    /** 발행 웨이브. 발행·취소·피킹 화면의 조회 단위 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outb_wave_id", nullable = false)
    private OutbWave wave;

    /** 지시의 근거 할당 — 1:1 (부분 유니크 uq_pikng_task_alloc: 살아 있는 지시는 할당당 하나) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outb_alloc_id", nullable = false)
    private OutbAlloc outbAlloc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Prod prod;

    /** 집품 로케이션 = 할당된 재고의 로케이션 스냅샷. TO는 SHIP-STAGE 고정이라 컬럼으로 두지 않는다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_loc_id", nullable = false)
    private Loc fromLoc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    /**
     * 지시 수량 = 발행 시점의 aloc_qty. 발행 후 재할당·해제가 막혀 항등식이 유지된다.
     * {@link #closeShort}만 이 값을 낮추며, 그때 {@code outb_alloc.aloc_qty}도 같은 값으로
     * 함께 내려가므로 항등식은 그대로다.
     */
    @Column(name = "drct_qty", nullable = false)
    private Long drctQty;

    /** 실행(PICK) 완료 수량 누계. 부분 피킹 허용 — drctQty에 도달하면 DONE */
    @Column(name = "cmpl_qty", nullable = false)
    private Long cmplQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private PikngTaskStatus status;

    /** 집품 순서 (웨이브 내 1..N). 발행 시점 정렬 스냅샷 — 작업 중 마스터가 바뀌어도 순서가 흔들리지 않는다 */
    @Column(name = "srt_seq", nullable = false)
    private Integer srtSeq;

    /** 지시 완료 시각 (DONE 전이 시점) */
    @Column(name = "cmpl_dt")
    private LocalDateTime cmplDt;

    /**
     * 결품 수량 — {@link #closeShort}가 포기한 잔량. 종결이 {@code drct_qty}를 실적까지 낮추므로
     * <b>종결 후에는 어디에서도 파생시킬 수 없다</b>(원래 지시수량이 남지 않는다). 그래서 컬럼으로 둔다 —
     * 「저장은 파생 불가능한 것만」의 대상이고, DONE 이후 값이 바뀌지 않아 캐시가 아니라 사실이다.
     */
    @Column(name = "shotge_qty")
    private Long shotgeQty;

    /**
     * 결품 사유 코드 (공통코드 SHOTGE_RSN). {@link #closeShort}로 닫힌 지시에만 채워진다 —
     * 전량 집품으로 DONE이 된 지시는 NULL이라, 이 컬럼의 유무가 곧 결품 여부다.
     */
    @Column(name = "shotge_rsn_cd", length = 10)
    private String shotgeRsnCd;

    /** 기타 결품사유 텍스트. shotgeRsnCd = ETC일 때만 사용 (inv_hld.rsn_dscr과 같은 규칙) */
    @Column(name = "shotge_rsn_dscr", length = 200)
    private String shotgeRsnDscr;

    @Builder
    private PikngTask(OutbWave wave, OutbAlloc outbAlloc, Prod prod, Loc fromLoc, Lot lot,
                      Long drctQty, Integer srtSeq) {
        this.wave = wave;
        this.outbAlloc = outbAlloc;
        this.prod = prod;
        this.fromLoc = fromLoc;
        this.lot = lot;
        this.drctQty = drctQty;
        this.cmplQty = 0L;
        this.srtSeq = srtSeq;
        this.status = PikngTaskStatus.DIRECTED;
    }

    /** 잔여수량 (파생값 — 컬럼 아님) */
    public long remainingQty() {
        return drctQty - cmplQty;
    }

    /** 실행 반영 (부분 허용, 잔여수량 검증은 서비스가 먼저 한다). 전량 도달 시 DONE 전이 */
    public void execute(long qty) {
        this.cmplQty += qty;
        if (cmplQty.equals(drctQty)) {
            this.status = PikngTaskStatus.DONE;
            this.cmplDt = LocalDateTime.now();
        }
    }

    /**
     * 결품 종결 — <b>지시수량을 실적수량까지 낮춰 DONE으로 닫는다.</b> 시킨 만큼 실물이 없어
     * 잔량을 끝내 집을 수 없을 때의 유일한 출구다. {@code InvMovTask.cancelRemainder()}의
     * 부분확정 분기와 같은 조작이고, 예약 해제는 서비스가 함께 한다.
     *
     * <p><b>실적이 있을 때만 연다</b>({@code cmplQty > 0}). 실적 0인 지시는 웨이브 단위
     * {@link #cancel()}이 이미 덮으므로 여기서 또 열면 살아 있는 지시가 없는 ISSUED 웨이브가
     * 남고, 주문도 아직 ALLOCATED라 피킹완료 전이가 성립하지 않는다.
     *
     * <p>결품사유는 필수다 — 잔량을 없앤 근거가 이 컬럼 말고는 어디에도 남지 않는다.
     */
    public void closeShort(String rsnCd, String rsnDscr) {
        if (status != PikngTaskStatus.DIRECTED) {
            throw new IllegalStateException("지시 상태에서만 결품 종결할 수 있습니다 (" + status.getLabel() + ")");
        }
        if (cmplQty == 0L) {
            throw new IllegalStateException("피킹 실적이 없는 지시는 결품 종결이 아니라 지시취소 대상입니다");
        }
        this.shotgeQty = remainingQty();
        this.drctQty = this.cmplQty;
        this.shotgeRsnCd = rsnCd;
        this.shotgeRsnDscr = rsnDscr;
        this.status = PikngTaskStatus.DONE;
        this.cmplDt = LocalDateTime.now();
    }

    /**
     * 지시 취소 (행 보존 — CANCELLED 전이). 웨이브 단위 지시취소가 웨이브의 살아 있는 지시
     * 전량에 대해 호출한다. 실행 실적이 있으면 취소하지 않는다 — 실적이 남은 지시를 닫는 것은
     * {@link #closeShort}의 몫이다(취소가 아니라 결품 종결).
     */
    public void cancel() {
        if (status != PikngTaskStatus.DIRECTED) {
            throw new IllegalStateException("지시 상태에서만 취소할 수 있습니다 (" + status.getLabel() + ")");
        }
        if (cmplQty != 0L) {
            throw new IllegalStateException("이미 피킹된 수량이 있어 취소할 수 없습니다 (완료 " + cmplQty + ")");
        }
        this.status = PikngTaskStatus.CANCELLED;
    }
}
