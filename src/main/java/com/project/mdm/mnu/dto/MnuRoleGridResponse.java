package com.project.mdm.mnu.dto;

import com.project.common.security.SecurityRules;
import com.project.mdm.mnu.entity.Mnu;
import com.project.mdm.usr.entity.Role;

import java.util.List;

/**
 * 권한별 메뉴 관리 화면의 한 행. 역할이 열이고 체크박스가 값이다.
 * <p>
 * {@code readOnlyRoles}는 <b>켜져 있지만 저장은 못 하는</b> 역할이다 — 코드의 역할 상한
 * ({@link SecurityRules})이 그 업무 구역의 쓰기를 이미 막고 있는 경우다. 막지 않고 알려만 준다:
 * 조회 목적으로 메뉴를 열어두는 것이 정상적인 설정이라서다.
 */
public record MnuRoleGridResponse(
        String mnuCd,
        String mnuNm,
        String grpNm,
        int srtSeq,
        List<String> roles,
        List<String> readOnlyRoles) {

    public static MnuRoleGridResponse of(Mnu mnu, List<Role> roles) {
        List<String> names = roles.stream().map(Enum::name).toList();
        List<String> readOnly = mnu.getApiPrfx() == null ? List.of()
                : names.stream()
                        .filter(r -> !SecurityRules.canWrite(r, mnu.getApiPrfx()))
                        .toList();
        return new MnuRoleGridResponse(mnu.getMnuCd(), mnu.getMnuNm(), mnu.getGrpNm(),
                mnu.getSrtSeq(), names, readOnly);
    }
}
