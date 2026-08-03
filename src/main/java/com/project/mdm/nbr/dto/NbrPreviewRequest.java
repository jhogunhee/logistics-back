package com.project.mdm.nbr.dto;

import com.project.mdm.nbr.entity.DyncKyTyp;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NbrPreviewRequest {

    private String prfx;
    private String prfxDlmt;
    private String deDlmt;
    private Integer seqDgt;
    private DyncKyTyp dyncKyTyp;
}
