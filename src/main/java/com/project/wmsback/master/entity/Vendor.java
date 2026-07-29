package com.project.wmsback.master.entity;

import com.project.wmsback.common.entity.BaseEntity;
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
 * 벤더(납품처) 마스터. 입고주문·입고예정이 참조한다.
 *
 * 사용여부 컬럼을 두지 않는다 — 이 마스터는 물리삭제로 운용한다. 거래 종료 벤더를
 * 목록에서만 빼는 상태를 따로 두지 않고 실제로 지운다.
 */
@Entity
@Table(name = "vendor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vendor extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vendor_id")
    private Long id;

    /** 벤더 코드 (업무 식별자, 예: VD-0001). 서버가 시퀀스로 채번 */
    @Column(name = "vndr_cd", nullable = false, length = 30, unique = true)
    private String vndrCd;

    /** 벤더명 */
    @Column(name = "vndr_nm", nullable = false, length = 100)
    private String vndrNm;

    /** 담당자명 */
    @Column(name = "pic_nm", length = 50)
    private String picNm;

    /** 연락처 */
    @Column(name = "tel_no", length = 30)
    private String telNo;

    @Builder
    private Vendor(String vndrCd, String vndrNm, String picNm, String telNo) {
        this.vndrCd = vndrCd;
        this.vndrNm = vndrNm;
        this.picNm = picNm;
        this.telNo = telNo;
    }

    public void update(String vndrNm, String picNm, String telNo) {
        this.vndrNm = vndrNm;
        this.picNm = picNm;
        this.telNo = telNo;
    }
}
