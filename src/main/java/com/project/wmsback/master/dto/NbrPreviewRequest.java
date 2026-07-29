package com.project.wmsback.master.dto;

import com.project.wmsback.master.entity.DyncKyTyp;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NbrPreviewRequest {

    private String ptrn;
    private DyncKyTyp dyncKyTyp;
}
