package com.project.mdm.mnu.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.mdm.mnu.entity.Mnu;
import com.project.mdm.mnu.entity.MnuDvsn;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * 메뉴 코드는 채번 없이 사용자가 입력한다 (신규일 때만, 중복 검증은 서버에서).
 * <p>
 * 자기 필드만으로 판정할 수 있는 검사와 엔티티 생성·반영은 여기서 한다({@link #toEntity} · {@link #updateEntity}).
 * DB를 봐야 하는 일(코드 중복 · 화면 경로 중복)은 서비스 몫이다.
 */
@Getter
@Setter
@NoArgsConstructor
public class MnuSaveRequest {

    /** 그리드 행 상태 (C/U/D). JSON 필드명은 프론트 그리드 관례대로 _status */
    @JsonProperty("_status")
    private String status;

    private String mnuCd;
    private String mnuNm;
    private MnuDvsn dvsn;
    private String grpNm;
    private Integer srtSeq;
    private String iconNm;
    private String scrnPth;
    private String apiPrfx;
    private String kywd;

    /** 신규 행 → 엔티티 */
    public Mnu toEntity() {
        if (mnuCd == null || mnuCd.isBlank()) {
            throw new IllegalArgumentException("메뉴 코드는 필수입니다.");
        }
        validateFields(mnuCd);
        return Mnu.builder()
                .mnuCd(mnuCd)
                .mnuNm(mnuNm)
                .dvsn(dvsn)
                .grpNm(grpNm)
                .srtSeq(srtSeq)
                .iconNm(iconNm)
                .scrnPth(scrnPth)
                .apiPrfx(blankToNull(apiPrfx))
                .kywd(blankToNull(kywd))
                .build();
    }

    /** 수정 행 → 기존 엔티티에 반영. 메뉴 코드는 권한 행이 참조하는 식별자라 바꾸지 않는다 */
    public void updateEntity(Mnu mnu) {
        validateFields(mnu.getMnuCd());
        mnu.update(mnuNm, dvsn, grpNm, srtSeq, iconNm, scrnPth, blankToNull(apiPrfx), blankToNull(kywd));
    }

    private void validateFields(String mnuCd) {
        if (mnuNm == null || mnuNm.isBlank()) {
            throw new IllegalArgumentException("메뉴 명은 필수입니다: " + mnuCd);
        }
        if (dvsn == null) {
            throw new IllegalArgumentException("구분은 필수입니다: " + mnuCd);
        }
        if (grpNm == null || grpNm.isBlank()) {
            throw new IllegalArgumentException("그룹 명은 필수입니다: " + mnuCd);
        }
        if (srtSeq == null) {
            throw new IllegalArgumentException("정렬순서는 필수입니다: " + mnuCd);
        }
        if (iconNm == null || iconNm.isBlank()) {
            throw new IllegalArgumentException("아이콘은 필수입니다: " + mnuCd);
        }
        if (scrnPth == null || scrnPth.isBlank()) {
            throw new IllegalArgumentException("화면 경로는 필수입니다: " + mnuCd);
        }
        if (!scrnPth.startsWith("/")) {
            throw new IllegalArgumentException("화면 경로는 /로 시작해야 합니다: " + mnuCd);
        }
        // 접두 매칭이 세그먼트 경계로 판정하므로 끝의 /는 뜻이 없고 비교만 어긋나게 한다
        if (apiPrfx != null && !apiPrfx.isBlank()
                && (!apiPrfx.startsWith("/") || apiPrfx.endsWith("/"))) {
            throw new IllegalArgumentException("API 접두는 /로 시작하고 /로 끝나지 않아야 합니다: " + mnuCd);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
