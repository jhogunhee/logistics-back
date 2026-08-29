package com.project.mdm.mnu.dto;

import java.util.List;

/**
 * 메뉴 관리 화면의 목록 응답.
 * <p>
 * {@code uncoveredEndpoints}는 어느 메뉴의 API 접두에도 안 걸리는 저장 API다 — 그 경로는
 * 메뉴 권한이 관여하지 못하므로(상한만 남는다) 화면이 배너로 알린다. 목록과 같이 실어야
 * 「메뉴를 고치면 배너가 준다」가 한 화면 안에서 보인다.
 */
public record MnuListResponse(List<MnuResponse> menus, List<String> uncoveredEndpoints) {
}
