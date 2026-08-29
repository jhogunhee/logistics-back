package com.project.mdm.mnu.entity;

import com.project.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 메뉴 카탈로그. 사이드바(WEB)와 PDA 홈이 이 테이블을 읽는다.
 * <p>
 * PK가 생성값이 아니라 사용자가 넣는 코드라는 점만 다르고 나머지는 다른 마스터와 같다.
 * 아이콘과 라우트는 프론트 코드에 실체가 있고 여기엔 이름표·경로 문자열만 담긴다.
 */
@Entity
@Table(name = "mnu")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Mnu extends BaseEntity {

    @Id
    @Column(name = "mnu_cd", length = 30)
    private String mnuCd;

    @Column(name = "mnu_nm", nullable = false, length = 50)
    private String mnuNm;

    @Enumerated(EnumType.STRING)
    @Column(name = "dvsn", nullable = false, length = 10)
    private MnuDvsn dvsn;

    @Column(name = "grp_nm", nullable = false, length = 30)
    private String grpNm;

    @Column(name = "srt_seq", nullable = false)
    private int srtSeq;

    @Column(name = "icon_nm", nullable = false, length = 30)
    private String iconNm;

    @Column(name = "scrn_pth", nullable = false, length = 60)
    private String scrnPth;

    /**
     * 이 화면의 쓰기 API 이름공간. 조회 전용 화면이면 null이다.
     * 같은 값을 여러 화면이 가질 수 있다 — 같은 API를 나눠 쓰는 화면들이라
     * 요청만 봐서는 구분할 수 없고, 그 경우 하나라도 켜져 있으면 통과다.
     */
    @Column(name = "api_prfx", length = 50)
    private String apiPrfx;

    @Column(name = "kywd", length = 200)
    private String kywd;

    @Builder
    private Mnu(String mnuCd, String mnuNm, MnuDvsn dvsn, String grpNm, int srtSeq,
                String iconNm, String scrnPth, String apiPrfx, String kywd) {
        this.mnuCd = mnuCd;
        this.mnuNm = mnuNm;
        this.dvsn = dvsn;
        this.grpNm = grpNm;
        this.srtSeq = srtSeq;
        this.iconNm = iconNm;
        this.scrnPth = scrnPth;
        this.apiPrfx = apiPrfx;
        this.kywd = kywd;
    }

    /** 메뉴 코드는 권한 행이 참조하는 식별자라 수정 대상에서 제외한다 */
    public void update(String mnuNm, MnuDvsn dvsn, String grpNm, int srtSeq,
                       String iconNm, String scrnPth, String apiPrfx, String kywd) {
        this.mnuNm = mnuNm;
        this.dvsn = dvsn;
        this.grpNm = grpNm;
        this.srtSeq = srtSeq;
        this.iconNm = iconNm;
        this.scrnPth = scrnPth;
        this.apiPrfx = apiPrfx;
        this.kywd = kywd;
    }
}
