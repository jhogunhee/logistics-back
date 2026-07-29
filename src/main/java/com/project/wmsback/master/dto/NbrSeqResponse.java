package com.project.wmsback.master.dto;

import com.project.wmsback.master.entity.NbrSeq;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class NbrSeqResponse {

    private final String ruleCd;
    private final String dyncKy;
    private final Long seq;
    private final LocalDateTime updatedAt;

    private NbrSeqResponse(NbrSeq row) {
        this.ruleCd = row.getRuleCd();
        this.dyncKy = row.getDyncKy();
        this.seq = row.getSeq();
        this.updatedAt = row.getUpdatedAt();
    }

    public static NbrSeqResponse from(NbrSeq row) {
        return new NbrSeqResponse(row);
    }
}
