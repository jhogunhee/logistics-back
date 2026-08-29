package com.project.mdm.mnu.entity;

import com.project.mdm.usr.entity.Role;

import java.io.Serializable;
import java.util.Objects;

/**
 * mnu_role 복합키. @IdClass용이라 기본 생성자와 equals/hashCode가 필요하고,
 * <b>필드 타입이 엔티티의 @Id와 정확히 같아야 한다</b> — role을 String으로 두면
 * Hibernate가 행을 읽어 키를 채우는 시점에 PropertyAccessException으로 터진다.
 */
public class MnuRoleId implements Serializable {

    private String mnuCd;
    private Role role;

    protected MnuRoleId() {
    }

    public MnuRoleId(String mnuCd, Role role) {
        this.mnuCd = mnuCd;
        this.role = role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MnuRoleId that)) return false;
        return Objects.equals(mnuCd, that.mnuCd) && Objects.equals(role, that.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mnuCd, role);
    }
}
