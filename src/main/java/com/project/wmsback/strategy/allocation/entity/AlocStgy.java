package com.project.wmsback.strategy.allocation.entity;

import com.project.common.entity.BaseEntity;
import com.project.wmsback.strategy.core.condition.FieldCondition;
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
 * 할당 전략 헤더. 자동할당의 「어느 재고를, 어떤 순서로, 모자라면 누구에게」를 슬롯으로 정의한다.
 *
 * <p>다른 세 유형과 갈리는 성질이 하나 있다 — <b>할당에는 이미 코드에 박힌 기본 동작이 있다</b>
 * (FEFO + 점포 잔여수명 + 순차 소진). 그래서 전략은 없던 판단을 만드는 것이 아니라 그 기본값을
 * 슬롯 단위로 덮어쓰고, 필수 슬롯이 없으며, 전략이 0건이면 할당은 지금과 똑같이 동작한다.
 *
 * <p>선택은 <b>실행 1회당 1건</b>이다 — 처리 단위인 상품 그룹이 웨이브를 가로지르므로(여러
 * 웨이브의 같은 상품이 한 그룹), 웨이브마다 다른 전략을 고르면 한 후보 풀을 두 정의가 다른
 * 순서로 소진하게 되어 정렬도 배분도 성립하지 않는다. 그래서 웨이브처럼 전 전략을 순회하되
 * 웨이브와 달리 <b>첫 매칭에서 멈춘다.</b>
 */
@Entity
@Table(name = "aloc_stgy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlocStgy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "aloc_stgy_id")
    private Long id;

    /** 전략명. 표시용 — 실행에 사용하지 않는다 */
    @Column(name = "stgy_nm", nullable = false, length = 100)
    private String stgyNm;

    /** 선택 순서. 낮을수록 먼저 매칭 판정. 동률은 id 순으로 결정적이게 처리한다 */
    @Column(name = "prty", nullable = false)
    private Integer prty;

    /**
     * 적용대상 조건 (원소끼리 AND). 빈 배열 = 전체 매칭 폴백.
     * 웨이브의 조건그룹과 달리 <b>0건이 정상</b>이다 — 어느 전략도 매칭되지 않을 때
     * 무엇을 쓸지가 이걸로 표현되기 때문에 길이 CHECK를 걸지 않았다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tgt_cond", nullable = false)
    private List<FieldCondition> tgtCond;

    @Column(name = "last_rvsn_no", nullable = false)
    private Long lastRvsnNo;

    @OneToMany(mappedBy = "stgy", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("slotTyp, srtSeq")
    private List<AlocStgySlot> slots = new ArrayList<>();

    @Builder
    private AlocStgy(String stgyNm, Integer prty, List<FieldCondition> tgtCond) {
        this.stgyNm = stgyNm;
        this.prty = prty != null ? prty : 0;
        this.tgtCond = tgtCond != null ? tgtCond : List.of();
        this.lastRvsnNo = 1L;
    }

    /** 수정 저장 — 슬롯 목록 통째 교체 + 리비전 증가 (슬롯 끄기 = 행 삭제, D4) */
    public long applyDefinition(String stgyNm, Integer prty, List<FieldCondition> tgtCond,
                                List<AlocStgySlot> newSlots) {
        this.stgyNm = stgyNm;
        this.prty = prty != null ? prty : 0;
        this.tgtCond = tgtCond != null ? tgtCond : List.of();
        this.slots.clear();
        newSlots.forEach(this::addSlot);
        this.lastRvsnNo++;
        return this.lastRvsnNo;
    }

    public void addSlot(AlocStgySlot slot) {
        slots.add(slot);
        slot.assignStgy(this);
    }

    /** 슬롯 타입별 목록 (srt_seq 순). 없으면 빈 목록 = 그 역할은 기본 동작 */
    public List<AlocStgySlot> slotsOf(AlocSlotTyp slotTyp) {
        return slots.stream()
                .filter(slot -> slot.getSlotTyp() == slotTyp)
                .sorted(java.util.Comparator.comparing(AlocStgySlot::getSrtSeq))
                .toList();
    }
}
