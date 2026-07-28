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
 * 거래 종료된 벤더도 과거 주문이 참조하므로 삭제 대신 useYn='N'으로 막는다
 * (실제 삭제는 참조가 하나도 없을 때만 FK가 허용한다).
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
    @Column(name = "mgr_nm", length = 50)
    private String mgrNm;

    /** 연락처 */
    @Column(name = "tel_no", length = 30)
    private String telNo;

    /** 사용 여부. 'N'이면 신규 주문에서 선택 불가 (과거 주문은 그대로 유지) */
    @Column(name = "use_yn", nullable = false, length = 1)
    private String useYn;

    @Builder
    private Vendor(String vndrCd, String vndrNm, String mgrNm, String telNo, String useYn) {
        this.vndrCd = vndrCd;
        this.vndrNm = vndrNm;
        this.mgrNm = mgrNm;
        this.telNo = telNo;
        this.useYn = useYn != null ? useYn : "Y";
    }

    public void update(String vndrNm, String mgrNm, String telNo, String useYn) {
        this.vndrNm = vndrNm;
        this.mgrNm = mgrNm;
        this.telNo = telNo;
        this.useYn = useYn;
    }

    /** 주문에 담을 수 있는 벤더인지. 사용중지 벤더로 새 주문을 만드는 걸 막는다 */
    public boolean isUsable() {
        return "Y".equals(useYn);
    }
}
