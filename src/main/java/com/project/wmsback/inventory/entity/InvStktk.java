package com.project.wmsback.inventory.entity;

import com.project.common.entity.BaseEntity;
import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.warehouse.entity.Loc;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 재고조사(실사) 헤더. 조사 범위를 잡아 라인을 만들고(전산수량 스냅샷), 실사수량을 입력한 뒤
 * 확정 시점에 차이를 ADJUST로 보정한다.
 *
 * 상태는 워크플로 단계만 표현한다 — 「부분확정」을 두지 않는다. 확정 후 재정정은 조사를 되열지 않고
 * 새 조사를 만든다(append-only 원칙). 실적 테이블은 없다 — 실적의 실체는 inv_hist의 ADJUST 행이다
 * (rfn_doc_typ=INV_STKTK, rfn_doc_no=stktkNo).
 */
@Entity
@Table(name = "inv_stktk", uniqueConstraints = @UniqueConstraint(name = "uq_inv_stktk_no", columnNames = "stktk_no"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvStktk extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inv_stktk_id")
    private Long id;

    /** 재고조사 번호 (건당 유일 — 라인은 InvStktkLn). nbr_rule STKTK_NO 채번 */
    @Column(name = "stktk_no", nullable = false, length = 30)
    private String stktkNo;

    /** 조사 범위 — 존 코드 (null이면 조건 없음). 라인 생성에 쓴 조건을 보존해 「무엇을 조사했나」를 남긴다 */
    @Column(name = "zon_cd", length = 20)
    private String zonCd;

    /** 조사 범위 — 로케이션 (null이면 조건 없음) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loc_id")
    private Loc loc;

    /** 조사 범위 — 상품 (null이면 조건 없음) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id")
    private Prod prod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private InvStktkStatus status;

    /** 확정 시각 (CONFIRMED 전이 시점) */
    @Column(name = "cfm_dt")
    private LocalDateTime cfmDt;

    @OneToMany(mappedBy = "invStktk", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvStktkLn> lines = new ArrayList<>();

    @Builder
    private InvStktk(String stktkNo, String zonCd, Loc loc, Prod prod) {
        this.stktkNo = stktkNo;
        this.zonCd = zonCd;
        this.loc = loc;
        this.prod = prod;
        this.status = InvStktkStatus.CREATED;
    }

    public void addLine(InvStktkLn line) {
        lines.add(line);
        line.assignStktk(this);
    }

    /**
     * 라인 제거 — 컬렉션에서 빼면 orphanRemoval이 DELETE를 낸다.
     * ID로 지우는 이유: 밖에서 조회한 인스턴스를 remove(Object)로 넘기면 equals가 동일성 비교라
     * 컬렉션 안의 인스턴스와 다를 때 조용히 아무것도 지우지 않는다. 소속 검증도 여기서 겸한다.
     *
     * @return 지운 라인이 있으면 true (없으면 이 조사의 라인이 아니다)
     */
    public boolean removeLine(Long lnId) {
        return lines.removeIf(l -> l.getId().equals(lnId));
    }

    /** 라인 편집(실사수량 입력·라인 추가/삭제) 가능 상태 검증 — 작성 중인 조사만 */
    public void requireEditable() {
        if (status != InvStktkStatus.CREATED) {
            throw new IllegalStateException("작성 중인 조사만 수정할 수 있습니다 (" + status.getLabel() + "): " + stktkNo);
        }
    }

    /** 확정 전이. 재고 보정은 서비스가 라인별로 수행하고, 여기서는 상태만 넘긴다 */
    public void confirm() {
        requireEditable();
        this.status = InvStktkStatus.CONFIRMED;
        this.cfmDt = LocalDateTime.now();
    }

    /** 조사 취소 (확정 전 폐기). 라인은 보존한다 — 무엇을 세려다 접었는지가 남아야 한다 */
    public void cancel() {
        requireEditable();
        this.status = InvStktkStatus.CANCELLED;
    }
}
