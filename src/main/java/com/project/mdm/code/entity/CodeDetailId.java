package com.project.mdm.code.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** CodeDetail 복합키 (grp_cd + code_cd) */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CodeDetailId implements Serializable {

    private String grpCd;
    private String codeCd;
}