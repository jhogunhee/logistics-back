package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.NbrSeq;
import com.querydsl.jpa.impl.JPAQueryFactory;

import java.util.Optional;

public interface NbrSeqRepositoryCustom {

    Optional<NbrSeq> findByIdForUpdate(String ruleCd, String dyncKy);
}
