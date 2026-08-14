package com.project.mdm.nbr.repository;

import com.project.mdm.nbr.entity.NbrSeq;

import java.util.Optional;

public interface NbrSeqRepositoryCustom {

    Optional<NbrSeq> findByIdForUpdate(String ruleCd, String dyncKy);
}
