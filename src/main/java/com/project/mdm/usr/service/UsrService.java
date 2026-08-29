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
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsrService {

    private final UsrRepository usrRepository;
    private final PasswordEncoder passwordEncoder;
    /** 역할이 바뀐 사용자를 즉시 내보내는 창구. 세션 저장소가 DB라 다른 인스턴스의 세션도 같이 끊긴다 */
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

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
        Set<Role> before = Set.copyOf(usr.getRoles());
        Set<Role> after = row.toRoles();
        if (before.contains(Role.ADMR) && !after.contains(Role.ADMR)) {
            // 삭제와 같은 짝이다 — 아래 마지막 관리자 가드는 「시스템에 관리자가 0명이 되는 것」을 막지,
            // 관리자가 둘일 때 자기 것을 떼는 것은 통과시킨다. 그러면 시스템은 멀쩡한데
            // 누른 사람만 세션이 끊겨 이 화면에 다시 못 들어온다 (다른 관리자가 해주면 된다)
            requireNotSelf(usr, "자기 자신의 시스템관리자 역할은 해제할 수 없습니다.");
            requireNotLastAdmr("역할을 해제할 수 없습니다");
        }
        row.updateEntity(usr, passwordEncoder);

        // 역할이 바뀌면 그 사람의 세션을 끊는다 — 세션에 실린 권한은 로그인 시점 것이라,
        // 끊지 않으면 관리자가 방금 뺏은 권한으로 계속 돈다(다시 로그인하면 새 역할로 들어온다)
        if (!before.equals(after)) {
            expireSessionsOf(usr.getLoginId());
        }
    }

    private void delete(UsrSaveRequest row) {
        Usr usr = find(row.getUsrId());
        requireNotSelf(usr, "자기 자신은 삭제할 수 없습니다.");
        if (usr.hasRole(Role.ADMR)) {
            requireNotLastAdmr("삭제할 수 없습니다");
        }
        usrRepository.delete(usr);
        expireSessionsOf(usr.getLoginId());
    }

    /**
     * 자기 발등 찍기 방지. 「시스템이 잠기는 것」을 막는 {@link #requireNotLastAdmr}와 걸리는 조건이
     * 겹치지 않는다 — 관리자가 둘이어도 자기 권한을 떼면 자기만 갇힌다. 다른 관리자가 해주면 된다.
     * <p>
     * 인증 없이 도는 실행(스케줄러 · 시드)에서는 비교할 「나」가 없어 통과시킨다.
     */
    private void requireNotSelf(Usr usr, String message) {
        AuthUser.current().ifPresent(me -> {
            if (me.loginId().equals(usr.getLoginId())) {
                throw new IllegalArgumentException(message);
            }
        });
    }

    /** 시스템관리자가 0명이 되면 사용자 관리 화면 자체에 들어갈 수 없다 — 마지막 한 명은 남긴다 */
    private void requireNotLastAdmr(String action) {
        if (usrRepository.countByRole(Role.ADMR) <= 1) {
            throw new IllegalArgumentException("마지막 시스템관리자입니다 — " + action + ".");
        }
    }

    private void expireSessionsOf(String loginId) {
        sessionRepository.findByPrincipalName(loginId).keySet()
                .forEach(sessionRepository::deleteById);
    }

    private Usr find(Long usrId) {
        return usrRepository.findById(usrId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + usrId));
    }
}
