package com.project.mdm.nbr.entity;

import com.project.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채번 카운터. rule_cd+dync_ky별 현재 발급값.
 * 실제 신규 행은 애플리케이션이 이 엔티티를 직접 save()하지 않고, 네이티브
 * INSERT ... ON CONFLICT DO NOTHING 업서트로 만든다 (NbrSeqRepository.insertIfAbsent, Task 7).
 * 여기 @Builder는 테스트에서 인스턴스를 만들 때만 쓴다.
 */
@Entity
@Table(name = "nbr_seq")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(NbrSeqId.class)
public class NbrSeq extends BaseEntity {

    @Id
    @Column(name = "rule_cd", length = 30)
    private String ruleCd;

    @Id
    @Column(name = "dync_ky", length = 30)
    private String dyncKy;

    @Column(name = "seq", nullable = false)
    private Long seq;

    @Builder
    private NbrSeq(String ruleCd, String dyncKy, Long seq) {
        this.ruleCd = ruleCd;
        this.dyncKy = dyncKy;
        this.seq = seq;
    }

    public void increment() {
        this.seq += 1;
    }
}
