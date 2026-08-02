package com.project.wmsback.strategy.putaway.entity;

import com.project.wmsback.common.entity.BaseEntity;
import com.project.wmsback.strategy.core.condition.FieldCondition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

/**
 * 적치 단계. 레거시의 "위치 × 방식 카티전 곱"을 관리자가 순서를 직접 배열하는 목록으로 대체 —
 * 실행 순서(srt_seq)가 화면 drag&drop 순서 그대로다.
 * 상품 온도대 일치 + STORAGE는 조건이 아니라 모든 단계의 불변 전제라 저장하지 않는다.
 */
@Entity
@Table(name = "ptawy_stgy_stg")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PtawyStgyStg extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ptawy_stgy_stg_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ptawy_stgy_id", nullable = false)
    private PtawyStgy stgy;

    @Column(name = "srt_seq", nullable = false)
    private Integer srtSeq;

    /** 추천 방식 code (PutawayMethod enum name — 1차: SAME_PROD_LOC / EMPTY_LOC / ANY_LOC) */
    @Column(name = "mthd_cd", nullable = false, length = 30)
    private String mthdCd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mthd_para", nullable = false)
    private Map<String, Object> mthdPara;

    /** 이 단계를 시도할 입고라인 조건. 빈 목록 = 항상 시도 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "line_cond", nullable = false)
    private List<FieldCondition> lineCond;

    /** 후보 로케이션 범위 조건 (ZON·LOC_CD). 빈 목록 = 제한 없음 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "loc_cond", nullable = false)
    private List<FieldCondition> locCond;

    @Builder
    private PtawyStgyStg(Integer srtSeq, String mthdCd, Map<String, Object> mthdPara,
                         List<FieldCondition> lineCond, List<FieldCondition> locCond) {
        this.srtSeq = srtSeq;
        this.mthdCd = mthdCd;
        this.mthdPara = mthdPara != null ? mthdPara : Map.of();
        this.lineCond = lineCond != null ? lineCond : List.of();
        this.locCond = locCond != null ? locCond : List.of();
    }

    void assignStgy(PtawyStgy stgy) {
        this.stgy = stgy;
    }
}
