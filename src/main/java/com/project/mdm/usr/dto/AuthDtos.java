package com.project.mdm.usr.dto;

import java.util.List;

/** 로그인·내정보·비밀번호 변경의 입출력. 엔티티에 붙지 않는 단순 전달값이라 record로 둔다. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(String loginId, String pwd) {
    }

    public record LoginResponse(String token, String loginId, String usrNm, List<String> roles) {
    }

    public record MeResponse(String loginId, String usrNm, List<String> roles) {
    }

    public record PwdChangeRequest(String curPwd, String newPwd) {
    }
}
