package com.project.mdm.nbr.repository;

import com.project.mdm.nbr.entity.NbrSeq;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

import static com.project.mdm.nbr.entity.QNbrSeq.nbrSeq;

@RequiredArgsConstructor
public class NbrSeqRepositoryImpl implements NbrSeqRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<NbrSeq> findByIdForUpdate(String ruleCd, String dyncKy) {
        return Optional.ofNullable(
                queryFactory
                        .selectFrom(nbrSeq)
                        .where(nbrSeq.ruleCd.eq(ruleCd), nbrSeq.dyncKy.eq(dyncKy))
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                        .fetchOne());
    }
}
