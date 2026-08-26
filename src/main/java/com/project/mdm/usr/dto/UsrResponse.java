package com.project.mdm.usr.dto;

import com.project.mdm.usr.entity.Role;
import com.project.mdm.usr.entity.Usr;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** 비밀번호 해시는 담지 않는다. */
@Getter
public class UsrResponse {

    private final Long usrId;
    private final String loginId;
    private final String usrNm;
    private final List<String> roles;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final String updatedBy;
    private final LocalDateTime updatedAt;

    private UsrResponse(Usr usr) {
        this.usrId = usr.getId();
        this.loginId = usr.getLoginId();
        this.usrNm = usr.getUsrNm();
        this.roles = usr.getRoles().stream().map(Role::name).toList();
        this.createdBy = usr.getCreatedBy();
        this.createdAt = usr.getCreatedAt();
        this.updatedBy = usr.getUpdatedBy();
        this.updatedAt = usr.getUpdatedAt();
    }

    public static UsrResponse from(Usr usr) {
        return new UsrResponse(usr);
    }
}
