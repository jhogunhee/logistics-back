package com.project.mdm.nbr.dto;

import lombok.Getter;

@Getter
public class NbrPreviewResponse {

    private final String number;

    public NbrPreviewResponse(String number) {
        this.number = number;
    }
}
