package com.project.wmsback.strategy.putaway.entity;

import com.project.wmsback.common.entity.BaseEntity;
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
 * 적치 전략 헤더. 선택 기준은 발주구분(odr_dvsn) 단 하나 — 입고의 유형과 일치하는 전략,
 * 없으면 전체(odr_dvsn = NULL) 전략, 그것도 없으면 수동 폴백. 유형당 1개(UNIQUE)라
 * 우선순위 숫자가 없다. 1차에서 전략은 추천만 한다 — 실행(즉시 MOVE)은 기존 PutawayService 담당.
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

    /** 적용대상 발주구분 (공통코드 ODR_DVSN — NRML/URGT). NULL = 전체. 반품(RTNGS)은 스코프 아웃 */
    @Column(name = "odr_dvsn", length = 10)
    private String odrDvsn;

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
    private PtawyStgy(String stgyNm, String odrDvsn, Boolean untSpltYn, List<SortCriterion> locSrt) {
        this.stgyNm = stgyNm;
        this.odrDvsn = odrDvsn;
        this.untSpltYn = untSpltYn != null && untSpltYn;
        this.locSrt = locSrt != null ? locSrt : List.of();
        this.lastRvsnNo = 1L;
    }

    /** 수정 저장 — 단계 목록 통째 교체 + 리비전 증가 (단계 끄기 = 행 삭제) */
    public long applyDefinition(String stgyNm, String odrDvsn, Boolean untSpltYn,
                                List<SortCriterion> locSrt, List<PtawyStgyStg> newStages) {
        this.stgyNm = stgyNm;
        this.odrDvsn = odrDvsn;
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
