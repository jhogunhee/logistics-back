package com.project.mdm.usr.dto;

import java.util.List;

/** 로그인·내정보·비밀번호 변경의 입출력. 엔티티에 붙지 않는 단순 전달값이라 record로 둔다. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(String loginId, String pwd) {
    }

    /**
     * PDA 간편 로그인 — 작업자 코드 바코드를 찍어 보낸다. 작업자 코드는 별도 컬럼이 아니라
     * 로그인 아이디 그대로다 (라벨 인쇄가 {@code login_id}를 그대로 바코드로 찍는다).
     */
    public record ScanLoginRequest(String loginId) {
    }

    /**
     * 인증 자체는 세션 쿠키가 나른다. 본문의 {@code csrfToken}은 이후 저장 요청에 헤더로 붙일
     * 값이다 — 프론트가 백엔드와 다른 도메인이라 쿠키 방식 CSRF 토큰을 읽지 못해 본문으로 준다.
     */
    public record LoginResponse(String loginId, String usrNm, List<String> roles, String csrfToken) {
    }

    public record MeResponse(String loginId, String usrNm, List<String> roles, String csrfToken) {
    }

    public record PwdChangeRequest(String curPwd, String newPwd) {
    }
}
