package com.project.mdm.mnu.dto;

import com.project.common.security.SecurityRules;
import com.project.mdm.mnu.entity.Mnu;
import com.project.mdm.usr.entity.Role;

import java.util.Arrays;
import java.util.List;

/**
 * 권한별 메뉴 관리 화면의 한 행. 역할이 열이고 체크박스가 값이다.
 * <p>
 * {@code readOnlyRoles}는 이 화면을 열어줘도 <b>저장까지는 못 하는</b> 역할이다 — 코드의 역할
 * 상한({@link SecurityRules})이 그 업무 구역의 쓰기를 이미 막고 있는 경우다. 막지 않고 알려만
 * 준다: 조회 목적으로 메뉴를 열어두는 것이 정상적인 설정이라서다.
 * <p>
 * <b>지금 켜진 역할이 아니라 전 역할을 대상으로 계산한다.</b> 켜진 것만 담으면 화면에서 방금
 * 켠 칸에는 표시가 안 붙어, 같은 상태인데 저장 전후로 표시가 달라진다.
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
        // 조회 전용 화면(api_prfx 없음)은 저장할 것이 없어 표시할 것도 없다.
        // ADMR은 격자에 열이 없다 — 항상 전 메뉴를 보고 상한도 통과한다
        List<String> readOnly = mnu.getApiPrfx() == null ? List.of()
                : Arrays.stream(Role.values())
                        .filter(role -> role != Role.ADMR)
                        .map(Enum::name)
                        .filter(role -> !SecurityRules.canWrite(role, mnu.getApiPrfx()))
                        .toList();
        return new MnuRoleGridResponse(mnu.getMnuCd(), mnu.getMnuNm(), mnu.getGrpNm(),
                mnu.getSrtSeq(), names, readOnly);
    }
}
