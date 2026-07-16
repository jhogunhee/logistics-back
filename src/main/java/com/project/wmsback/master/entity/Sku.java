package com.project.wmsback.master.entity;

import com.project.wmsback.common.entity.BaseTimeEntity;
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
 * 상품 마스터. 보관/출고 규칙(온도대, 납품 허용 잔여수명)을 상품 단위로 정의.
 */
@Entity
@Table(name = "sku")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sku extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sku_id")
    private Long id;

    /** 상품 코드 (업무 식별자, 예: SKU-0001) */
    @Column(name = "sku_cd", nullable = false, length = 30, unique = true)
    private String skuCd;

    /** 상품명 */
    @Column(name = "sku_nm", nullable = false, length = 100)
    private String skuNm;

    /** 보관 온도대. 적치·이동 시 로케이션 온도대와 일치 검증 */
    @Enumerated(EnumType.STRING)
    @Column(name = "temp_zone", nullable = false, length = 10)
    private TempZone tempZone;

    /** 제조일 기준 총 유통기한(일). NULL = 유통기한 미관리(공산품 등). 시더가 Lot 유통기한 생성 시 사용 */
    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Builder
    private Sku(String skuCd, String skuNm, TempZone tempZone, Integer shelfLifeDays) {
        this.skuCd = skuCd;
        this.skuNm = skuNm;
        this.tempZone = tempZone;
        this.shelfLifeDays = shelfLifeDays;
    }

    public void update(String skuNm, TempZone tempZone, Integer shelfLifeDays) {
        this.skuNm = skuNm;
        this.tempZone = tempZone;
        this.shelfLifeDays = shelfLifeDays;
    }
}
