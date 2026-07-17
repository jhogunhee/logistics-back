package com.project.wmsback.master.entity;

import com.project.wmsback.common.entity.BaseEntity;
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
    @Column(name = "zone_cd", nullable = false, length = 20)
    private String zoneCd;

    /** 존 온도대. SKU 온도대와 불일치하면 적치·이동 차단 */
    @Enumerated(EnumType.STRING)
    @Column(name = "temp_zone", nullable = false, length = 10)
    private TempZone tempZone;

    /** STAGE: 입고 스테이징(적치 대기) / STORAGE: 보관(할당 대상) */
    @Enumerated(EnumType.STRING)
    @Column(name = "loc_type", nullable = false, length = 10)
    private LocType locType;

    /** 할당 시 동일 유통기한(FEFO 동순위) 간 로케이션 우선순위. 낮을수록 먼저 할당 */
    @Column(name = "pick_prty", nullable = false)
    private Integer pickPrty;

    @Builder
    private Loc(String locCd, String zoneCd, TempZone tempZone, LocType locType, Integer pickPrty) {
        this.locCd = locCd;
        this.zoneCd = zoneCd;
        this.tempZone = tempZone;
        this.locType = locType;
        this.pickPrty = pickPrty != null ? pickPrty : 0;
    }

    public void update(String zoneCd, TempZone tempZone, LocType locType, Integer pickPrty) {
        this.zoneCd = zoneCd;
        this.tempZone = tempZone;
        this.locType = locType;
        this.pickPrty = pickPrty;
    }
}
