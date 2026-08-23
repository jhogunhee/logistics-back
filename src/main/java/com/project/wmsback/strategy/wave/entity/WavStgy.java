package com.project.wmsback.strategy.wave.entity;

import com.project.common.entity.BaseEntity;
import com.project.wmsback.strategy.core.condition.FieldCondition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.util.List;

/**
 * 웨이브 전략. 실행하면 전략마다 웨이브 1개를 만들고 조건에 맞는 미편성 주문을 편입한다.
 *
 * <b>전 전략을 prty 순으로 순회</b>하는 유형이라 우선순위 컬럼이 있다
 * — 주문은 먼저 실행된 전략이 선점한다(한 주문은 웨이브 1개).
 *
 * <p>조건그룹은 하위 테이블이 아니라 JSONB다 — 그룹에는 개별 파라미터도 활성 플래그도 없고
 * 항상 헤더와 통째로 저장되기 때문(편집 단위 = 저장 단위). 웨이브 도메인은 테이블 1개로 끝난다.
 */
@Entity
@Table(name = "wav_stgy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WavStgy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wav_stgy_id")
    private Long id;

    /** 전략명. 표시용 — 실행에 사용하지 않는다 */
    @Column(name = "stgy_nm", nullable = false, length = 100)
    private String stgyNm;

    /** 실행 순서. 낮을수록 먼저. 동률은 id 순으로 결정적이게 처리한다 */
    @Column(name = "prty", nullable = false)
    private Integer prty;

    /** 조건그룹 — 그룹끼리 OR, 그룹 안 AND. 0건 저장은 CHECK가, 빈 그룹은 저장 서비스가 거부한다 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cond_grp", nullable = false)
    private List<List<FieldCondition>> condGrp;

    @Column(name = "last_rvsn_no", nullable = false)
    private Long lastRvsnNo;

    @Builder
    private WavStgy(String stgyNm, Integer prty, List<List<FieldCondition>> condGrp) {
        this.stgyNm = stgyNm;
        this.prty = prty != null ? prty : 0;
        this.condGrp = condGrp;
        this.lastRvsnNo = 1L;
    }

    /** 수정 저장 — 정의 통째 교체 + 리비전 증가 */
    public long applyDefinition(String stgyNm, Integer prty, List<List<FieldCondition>> condGrp) {
        this.stgyNm = stgyNm;
        this.prty = prty != null ? prty : 0;
        this.condGrp = condGrp;
        this.lastRvsnNo++;
        return this.lastRvsnNo;
    }
}
