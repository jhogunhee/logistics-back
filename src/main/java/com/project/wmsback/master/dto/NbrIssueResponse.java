package com.project.wmsback.master.dto;

import lombok.Getter;

@Getter
public class NbrIssueResponse {

    private final String number;

    public NbrIssueResponse(String number) {
        this.number = number;
    }
}
