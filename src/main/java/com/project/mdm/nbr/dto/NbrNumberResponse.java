package com.project.mdm.nbr.dto;

import lombok.Getter;

/** 발급·미리보기 공용 번호 응답 — 둘 다 조립된 번호 문자열 하나를 돌려준다 */
@Getter
public class NbrNumberResponse {

    private final String number;

    public NbrNumberResponse(String number) {
        this.number = number;
    }
}
