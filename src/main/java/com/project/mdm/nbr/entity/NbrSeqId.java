package com.project.mdm.nbr.entity;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** NbrSeq 복합키 (rule_cd + dync_ky) */
@NoArgsConstructor
@EqualsAndHashCode
public class NbrSeqId implements Serializable {

    private String ruleCd;
    private String dyncKy;
}
