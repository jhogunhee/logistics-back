package com.project.mdm.mnu.service;

import com.project.mdm.mnu.dto.MnuListResponse;
import com.project.mdm.mnu.dto.MnuResponse;
import com.project.mdm.mnu.dto.MnuRoleGridResponse;
import com.project.mdm.mnu.dto.MnuRoleSaveRequest;
import com.project.mdm.mnu.dto.MnuSaveRequest;
import com.project.mdm.mnu.entity.Mnu;
import com.project.mdm.mnu.entity.MnuDvsn;
import com.project.mdm.mnu.entity.MnuRole;
import com.project.mdm.mnu.repository.MnuRepository;
import com.project.mdm.mnu.repository.MnuRoleRepository;
import com.project.mdm.usr.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MnuService {

    private final MnuRepository mnuRepository;
    private final MnuRoleRepository mnuRoleRepository;
    private final MnuAccessCache mnuAccessCache;

    /** dvsn이 null이면 전부. 주인 없는 저장 API 목록을 함께 실어 보낸다 */
    public MnuListResponse list(MnuDvsn dvsn) {
        List<MnuResponse> menus = catalogOf(dvsn).stream().map(MnuResponse::from).toList();
        return new MnuListResponse(menus, mnuAccessCache.uncoveredEndpoints());
    }

    /** 신규(C)/수정(U)/삭제(D) 행 일괄 저장. 한 건이라도 실패하면 전체 롤백. */
    @Transactional
    public void saveAll(List<MnuSaveRequest> rows) {
        for (MnuSaveRequest row : rows) {
            switch (row.getStatus()) {
                case "C" -> create(row);
                case "U" -> update(row);
                case "D" -> delete(row);
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        mnuRepository.flush();
        mnuAccessCache.reload();
    }

    /**
     * 이 역할들이 볼 메뉴. 사이드바와 PDA 홈이 이걸로 그려진다.
     * <p>
     * ADMR은 매핑에 담기지 않는 역할이라 DB를 보지 않고 전부 돌려준다 —
     * 메뉴를 다 꺼도 관리자는 되살릴 수 있어야 한다({@code MnuAccessFilter}의 우회와 같은 이유).
     */
    public List<MnuResponse> menusOf(List<String> roleNames) {
        List<Mnu> menus = mnuRepository.findAllByOrderByGrpNmAscSrtSeqAsc();
        if (roleNames.contains(Role.ADMR.name())) {
            return menus.stream().map(MnuResponse::from).toList();
        }
        Set<String> granted = mnuRoleRepository
                .findAllByMnuCdIn(menus.stream().map(Mnu::getMnuCd).toList()).stream()
                .filter(r -> roleNames.contains(r.getRole().name()))
                .map(MnuRole::getMnuCd)
                .collect(toSet());

        return menus.stream()
                .filter(m -> granted.contains(m.getMnuCd()))
                .map(MnuResponse::from)
                .toList();
    }

    /** 권한 격자. dvsn이 null이면 전부 */
    public List<MnuRoleGridResponse> roleGrid(MnuDvsn dvsn) {
        List<Mnu> menus = catalogOf(dvsn);
        Map<String, List<Role>> byMnu = mnuRoleRepository
                .findAllByMnuCdIn(menus.stream().map(Mnu::getMnuCd).toList()).stream()
                .collect(groupingBy(MnuRole::getMnuCd, mapping(MnuRole::getRole, toList())));

        return menus.stream()
                .map(m -> MnuRoleGridResponse.of(m, byMnu.getOrDefault(m.getMnuCd(), List.of())))
                .toList();
    }

    /** 그 구분의 매핑을 통째로 교체한다 — 체크박스 격자라 「지운 칸」을 따로 표현하는 것보다 단순하고 재실행이 안전하다 */
    @Transactional
    public void replaceRoles(MnuDvsn dvsn, List<MnuRoleSaveRequest> rows) {
        List<String> scope = catalogOf(dvsn).stream().map(Mnu::getMnuCd).toList();

        List<MnuRole> next = new ArrayList<>();
        for (MnuRoleSaveRequest row : rows) {
            if (!scope.contains(row.getMnuCd())) {
                throw new IllegalArgumentException("이 구분에 없는 메뉴입니다: " + row.getMnuCd());
            }
            for (String role : row.getRoles()) {
                if (Role.ADMR.name().equals(role)) {
                    throw new IllegalArgumentException("시스템관리자는 메뉴 권한 대상이 아닙니다.");
                }
                next.add(new MnuRole(row.getMnuCd(), Role.valueOf(role)));
            }
        }
        mnuRoleRepository.deleteByMnuCdIn(scope);
        mnuRoleRepository.flush();
        mnuRoleRepository.saveAll(next);
        mnuAccessCache.reload();
    }

    private List<Mnu> catalogOf(MnuDvsn dvsn) {
        return mnuRepository.findAllByOrderByGrpNmAscSrtSeqAsc().stream()
                .filter(m -> dvsn == null || m.getDvsn() == dvsn)
                .toList();
    }

    private void create(MnuSaveRequest row) {
        Mnu mnu = row.toEntity();
        if (mnuRepository.existsById(mnu.getMnuCd())) {
            throw new IllegalArgumentException("이미 존재하는 메뉴 코드입니다: " + mnu.getMnuCd());
        }
        if (mnuRepository.existsByScrnPth(mnu.getScrnPth())) {
            throw new IllegalArgumentException("이미 쓰이는 화면 경로입니다: " + mnu.getScrnPth());
        }
        // api_prfx는 중복을 막지 않는다 — 같은 API를 나눠 쓰는 화면이 여럿이다(docs/design.md 「인가 — 두 단계」)
        mnuRepository.save(mnu);
    }

    private void update(MnuSaveRequest row) {
        Mnu mnu = find(row.getMnuCd());
        if (!mnu.getScrnPth().equals(row.getScrnPth())
                && mnuRepository.existsByScrnPth(row.getScrnPth())) {
            throw new IllegalArgumentException("이미 쓰이는 화면 경로입니다: " + row.getScrnPth());
        }
        row.updateEntity(mnu);
    }

    private void delete(MnuSaveRequest row) {
        Mnu mnu = find(row.getMnuCd());
        // FK가 없어 DB가 안 치운다 — 남으면 없는 메뉴를 가리키는 권한 행이 된다
        mnuRoleRepository.deleteByMnuCdIn(List.of(mnu.getMnuCd()));
        mnuRepository.delete(mnu);
    }

    private Mnu find(String mnuCd) {
        return mnuRepository.findById(mnuCd)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 메뉴입니다: " + mnuCd));
    }
}
