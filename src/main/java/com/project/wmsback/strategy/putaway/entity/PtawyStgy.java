package com.project.wmsback.strategy.putaway.entity;

import com.project.wmsback.common.entity.BaseEntity;
import com.project.wmsback.strategy.core.condition.FieldCondition;
import com.project.wmsback.strategy.core.condition.SortCriterion;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * 적치 전략 헤더. 추천 시 tgt_cond 매칭(빈 목록 = 전체) 후보 중 prty 최소 1건이 선택된다.
 * 1차에서 전략은 추천만 한다 — 실행은 기존 즉시 MOVE 흐름 유지 (docs/st/전략_프로세스정의서.md §3).
 */
@Entity
@Table(name = "ptawy_stgy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PtawyStgy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ptawy_stgy_id")
    private Long id;

    /** 전략명. 표시용 — 실행에 사용하지 않는다 */
    @Column(name = "stgy_nm", nullable = false, length = 100)
    private String stgyNm;

    /** 낮을수록 우선. 동률은 (prty, id) 순으로 결정적 */
    @Column(name = "prty", nullable = false)
    private Integer prty;

    /** 적용대상 조건 — 빈 목록 = 전체 매칭. 필드는 putaway-target 레지스트리 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tgt_cond", nullable = false)
    private List<FieldCondition> tgtCond;

    /** 입수 단위 배수 절사 (낱개 혼적 방지). 입수 = ea_qty(입고단위)/ea_qty(출고단위) */
    @Column(name = "unt_splt_yn", nullable = false)
    private Boolean untSpltYn;

    /** 후보 정렬 기준. 빈 목록 = 기본(피킹순위 ASC → 로케이션코드 ASC) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "loc_srt", nullable = false)
    private List<SortCriterion> locSrt;

    @Column(name = "last_rvsn_no", nullable = false)
    private Long lastRvsnNo;

    @OneToMany(mappedBy = "stgy", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("srtSeq")
    private List<PtawyStgyStg> stages = new ArrayList<>();

    @Builder
    private PtawyStgy(String stgyNm, Integer prty, List<FieldCondition> tgtCond,
                      Boolean untSpltYn, List<SortCriterion> locSrt) {
        this.stgyNm = stgyNm;
        this.prty = prty != null ? prty : 0;
        this.tgtCond = tgtCond != null ? tgtCond : List.of();
        this.untSpltYn = untSpltYn != null && untSpltYn;
        this.locSrt = locSrt != null ? locSrt : List.of();
        this.lastRvsnNo = 1L;
    }

    /** 수정 저장 — 단계 목록 통째 교체 + 리비전 증가 (D4: 단계 끄기 = 행 삭제) */
    public long applyDefinition(String stgyNm, Integer prty, List<FieldCondition> tgtCond,
                                Boolean untSpltYn, List<SortCriterion> locSrt, List<PtawyStgyStg> newStages) {
        this.stgyNm = stgyNm;
        this.prty = prty != null ? prty : 0;
        this.tgtCond = tgtCond != null ? tgtCond : List.of();
        this.untSpltYn = untSpltYn != null && untSpltYn;
        this.locSrt = locSrt != null ? locSrt : List.of();
        this.stages.clear();
        newStages.forEach(this::addStage);
        this.lastRvsnNo++;
        return this.lastRvsnNo;
    }

    public void addStage(PtawyStgyStg stage) {
        stages.add(stage);
        stage.assignStgy(this);
    }
}
