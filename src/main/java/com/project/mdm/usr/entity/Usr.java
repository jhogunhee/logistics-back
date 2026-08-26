package com.project.mdm.usr.entity;

import com.project.common.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 사용자 마스터. 로그인 계정이자 감사 컬럼(created_by)의 출처다.
 *
 * <p>벤더·점포와 같이 물리삭제로 운용한다 — 퇴사자는 지운다. 이력의 {@code created_by}에는
 * 아이디 문자열이 남아 있으므로 계정을 지워도 「누가 했는지」는 깨지지 않는다.
 */
@Entity
@Table(name = "usr")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usr extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usr_id")
    private Long id;

    /** 로그인 아이디. 사람이 정한다(채번 대상이 아니다) */
    @Column(name = "login_id", nullable = false, length = 30, unique = true)
    private String loginId;

    /** 사용자명 */
    @Column(name = "usr_nm", nullable = false, length = 50)
    private String usrNm;

    /** BCrypt 해시 */
    @Column(name = "pwd", nullable = false, length = 100)
    private String pwd;

    /**
     * 역할 다건. 자식 엔티티가 아니라 값의 집합이라 {@code @OneToMany}를 쓰지 않는다
     * (이 코드베이스의 첫 {@code @ElementCollection}이다).
     *
     * <p>EAGER인 이유: 로그인이 곧바로 역할을 토큰 claim에 넣어야 하는데
     * {@code open-in-view=false}라 LAZY면 세션 밖에서 터진다. 사용자 수는 수십 명이다.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usr_role", joinColumns = @JoinColumn(name = "usr_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Set<Role> roles = new LinkedHashSet<>();

    @Builder
    private Usr(String loginId, String usrNm, String pwd, Set<Role> roles) {
        this.loginId = loginId;
        this.usrNm = usrNm;
        this.pwd = pwd;
        this.roles = new LinkedHashSet<>(roles);
    }

    /** 아이디는 바꾸지 않는다 — 이력의 created_by가 그 값으로 남아 있어 바꾸면 연결이 끊긴다 */
    public void update(String usrNm, Set<Role> roles) {
        this.usrNm = usrNm;
        this.roles.clear();
        this.roles.addAll(roles);
    }

    public void changePwd(String encodedPwd) {
        this.pwd = encodedPwd;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
