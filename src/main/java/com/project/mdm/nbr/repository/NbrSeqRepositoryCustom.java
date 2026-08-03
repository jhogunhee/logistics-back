package com.project.mdm.nbr.repository;

import com.project.mdm.nbr.entity.NbrSeq;
import com.querydsl.jpa.impl.JPAQueryFactory;

import java.util.Optional;

public interface NbrSeqRepositoryCustom {

    Optional<NbrSeq> findByIdForUpdate(String ruleCd, String dyncKy);
}
