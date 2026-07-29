package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.NbrSeq;
import com.project.wmsback.master.entity.NbrSeqId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NbrSeqRepository extends JpaRepository<NbrSeq, NbrSeqId>, NbrSeqRepositoryCustom {

    /** 규칙 관리 화면의 "현재 카운터 조회" 읽기전용 목록 */
    List<NbrSeq> findByRuleCdOrderByDyncKy(String ruleCd);

    /**
     * 최초 발급 시 카운터 행이 없으면 만든다. ON CONFLICT DO NOTHING을 쓰는 이유:
     * 동시에 첫 발급이 몰려 PK 충돌이 나면 PostgreSQL은 그 순간 트랜잭션을 abort 상태로
     * 만들어(25P02) 같은 트랜잭션의 후속 조회까지 실패한다 — DataIntegrityViolationException을
     * catch하는 방식은 여기서 쓸 수 없다. ON CONFLICT DO NOTHING은 예외 자체를 던지지 않는다.
     * seq/created_at/created_by는 DB 기본값(0 / CURRENT_TIMESTAMP / 'admin')에 맡긴다.
     */
    @Modifying
    @Query(value = "INSERT INTO nbr_seq (rule_cd, dync_ky) VALUES (:ruleCd, :dyncKy) "
            + "ON CONFLICT (rule_cd, dync_ky) DO NOTHING", nativeQuery = true)
    void insertIfAbsent(@Param("ruleCd") String ruleCd, @Param("dyncKy") String dyncKy);
}
