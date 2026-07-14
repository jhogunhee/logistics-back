package com.project.wmsback.master.entity;

import com.project.wmsback.common.entity.BaseTimeEntity;
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

/**
 * 점포(납품처) 마스터. 납품 허용 잔여수명 기준을 점포 단위로 관리.
 */
@Entity
@Table(name = "store")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "store_id")
    private Long id;

    /** 점포 코드 (업무 식별자, 예: ST-0001) */
    @Column(name = "store_cd", nullable = false, length = 30, unique = true)
    private String storeCd;

    /** 점포명 */
    @Column(name = "store_nm", nullable = false, length = 100)
    private String storeNm;

    /** 납품 허용 잔여수명 비율(%). 이 점포 출고 시 잔여 유통기한이 이 비율 미만인 Lot은 할당 제외 (FEFO 앞단 필터) */
    @Column(name = "outb_life_rate", nullable = false)
    private Integer outbLifeRate;

    @Builder
    private Store(String storeCd, String storeNm, Integer outbLifeRate) {
        this.storeCd = storeCd;
        this.storeNm = storeNm;
        this.outbLifeRate = outbLifeRate != null ? outbLifeRate : 40;
    }

    public void update(String storeNm, Integer outbLifeRate) {
        this.storeNm = storeNm;
        this.outbLifeRate = outbLifeRate;
    }
}
