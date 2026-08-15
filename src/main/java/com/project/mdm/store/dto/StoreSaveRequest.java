package com.project.mdm.store.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.mdm.store.entity.Store;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * 신규 행의 점포 코드는 클라이언트에서 받지 않는다 — 서버가 채번 규칙 STORE_CD로 발급한다.
 * <p>
 * 자기 필드만으로 판정할 수 있는 검사와 엔티티 생성·반영은 여기서 한다({@link #toEntity} · {@link #updateEntity}).
 * DB를 봐야 하는 일(채번 · 삭제 참조 검사)은 서비스 몫이다.
 */
@Getter
@Setter
@NoArgsConstructor
public class StoreSaveRequest {

    /** 그리드 행 상태 (C/U/D). JSON 필드명은 프론트 그리드 관례대로 _status */
    @JsonProperty("_status")
    private String status;

    private Long storeId;
    private String storeNm;
    private String storeGrp;
    private String storeTyp;
    private Short outbLifeRate;

    /** 신규 행 → 엔티티. 점포 코드는 서비스가 채번해 넘긴다 */
    public Store toEntity(String storeCd) {
        validateFields();
        return Store.builder()
                .storeCd(storeCd)
                .storeNm(storeNm)
                .storeGrp(storeGrp)
                .storeTyp(storeTyp)
                .outbLifeRate(outbLifeRate)
                .build();
    }

    /** 수정 행 → 기존 엔티티에 반영 */
    public void updateEntity(Store store) {
        validateFields();
        store.update(storeNm, storeGrp, storeTyp, outbLifeRate);
    }

    private void validateFields() {
        if (storeNm == null || storeNm.isBlank()) {
            throw new IllegalArgumentException("점포명은 필수입니다.");
        }
        // 그리드 숫자 셀을 비우면 null로 넘어온다 — DB NOT NULL·CHECK(0~100)에 맡기면
        // 어느 필드가 문제인지 없는 일반 메시지(409)가 나가므로 여기서 필드를 짚어 먼저 막는다
        if (outbLifeRate == null) {
            throw new IllegalArgumentException("잔여수명 허용률은 필수입니다: " + storeNm);
        }
        if (outbLifeRate < 0 || outbLifeRate > 100) {
            throw new IllegalArgumentException("잔여수명 허용률은 0~100 사이여야 합니다: " + storeNm);
        }
    }
}
