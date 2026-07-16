package com.project.wmsback.master.entity;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** CodeDetail 복합키 (group_cd + code_cd) */
@NoArgsConstructor
@EqualsAndHashCode
public class CodeDetailId implements Serializable {

    private String groupCd;
    private String codeCd;
}