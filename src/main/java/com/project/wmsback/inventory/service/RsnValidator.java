package com.project.wmsback.inventory.service;

import com.project.mdm.code.entity.CodeDetailId;
import com.project.mdm.code.repository.CodeDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 사유코드 검증 — 보류 등록/해제·재고조사 조정·Lot 속성 정정·피킹 결품 종결이 같은 규칙을
 * 공유한다: 그룹에 존재해야 하고, ETC(기타)일 때만 텍스트 필수·그 외에는 무시(null 저장).
 */
@Component
@RequiredArgsConstructor
public class RsnValidator {

    private static final String ETC_RSN_CD = "ETC";

    private final CodeDetailRepository codeDetailRepository;

    /**
     * @param grpCd 사유코드가 속해야 하는 공통코드 그룹
     * @param label 오류 메시지에 쓸 사유 이름 (보류사유·해제사유·조정사유·정정사유)
     * @return 저장할 사유 텍스트 (ETC면 trim한 입력, 그 외 코드는 null)
     */
    public String validate(String grpCd, String label, String rsnCd, String rsnDscr) {
        if (!StringUtils.hasText(rsnCd)) {
            throw new IllegalArgumentException(label + "를 선택해야 합니다.");
        }
        if (!codeDetailRepository.existsById(new CodeDetailId(grpCd, rsnCd))) {
            throw new IllegalArgumentException("존재하지 않는 " + label + " 코드입니다: " + rsnCd);
        }
        if (ETC_RSN_CD.equals(rsnCd)) {
            if (!StringUtils.hasText(rsnDscr)) {
                throw new IllegalArgumentException(label + "가 기타일 때는 사유 내용을 입력해야 합니다.");
            }
            return rsnDscr.trim();
        }
        return null;
    }
}
