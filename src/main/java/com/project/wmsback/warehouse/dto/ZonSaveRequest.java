package com.project.wmsback.warehouse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.wmsback.warehouse.entity.BizDvsn;
import com.project.wmsback.warehouse.entity.StrgTyp;
import com.project.wmsback.warehouse.entity.Zon;
import com.project.mdm.prod.entity.TmpZon;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * 존 코드는 채번 없이 사용자가 입력한다 (신규일 때만, 중복 검증은 서버에서).
 * <p>
 * 자기 필드만으로 판정할 수 있는 검사와 엔티티 생성·반영은 여기서 한다({@link #toEntity} · {@link #updateEntity}).
 * DB를 봐야 하는 일(코드 중복 · 하위 로케이션 검사)은 서비스 몫이다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ZonSaveRequest {

    /** 그리드 행 상태 (C/U/D). JSON 필드명은 프론트 그리드 관례대로 _status */
    @JsonProperty("_status")
    private String status;

    private Long zonId;
    private String zonCd;
    private String zonNm;
    private TmpZon tmpZon;
    private StrgTyp strgTyp;
    private BizDvsn bizDvsn;

    /** 신규 행 → 엔티티 */
    public Zon toEntity() {
        if (zonCd == null || zonCd.isBlank()) {
            throw new IllegalArgumentException("존 코드는 필수입니다.");
        }
        validateFields(zonCd);
        return Zon.builder()
                .zonCd(zonCd)
                .zonNm(zonNm)
                .tmpZon(tmpZon)
                .strgTyp(strgTyp)
                .bizDvsn(bizDvsn)
                .build();
    }

    /** 수정 행 → 기존 엔티티에 반영. 존 코드는 하위 로케이션(loc.zon_cd)이 문자열로 참조하므로 바꾸지 않는다 */
    public void updateEntity(Zon zon) {
        validateFields(zon.getZonCd());
        zon.update(zonNm, tmpZon, strgTyp, bizDvsn);
    }

    private void validateFields(String zonCd) {
        if (zonNm == null || zonNm.isBlank()) {
            throw new IllegalArgumentException("존 명은 필수입니다: " + zonCd);
        }
        if (tmpZon == null) {
            throw new IllegalArgumentException("온도구분은 필수입니다: " + zonCd);
        }
        if (strgTyp == null) {
            throw new IllegalArgumentException("보관유형은 필수입니다: " + zonCd);
        }
        if (bizDvsn == null) {
            throw new IllegalArgumentException("업무구분은 필수입니다: " + zonCd);
        }
    }
}
