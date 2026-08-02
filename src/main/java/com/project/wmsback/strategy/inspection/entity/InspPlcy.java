package com.project.wmsback.strategy.inspection.entity;

import com.project.wmsback.common.entity.BaseEntity;
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

import java.util.ArrayList;
import java.util.List;

/**
 * 검수 정책 헤더 (전역 1행 — 강제는 InspPlcyService, D8). 검수 저장 직전 규칙 전부를
 * AND 평가하고 위반 시 저장 전체를 거부한다. prty가 없다 — 검수는 "선택되는" 전략이 아니라
 * 전량 실행 유형이라, 선택 순서 컬럼이 있으면 화면·스키마가 거짓말을 하게 된다.
 */
@Entity
@Table(name = "insp_plcy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspPlcy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "insp_plcy_id")
    private Long id;

    /** 정책명. 표시용 — 실행에 사용하지 않는다 (이름/동작 불일치는 미리보기가 보완) */
    @Column(name = "stgy_nm", nullable = false, length = 100)
    private String stgyNm;

    /** 마지막 저장 리비전 (stgy_rvsn.rvsn_no 최신값) */
    @Column(name = "last_rvsn_no", nullable = false)
    private Long lastRvsnNo;

    @OneToMany(mappedBy = "plcy", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("srtSeq")
    private List<InspPlcyRule> rules = new ArrayList<>();

    @Builder
    private InspPlcy(String stgyNm) {
        this.stgyNm = stgyNm;
        this.lastRvsnNo = 1L;
    }

    /** 수정 저장 — 규칙 목록 통째 교체 + 리비전 증가. orphanRemoval이 빠진 행을 지운다 (D4: 규칙 끄기 = 행 삭제) */
    public long applyDefinition(String stgyNm, List<InspPlcyRule> newRules) {
        this.stgyNm = stgyNm;
        this.rules.clear();
        newRules.forEach(this::addRule);
        this.lastRvsnNo++;
        return this.lastRvsnNo;
    }

    public void addRule(InspPlcyRule rule) {
        rules.add(rule);
        rule.assignPlcy(this);
    }
}
