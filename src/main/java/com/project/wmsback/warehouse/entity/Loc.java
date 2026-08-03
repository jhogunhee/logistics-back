package com.project.wmsback.warehouse.entity;

import com.project.mdm.prod.entity.TempZone;
import com.project.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로케이션 마스터. 재고가 놓이는 물리 위치 (스테이징/보관존).
 */
@Entity
@Table(name = "loc")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Loc extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "loc_id")
    private Long id;

    /** 로케이션 코드 (예: DRY-A-01-01, RCV-STAGE) */
    @Column(name = "loc_cd", nullable = false, length = 30, unique = true)
    private String locCd;

    /** 존 코드 (RCV-STAGE / DRY / CHL / FRZ) */
    @Column(name = "zon_cd", nullable = false, length = 20)
    private String zonCd;

    /** 존 온도대. 상품 온도대와 불일치하면 적치·이동 차단 */
    @Enumerated(EnumType.STRING)
    @Column(name = "tmp_zon", nullable = false, length = 10)
    private TempZone tmpZon;

    /** STAGE: 입고 스테이징(적치 대기) / STORAGE: 보관(할당 대상) */
    @Enumerated(EnumType.STRING)
    @Column(name = "loc_typ", nullable = false, length = 10)
    private LocType locTyp;

    /** 할당 시 동일 유통기한(FEFO 동순위) 간 로케이션 우선순위. 낮을수록 먼저 할당 */
    @Column(name = "pikng_prty", nullable = false)
    private Integer pikngPrty;

    /** 적치 우선순위. 적치 전략의 후보 정렬 기준(PTAWY_PRTY) — 낮을수록 먼저 배정 */
    @Column(name = "ptawy_prty", nullable = false)
    private Integer ptawyPrty;

    /**
     * 최대 적재 수량 (schema: STORAGE는 NOT NULL 강제 — ck_loc_storage_capacity).
     * 읽기 전용 매핑이다 — 기존 저장 경로(LocService)가 이 컬럼을 다루지 않아 왔으므로
     * insert/update 동작을 바꾸지 않는다. 적치 전략의 적재가능수량 계산에만 쓴다.
     * 저장 화면에서 관리하게 되면 그때 쓰기 매핑으로 전환한다.
     */
    @Column(name = "max_qty", insertable = false, updatable = false)
    private Long maxQty;

    @Builder
    private Loc(String locCd, String zonCd, TempZone tmpZon, LocType locTyp, Integer pikngPrty, Integer ptawyPrty) {
        this.locCd = locCd;
        this.zonCd = zonCd;
        this.tmpZon = tmpZon;
        this.locTyp = locTyp;
        this.pikngPrty = pikngPrty != null ? pikngPrty : 0;
        this.ptawyPrty = ptawyPrty != null ? ptawyPrty : 0;
    }

    public void update(String zonCd, TempZone tmpZon, LocType locTyp, Integer pikngPrty, Integer ptawyPrty) {
        this.zonCd = zonCd;
        this.tmpZon = tmpZon;
        this.locTyp = locTyp;
        this.pikngPrty = pikngPrty;
        this.ptawyPrty = ptawyPrty;
    }
}
