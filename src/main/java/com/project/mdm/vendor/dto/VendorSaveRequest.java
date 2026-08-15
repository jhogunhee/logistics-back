package com.project.mdm.vendor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.mdm.vendor.entity.Vendor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * 신규 행의 벤더 코드는 클라이언트에서 받지 않는다 — 서버가 시퀀스로 채번한다.
 * <p>
 * 자기 필드만으로 판정할 수 있는 검사와 엔티티 생성·반영은 여기서 한다({@link #toEntity} · {@link #updateEntity}).
 * DB를 봐야 하는 일(채번 · 삭제 참조 검사)은 서비스 몫이다.
 */
@Getter
@Setter
@NoArgsConstructor
public class VendorSaveRequest {

    /** 그리드 행 상태 (C/U/D). JSON 필드명은 프론트 그리드 관례대로 _status */
    @JsonProperty("_status")
    private String status;

    private Long vendorId;
    private String vndrNm;
    private String picNm;
    private String telNo;

    /** 신규 행 → 엔티티. 벤더 코드는 서비스가 채번해 넘긴다 */
    public Vendor toEntity(String vndrCd) {
        validateFields();
        return Vendor.builder()
                .vndrCd(vndrCd)
                .vndrNm(vndrNm)
                .picNm(picNm)
                .telNo(telNo)
                .build();
    }

    /** 수정 행 → 기존 엔티티에 반영 */
    public void updateEntity(Vendor vendor) {
        validateFields();
        vendor.update(vndrNm, picNm, telNo);
    }

    private void validateFields() {
        if (vndrNm == null || vndrNm.isBlank()) {
            throw new IllegalArgumentException("벤더명은 필수입니다.");
        }
    }
}
