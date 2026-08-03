package com.project.mdm.nbr.dto;

import lombok.Getter;

@Getter
public class NbrIssueResponse {

    private final String number;

    public NbrIssueResponse(String number) {
        this.number = number;
    }
}
