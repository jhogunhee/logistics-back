package com.project.mdm.usr.service;

import com.project.common.security.AuthUser;
import com.project.mdm.usr.dto.UsrResponse;
import com.project.mdm.usr.dto.UsrSaveRequest;
import com.project.mdm.usr.dto.UsrSearchCond;
import com.project.mdm.usr.entity.Role;
import com.project.mdm.usr.entity.Usr;
import com.project.mdm.usr.repository.UsrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsrService {

    private final UsrRepository usrRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UsrResponse> list(UsrSearchCond cond) {
        return usrRepository.search(cond).stream()
                .map(UsrResponse::from)
                .toList();
    }

    /** 신규(C)/수정(U)/삭제(D) 행 일괄 저장. 한 건이라도 실패하면 전체 롤백. */
    @Transactional
    public void saveAll(List<UsrSaveRequest> rows) {
        for (UsrSaveRequest row : rows) {
            switch (row.getStatus()) {
                case "C" -> create(row);
                case "U" -> update(row);
                case "D" -> delete(row);
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        // 제약 위반(아이디 중복 등)을 커밋 시점이 아니라 여기서 터뜨려 예외 변환이 되게 한다
        usrRepository.flush();
    }

    private void create(UsrSaveRequest row) {
        // uq_usr_login_id가 있어도 먼저 본다 — DB 제약에 걸린 메시지는 사람이 읽을 것이 못 된다
        if (row.getLoginId() != null && usrRepository.existsByLoginId(row.getLoginId().trim())) {
            throw new IllegalArgumentException("이미 쓰고 있는 아이디입니다: " + row.getLoginId().trim());
        }
        usrRepository.save(row.toEntity(passwordEncoder));
    }

    private void update(UsrSaveRequest row) {
        Usr usr = find(row.getUsrId());
        if (usr.hasRole(Role.ADMR) && !row.toRoles().contains(Role.ADMR)) {
            requireNotLastAdmr("역할을 해제할 수 없습니다");
        }
        row.updateEntity(usr, passwordEncoder);
    }

    private void delete(UsrSaveRequest row) {
        Usr usr = find(row.getUsrId());
        AuthUser.current().ifPresent(me -> {
            if (me.loginId().equals(usr.getLoginId())) {
                throw new IllegalArgumentException("자기 자신은 삭제할 수 없습니다.");
            }
        });
        if (usr.hasRole(Role.ADMR)) {
            requireNotLastAdmr("삭제할 수 없습니다");
        }
        usrRepository.delete(usr);
    }

    /** 시스템관리자가 0명이 되면 사용자 관리 화면 자체에 들어갈 수 없다 — 마지막 한 명은 남긴다 */
    private void requireNotLastAdmr(String action) {
        if (usrRepository.countByRole(Role.ADMR) <= 1) {
            throw new IllegalArgumentException("마지막 시스템관리자입니다 — " + action + ".");
        }
    }

    private Usr find(Long usrId) {
        return usrRepository.findById(usrId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + usrId));
    }
}
