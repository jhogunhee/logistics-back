package com.project.wmsback.strategy.core.entity;

import com.project.wmsback.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 전략 실행 로그. 검수 위반(=검수 저장 롤백) 시에도 남아야 하므로 기록은
 * REQUIRES_NEW 별도 트랜잭션(StgyExecLogService). created_at = 실행 시각.
 */
@Entity
@Table(name = "stgy_exec_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StgyExecLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stgy_exec_log_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "stgy_typ", nullable = false, length = 10)
    private StgyTyp stgyTyp;

    @Column(name = "stgy_id", nullable = false)
    private Long stgyId;

    /** 실행에 사용된 리비전. stgy_rvsn과 조합해 판정 당시의 정의를 재구성한다 (P5) */
    @Column(name = "rvsn_no", nullable = false)
    private Long rvsnNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "trgr_typ", nullable = false, length = 10)
    private TrgrTyp trgrTyp;

    /** 대상 문서 번호 (입고번호 IB-…). 느슨한 참조 — 새 컬럼이라 ref 표기(rfn 아님) */
    @Column(name = "tgt_ref", length = 30)
    private String tgtRef;

    /** 사람용 한 줄 요약. 예: "라인 3건 중 위반 1건" */
    @Column(name = "rslt_smry", length = 200)
    private String rsltSmry;

    /** 건별 판정 상세 JSON. 구조: docs/st/전략_테이블설계서.md §8.2 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dcsn_trc")
    private String dcsnTrc;

    @Builder
    private StgyExecLog(StgyTyp stgyTyp, Long stgyId, Long rvsnNo, TrgrTyp trgrTyp,
                        String tgtRef, String rsltSmry, String dcsnTrc) {
        this.stgyTyp = stgyTyp;
        this.stgyId = stgyId;
        this.rvsnNo = rvsnNo;
        this.trgrTyp = trgrTyp;
        this.tgtRef = tgtRef;
        this.rsltSmry = rsltSmry;
        this.dcsnTrc = dcsnTrc;
    }
}
