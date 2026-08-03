package com.project.wmsback.strategy.core.entity;

import com.project.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 전략 리비전 스냅샷 (append-only). 전략 저장마다 1행 — created_*가 곧 저장 시각/저장자.
 * 전략이 삭제돼도 남아 복원·감사의 근거가 된다 (D4: 실행 제외 = 물리삭제의 안전망).
 */
@Entity
@Table(name = "stgy_rvsn", uniqueConstraints = @UniqueConstraint(name = "uq_stgy_rvsn",
        columnNames = {"stgy_typ", "stgy_id", "rvsn_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StgyRvsn extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stgy_rvsn_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "stgy_typ", nullable = false, length = 10)
    private StgyTyp stgyTyp;

    /** 유형별 헤더 PK. FK 없음 — 원본 삭제 후에도 남는 느슨한 참조 */
    @Column(name = "stgy_id", nullable = false)
    private Long stgyId;

    /** 전략별 1부터 증가. 부여는 헤더 행 기준 last_rvsn_no+1, uq_stgy_rvsn이 최후 방어선 */
    @Column(name = "rvsn_no", nullable = false)
    private Long rvsnNo;

    /** 정의 전체(헤더+하위 구성) JSON. 구조: docs/st/전략_테이블설계서.md §8.1 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snpsht", nullable = false)
    private String snpsht;

    @Builder
    private StgyRvsn(StgyTyp stgyTyp, Long stgyId, Long rvsnNo, String snpsht) {
        this.stgyTyp = stgyTyp;
        this.stgyId = stgyId;
        this.rvsnNo = rvsnNo;
        this.snpsht = snpsht;
    }
}
