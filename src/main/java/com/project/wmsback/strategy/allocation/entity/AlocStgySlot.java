package com.project.wmsback.strategy.allocation.entity;

import com.project.common.entity.BaseEntity;
import com.project.wmsback.strategy.core.condition.FieldCondition;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

/**
 * 할당 슬롯. 적치 단계와 같은 판단으로 JSONB가 아니라 <b>행</b>이다 —
 * 개별로 추가·삭제·순서변경(drag&amp;drop)하고 자기 파라미터·조건을 갖는 편집 단위이기 때문이다.
 * (웨이브 조건그룹이 JSONB인 것과 갈리는 지점.)
 */
@Entity
@Table(name = "aloc_stgy_slot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlocStgySlot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "aloc_stgy_slot_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aloc_stgy_id", nullable = false)
    private AlocStgy stgy;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot_typ", nullable = false, length = 15)
    private AlocSlotTyp slotTyp;

    /** 다중 슬롯 안의 순서. INVN_FLTR은 후보 계층 순서, DSTRB는 분배 실행 순서 */
    @Column(name = "srt_seq", nullable = false)
    private Integer srtSeq;

    /**
     * 구현체 code (enum name). <b>INVN_FLTR만 NULL</b>이다 — 그 슬롯은 「무엇을 실행할지」가
     * 아니라 「어느 후보만」을 정하므로 정의 전체가 cond이고 구현체 축이 없다.
     */
    @Column(name = "cmpnt_cd", length = 30)
    private String cmpntCd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "para", nullable = false)
    private Map<String, Object> para;

    /** INVN_FLTR은 계층 지정(존 업무유형 IN), DSTRB는 배분 대상 선별. 정렬·제약은 쓰지 않는다 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cond", nullable = false)
    private List<FieldCondition> cond;

    @Builder
    private AlocStgySlot(AlocSlotTyp slotTyp, Integer srtSeq, String cmpntCd,
                         Map<String, Object> para, List<FieldCondition> cond) {
        this.slotTyp = slotTyp;
        this.srtSeq = srtSeq != null ? srtSeq : 0;
        this.cmpntCd = cmpntCd;
        this.para = para != null ? para : Map.of();
        this.cond = cond != null ? cond : List.of();
    }

    void assignStgy(AlocStgy stgy) {
        this.stgy = stgy;
    }
}
