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
 * 존 마스터. 로케이션의 상위 그룹 (온도 · 보관형태 · 담당업무 단위).
 * {@code Loc.zonCd}가 {@code zonCd}를 문자열로 참조한다 (FK 없음 — 존재 검증은 LocService).
 * <p>
 * 컬럼·필드명은 {@code docs/naming-dictionary.md} 사전을 따르고, 온도구분의 타입만
 * 기존 {@link TempZone}을 재사용한다 (상품·로케이션과 값 도메인을 공유해야 하므로 복제하지 않는다).
 */
@Entity
@Table(name = "zon")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Zon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "zon_id")
    private Long id;

    /** 존 코드 (예: DRY, RCV-STAGE). 하위 로케이션이 문자열로 참조하므로 등록 후 변경하지 않는다 */
    @Column(name = "zon_cd", nullable = false, length = 20, unique = true)
    private String zonCd;

    /** 존 명 (화면 표시용) */
    @Column(name = "zon_nm", nullable = false, length = 100)
    private String zonNm;

    /** 온도구분. 보관 로케이션은 이 값과 loc.tmp_zon이 일치해야 한다 */
    @Enumerated(EnumType.STRING)
    @Column(name = "tmp_zon", nullable = false, length = 10)
    private TempZone tmpZon;

    /** 보관유형 (랙 / 평치 / 가상) */
    @Enumerated(EnumType.STRING)
    @Column(name = "strg_typ", nullable = false, length = 10)
    private StrgTyp strgTyp;

    /** 업무구분 (입고작업 / 출고작업 / 보관 / 피킹 / 반품 / 작업) */
    @Enumerated(EnumType.STRING)
    @Column(name = "biz_dvsn", nullable = false, length = 10)
    private BizDvsn bizDvsn;

    @Builder
    private Zon(String zonCd, String zonNm, TempZone tmpZon, StrgTyp strgTyp, BizDvsn bizDvsn) {
        this.zonCd = zonCd;
        this.zonNm = zonNm;
        this.tmpZon = tmpZon;
        this.strgTyp = strgTyp;
        this.bizDvsn = bizDvsn;
    }

    /** 존 코드는 하위 로케이션이 참조하는 업무 식별자라 수정 대상에서 제외한다 */
    public void update(String zonNm, TempZone tmpZon, StrgTyp strgTyp, BizDvsn bizDvsn) {
        this.zonNm = zonNm;
        this.tmpZon = tmpZon;
        this.strgTyp = strgTyp;
        this.bizDvsn = bizDvsn;
    }
}
