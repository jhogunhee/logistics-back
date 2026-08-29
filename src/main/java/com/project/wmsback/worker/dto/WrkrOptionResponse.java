package com.project.wmsback.worker.dto;

/** 작업자 필터의 선택지 한 줄. 계정이 지워졌으면 usrNm이 null이다 */
public record WrkrOptionResponse(String loginId, String usrNm) {
}
