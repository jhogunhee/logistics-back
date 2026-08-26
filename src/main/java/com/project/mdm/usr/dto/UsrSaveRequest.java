package com.project.mdm.usr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.mdm.usr.entity.Role;
import com.project.mdm.usr.entity.Usr;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * <p>
 * 자기 필드만으로 판정할 수 있는 검사와 엔티티 생성·반영은 여기서 한다({@link #toEntity} · {@link #updateEntity}).
 * DB를 봐야 하는 일(아이디 중복 · 자기 자신 삭제 · 마지막 관리자)은 서비스 몫이다.
 */
@Getter
@Setter
@NoArgsConstructor
public class UsrSaveRequest {

    /** 그리드 행 상태 (C/U/D). JSON 필드명은 프론트 그리드 관례대로 _status */
    @JsonProperty("_status")
    private String status;

    private Long usrId;
    private String loginId;
    private String usrNm;
    /** 신규는 필수. 수정은 비어 있으면 기존 비밀번호를 유지한다 */
    private String pwd;
    private List<String> roles;

    public Usr toEntity(PasswordEncoder passwordEncoder) {
        validateFields();
        if (loginId == null || loginId.isBlank()) {
            throw new IllegalArgumentException("아이디는 필수입니다.");
        }
        if (pwd == null || pwd.isBlank()) {
            throw new IllegalArgumentException("신규 사용자는 비밀번호가 필수입니다.");
        }
        Usr.validateRawPwd(pwd);
        return Usr.builder()
                .loginId(loginId.trim())
                .usrNm(usrNm)
                .pwd(passwordEncoder.encode(pwd))
                .roles(toRoles())
                .build();
    }

    public void updateEntity(Usr usr, PasswordEncoder passwordEncoder) {
        validateFields();
        usr.update(usrNm, toRoles());
        // 빈 값은 「바꾸지 않는다」는 뜻이라 통과시킨다 — 값이 들어온 경우에만 규칙을 본다
        if (pwd != null && !pwd.isBlank()) {
            Usr.validateRawPwd(pwd);
            usr.changePwd(passwordEncoder.encode(pwd));
        }
    }

    /** 수정·삭제 판정에 서비스도 새 역할을 봐야 해서 공개한다 */
    public Set<Role> toRoles() {
        Set<Role> parsed = new LinkedHashSet<>();
        for (String role : roles) {
            try {
                parsed.add(Role.valueOf(role));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("알 수 없는 역할입니다: " + role);
            }
        }
        return parsed;
    }

    private void validateFields() {
        if (usrNm == null || usrNm.isBlank()) {
            throw new IllegalArgumentException("사용자명은 필수입니다.");
        }
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("역할은 하나 이상이어야 합니다.");
        }
    }
}
