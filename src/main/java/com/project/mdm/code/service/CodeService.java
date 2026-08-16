package com.project.mdm.code.service;

import com.project.mdm.code.dto.CodeGroupResponse;
import com.project.mdm.code.dto.CodeGroupSaveRequest;
import com.project.mdm.code.dto.CodeGroupSearchCond;
import com.project.mdm.code.dto.CodeResponse;
import com.project.mdm.code.dto.CodeSaveRequest;
import com.project.mdm.code.dto.CodeSearchCond;
import com.project.mdm.code.entity.CodeDetail;
import com.project.mdm.code.entity.CodeGroup;
import com.project.mdm.code.entity.CodeDetailId;
import com.project.mdm.code.repository.CodeDetailRepository;
import com.project.mdm.code.repository.CodeGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CodeService {

    private final CodeDetailRepository codeDetailRepository;
    private final CodeGroupRepository codeGroupRepository;

    /** 그룹 목록. 공통코드 관리 화면이 어느 그룹을 편집할지 고르는 데 쓴다 */
    public List<CodeGroupResponse> groups() {
        return codeGroupRepository.findAllByOrderByGrpCd().stream()
                .map(CodeGroupResponse::from)
                .toList();
    }

    /** 관리 화면용 그룹 검색 (그룹코드/그룹명 부분일치) */
    public List<CodeGroupResponse> searchGroups(CodeGroupSearchCond cond) {
        return codeGroupRepository.search(cond).stream()
                .map(CodeGroupResponse::from)
                .toList();
    }

    /**
     * 그룹 일괄 저장. 코드와 같은 C/U/D 그리드 규약이다.
     * <p>
     * 그룹 코드는 PK이자 로직이 리터럴로 참조하는 값이라 수정 대상에서 제외한다 —
     * 이름과 설명만 고친다. 삭제는 하위 코드가 없을 때만 되는데, FK가 없어 DB가 막아주지
     * 않기 때문에 여기서 직접 확인한다(지우면 그 코드들이 어느 그룹에도 속하지 않게 된다).
     */
    @Transactional
    public void saveAllGroups(List<CodeGroupSaveRequest> rows) {
        for (CodeGroupSaveRequest row : rows) {
            switch (row.getStatus()) {
                case "C" -> createGroup(row);
                case "U" -> updateGroup(row);
                case "D" -> deleteGroup(row);
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        codeGroupRepository.flush();
    }

    private void createGroup(CodeGroupSaveRequest row) {
        CodeGroup group = row.toEntity();
        if (codeGroupRepository.existsById(group.getGrpCd())) {
            throw new IllegalArgumentException("이미 존재하는 그룹입니다: " + group.getGrpCd());
        }
        codeGroupRepository.save(group);
    }

    private void updateGroup(CodeGroupSaveRequest row) {
        row.updateEntity(findGroup(row.getGrpCd()));
    }

    private void deleteGroup(CodeGroupSaveRequest row) {
        CodeGroup group = findGroup(row.getGrpCd());
        if (codeDetailRepository.existsByGrpCd(group.getGrpCd())) {
            throw new IllegalArgumentException(
                    "코드가 남아 있는 그룹은 삭제할 수 없습니다. 코드를 먼저 지우세요: " + group.getGrpCd());
        }
        codeGroupRepository.delete(group);
    }

    private CodeGroup findGroup(String grpCd) {
        return codeGroupRepository.findById(grpCd)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 그룹입니다: " + grpCd));
    }

    /** 그룹의 코드 목록 (srt_seq 순). 화면 콤보박스가 쓴다 */
    public List<CodeResponse> list(String grpCd) {
        return codeDetailRepository.findByGrpCdOrderBySrtSeq(grpCd).stream()
                .map(CodeResponse::from)
                .toList();
    }

    /** 관리 화면용 검색 (코드/코드명 부분일치) */
    public List<CodeResponse> search(String grpCd, CodeSearchCond cond) {
        return codeDetailRepository.search(grpCd, cond).stream()
                .map(CodeResponse::from)
                .toList();
    }

    /** 신규(C)/수정(U)/삭제(D) 행 일괄 저장. 한 건이라도 실패하면 전체 롤백. */
    @Transactional
    public void saveAll(String grpCd, List<CodeSaveRequest> rows) {
        // 없는 그룹에 코드를 만들면 어느 그룹에도 보이지 않는 고아 코드가 된다 (FK가 없어 DB가 막지 않는다)
        if (!codeGroupRepository.existsById(grpCd)) {
            throw new IllegalArgumentException("존재하지 않는 그룹입니다: " + grpCd);
        }
        for (CodeSaveRequest row : rows) {
            switch (row.getStatus()) {
                case "C" -> create(grpCd, row);
                case "U" -> update(grpCd, row);
                case "D" -> delete(grpCd, row);
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        codeDetailRepository.flush();
    }

    private void create(String grpCd, CodeSaveRequest row) {
        CodeDetail code = row.toEntity(grpCd);
        if (codeDetailRepository.existsById(new CodeDetailId(grpCd, code.getCodeCd()))) {
            throw new IllegalArgumentException("이미 존재하는 코드입니다: " + code.getCodeCd());
        }
        codeDetailRepository.save(code);
    }

    private void update(String grpCd, CodeSaveRequest row) {
        row.updateEntity(find(grpCd, row.getCodeCd()));
    }

    /**
     * 물리삭제(사용여부 같은 중간 상태 없음). 참조 검사는 하지 않는다 — 공통코드는 값(문자열)으로
     * 참조되므로 지워져도 데이터는 남고, 같은 코드를 재등록하면 완전히 복구된다
     */
    private void delete(String grpCd, CodeSaveRequest row) {
        codeDetailRepository.delete(find(grpCd, row.getCodeCd()));
    }

    private CodeDetail find(String grpCd, String codeCd) {
        return codeDetailRepository.findById(new CodeDetailId(grpCd, codeCd))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 코드입니다: " + grpCd + " / " + codeCd));
    }
}
