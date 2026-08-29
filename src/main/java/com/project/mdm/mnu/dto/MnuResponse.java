package com.project.mdm.mnu.dto;

import com.project.mdm.mnu.entity.Mnu;
import com.project.mdm.mnu.entity.MnuDvsn;

/** 메뉴 관리 그리드 한 행 */
public record MnuResponse(
        String mnuCd,
        String mnuNm,
        MnuDvsn dvsn,
        String grpNm,
        int srtSeq,
        String iconNm,
        String scrnPth,
        String apiPrfx,
        String kywd) {

    public static MnuResponse from(Mnu mnu) {
        return new MnuResponse(mnu.getMnuCd(), mnu.getMnuNm(), mnu.getDvsn(), mnu.getGrpNm(),
                mnu.getSrtSeq(), mnu.getIconNm(), mnu.getScrnPth(), mnu.getApiPrfx(), mnu.getKywd());
    }
}
