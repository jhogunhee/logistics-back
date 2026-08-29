package com.project.mdm.mnu.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 권한 격자 한 행의 저장 요청. 켜진 역할 이름만 담는다.
 * 저장은 그 구분(WEB/PDA)의 매핑을 통째로 교체하므로, 안 보낸 메뉴는 권한이 없어진다.
 */
@Getter
@Setter
@NoArgsConstructor
public class MnuRoleSaveRequest {

    private String mnuCd;
    private List<String> roles = List.of();
}
