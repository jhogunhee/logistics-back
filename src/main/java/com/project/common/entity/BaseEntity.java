package com.project.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

/**
 * 감사 컬럼 4종(created_at/by, updated_at/by) 공통화. 모든 엔티티가 상속한다.
 * 작성자 값은 JpaConfig의 AuditorAware가 채운다 (로그인 아이디, 스케줄러 실행은 'system').
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity extends BaseTimeEntity {

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 30)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 30)
    private String updatedBy;
}
