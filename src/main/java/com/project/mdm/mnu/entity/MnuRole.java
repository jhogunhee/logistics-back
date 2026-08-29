package com.project.mdm.mnu.entity;

import com.project.common.entity.BaseEntity;
import com.project.mdm.usr.entity.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 역할별 메뉴 권한. 켜진 것만 행으로 있다.
 * <p>
 * {@code Usr}의 역할과 달리 {@code @ElementCollection}이 아니라 독립 엔티티다 —
 * 역할로 거꾸로 조회하고, 저장은 통째로 지웠다 다시 넣는다.
 * ADMR은 여기 담기지 않는다(항상 전 메뉴를 본다 — {@code ck_mnu_role_role}이 막는다).
 */
@Entity
@Table(name = "mnu_role")
@IdClass(MnuRoleId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MnuRole extends BaseEntity {

    @Id
    @Column(name = "mnu_cd", length = 30)
    private String mnuCd;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20)
    private Role role;

    public MnuRole(String mnuCd, Role role) {
        this.mnuCd = mnuCd;
        this.role = role;
    }
}
