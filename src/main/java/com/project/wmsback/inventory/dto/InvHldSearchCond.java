package com.project.wmsback.inventory.dto;

import com.project.wmsback.inventory.entity.InvHldStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InvHldSearchCond {

    private String hldNo;
    private String prodCd;
    private String prodNm;
    private String locCd;
    private String lotNo;
    private String rsnCd;
    private InvHldStatus status;
}
