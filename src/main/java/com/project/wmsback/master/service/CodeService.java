package com.project.wmsback.master.service;

import com.project.wmsback.master.dto.CodeGroupResponse;
import com.project.wmsback.master.dto.CodeGroupSaveRequest;
import com.project.wmsback.master.dto.CodeResponse;
import com.project.wmsback.master.dto.CodeSaveRequest;
import com.project.wmsback.master.dto.CodeSearchCond;
import com.project.wmsback.master.entity.CodeDetail;
import com.project.wmsback.master.entity.CodeGroup;
import com.project.wmsback.master.entity.CodeDetailId;
import com.project.wmsback.master.repository.CodeDetailRepository;
import com.project.wmsback.master.repository.CodeGroupRepository;
import com.project.wmsback.master.repository.ProdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CodeService {

    /** 계량단위 그룹. 이 그룹만 하위 참조(상품·포장)가 있어 삭제 가드가 붙는다 */
    private static final String UOM_GRP_CD = "UOM";


    private final CodeDetailRepository codeDetailRepository;
    private final CodeGroupRepository codeGroupRepository;
    private final ProdRepository prodRepository;

    /** 그룹 목록. 공통코드 관리 화면이 어느 그룹을 편집할지 고르는 데 쓴다 */
    public List<CodeGroupResponse> groups() {
        return codeGroupRepository.findAllByOrderByGrpCd().stream()
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
            if (row.getGrpCd() == null || row.getGrpCd().isBlank()) {
                throw new IllegalArgumentException("그룹 코드는 필수입니다.");
            }
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
        requireGroupNm(row);
        if (codeGroupRepository.existsById(row.getGrpCd())) {
            throw new IllegalArgumentException("이미 존재하는 그룹입니다: " + row.getGrpCd());
        }
        codeGroupRepository.save(CodeGroup.builder()
                .grpCd(row.getGrpCd())
                .grpNm(row.getGrpNm())
                .description(row.getDescription())
                .build());
    }

    private void updateGroup(CodeGroupSaveRequest row) {
        requireGroupNm(row);
        findGroup(row.getGrpCd()).update(row.getGrpNm(), row.getDescription());
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

    private void requireGroupNm(CodeGroupSaveRequest row) {
        if (row.getGrpNm() == null || row.getGrpNm().isBlank()) {
            throw new IllegalArgumentException("그룹명은 필수입니다: " + row.getGrpCd());
        }
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
        for (CodeSaveRequest row : rows) {
            switch (row.getStatus()) {
                case "C" -> { validate(row); create(grpCd, row); }
                case "U" -> { validate(row); update(grpCd, row); }
                case "D" -> delete(grpCd, row);
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        codeDetailRepository.flush();
    }

    private void create(String grpCd, CodeSaveRequest row) {
        if (codeDetailRepository.existsById(new CodeDetailId(grpCd, row.getCodeCd()))) {
            throw new IllegalArgumentException("이미 존재하는 코드입니다: " + row.getCodeCd());
        }
        codeDetailRepository.save(CodeDetail.builder()
                .grpCd(grpCd)
                .codeCd(row.getCodeCd())
                .codeNm(row.getCodeNm())
                .srtSeq(row.getSrtSeq())
                .build());
    }

    /** 코드 값은 PK이자 로직이 리터럴로 참조하는 값이라 수정 대상에서 제외한다 */
    private void update(String grpCd, CodeSaveRequest row) {
        find(grpCd, row.getCodeCd()).update(row.getCodeNm(), row.getSrtSeq());
    }

    /**
     * 물리삭제. 코드성 테이블이라 FK가 없어 DB가 막아주지 않으므로 하위 참조를 직접 확인한다.
     * <p>
     * 참조 여부를 아는 방법은 그룹마다 다르다 — 지금 하위 데이터를 갖는 그룹은 UOM 하나뿐이라
     * 여기서 분기한다. 다른 그룹(TEMP_ZONE 등)은 값이 enum으로 코드에 박혀 있어서,
     * 지우면 조회는 되지만 저장이 막히는 형태로 드러난다.
     * <p>
     * 사용여부 컬럼을 두지 않으므로 "목록에서만 빼기"라는 중간 상태가 없다 — 참조가 있으면
     * 삭제를 거부하고, 없으면 실제로 지운다.
     */
    private void delete(String grpCd, CodeSaveRequest row) {
        CodeDetail code = find(grpCd, row.getCodeCd());
        if (UOM_GRP_CD.equals(grpCd)) {
            requireUnusedUom(code.getCodeCd());
        }
        codeDetailRepository.delete(code);
    }

    private void requireUnusedUom(String uomCd) {
        if (prodRepository.existsByInbUomCdOrOutbUomCd(uomCd, uomCd)) {
            throw new IllegalArgumentException(
                    "입고단위 또는 출고단위로 쓰이는 상품이 있어 삭제할 수 없습니다: " + uomCd);
        }
        if (prodRepository.existsByUomsUomCd(uomCd)) {
            throw new IllegalArgumentException(
                    "포장으로 쓰이는 상품이 있어 삭제할 수 없습니다: " + uomCd);
        }
    }

    private CodeDetail find(String grpCd, String codeCd) {
        return codeDetailRepository.findById(new CodeDetailId(grpCd, codeCd))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 코드입니다: " + grpCd + " / " + codeCd));
    }

    private void validate(CodeSaveRequest row) {
        if (row.getCodeCd() == null || row.getCodeCd().isBlank()) {
            throw new IllegalArgumentException("코드는 필수입니다.");
        }
        if (row.getCodeNm() == null || row.getCodeNm().isBlank()) {
            throw new IllegalArgumentException("코드명은 필수입니다: " + row.getCodeCd());
        }
        if (row.getSrtSeq() == null) {
            throw new IllegalArgumentException("정렬순서는 필수입니다: " + row.getCodeCd());
        }
    }
}
