package com.project.wmsback.strategy.inspection.entity;

import com.project.wmsback.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * 검수 규칙. rule_cd는 InspectionRule enum의 name — 값 목록의 주인은
 * DB가 아니라 코드다 (P1). para는 저장 시 규칙별 validatePara로 검증된 값만 담는다 (P2).
 */
@Entity
@Table(name = "insp_plcy_rule", uniqueConstraints = @UniqueConstraint(name = "uq_insp_plcy_rule",
        columnNames = {"insp_plcy_id", "rule_cd"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspPlcyRule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "insp_plcy_rule_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insp_plcy_id", nullable = false)
    private InspPlcy plcy;

    /** 평가·표시 순서. 전 규칙이 실행되므로 결과에는 영향 없고 위반 목록 정렬에 쓰인다 */
    @Column(name = "srt_seq", nullable = false)
    private Integer srtSeq;

    @Column(name = "rule_cd", nullable = false, length = 30)
    private String ruleCd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "para", nullable = false)
    private Map<String, Object> para;

    @Builder
    private InspPlcyRule(Integer srtSeq, String ruleCd, Map<String, Object> para) {
        this.srtSeq = srtSeq;
        this.ruleCd = ruleCd;
        this.para = para != null ? para : Map.of();
    }

    void assignPlcy(InspPlcy plcy) {
        this.plcy = plcy;
    }
}
