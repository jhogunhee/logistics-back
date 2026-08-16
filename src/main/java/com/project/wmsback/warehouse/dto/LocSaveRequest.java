package com.project.wmsback.warehouse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Zon;
import com.project.mdm.prod.entity.TmpZon;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * 로케이션 코드는 채번 없이 사용자가 입력한다 (신규일 때만, 중복 검증은 서버에서).
 * <p>
 * 자기 필드만으로 판정할 수 있는 검사와 엔티티 생성·반영은 여기서 한다({@link #toEntity} · {@link #updateEntity}).
 * DB를 봐야 하는 일(코드 중복 · 존 존재와 온도대 일치 · 재고 · 참조 검사)은 서비스 몫이다 —
 * 그래서 존은 코드({@code zonCd})로 받고, 서비스가 찾은 {@link Zon}을 넘겨받아 엔티티에 싣는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class LocSaveRequest {

    /** 그리드 행 상태 (C/U/D). JSON 필드명은 프론트 그리드 관례대로 _status */
    @JsonProperty("_status")
    private String status;

    private Long locId;
    private String locCd;
    private String zonCd;
    private TmpZon tmpZon;
    private LocTyp locTyp;
    private Integer pikngPrty;
    private Integer ptawyPrty;
    private Long maxQty;

    /** 신규 행 → 엔티티. 우선순위 null→0 기본값은 엔티티 빌더가 맡는다 */
    public Loc toEntity(Zon zon) {
        if (locCd == null || locCd.isBlank()) {
            throw new IllegalArgumentException("로케이션 코드는 필수입니다.");
        }
        validateFields(locCd);
        return Loc.builder()
                .locCd(locCd)
                .zon(zon)
                .tmpZon(tmpZon)
                .locTyp(locTyp)
                .pikngPrty(pikngPrty)
                .ptawyPrty(ptawyPrty)
                .maxQty(maxQty)
                .build();
    }

    /** 수정 행 → 기존 엔티티에 반영. null→0 기본값 처리는 빌더와 함께 엔티티(update)가 맡는다 — 두 경로가 갈라지지 않게 */
    public void updateEntity(Loc loc, Zon zon) {
        validateFields(loc.getLocCd());
        loc.update(zon, tmpZon, locTyp, pikngPrty, ptawyPrty, maxQty);
    }

    private void validateFields(String locCd) {
        if (zonCd == null || zonCd.isBlank()) {
            throw new IllegalArgumentException("존은 필수입니다: " + locCd);
        }
        if (tmpZon == null) {
            throw new IllegalArgumentException("온도대는 필수입니다: " + locCd);
        }
        if (locTyp == null) {
            throw new IllegalArgumentException("유형은 필수입니다: " + locCd);
        }
        // DB 제약(ck_loc_storage_capacity · ck_loc_max_qty)을 커밋 전에 사용자 메시지로 돌려준다
        if (locTyp == LocTyp.STORAGE && maxQty == null) {
            throw new IllegalArgumentException("보관 로케이션은 최대 적재 수량이 필수입니다: " + locCd);
        }
        if (maxQty != null && maxQty < 1) {
            throw new IllegalArgumentException("최대 적재 수량은 1 이상이어야 합니다: " + locCd);
        }
        if (pikngPrty != null && pikngPrty < 0) {
            throw new IllegalArgumentException("피킹 우선순위는 0 이상이어야 합니다: " + locCd);
        }
        if (ptawyPrty != null && ptawyPrty < 0) {
            throw new IllegalArgumentException("적치 우선순위는 0 이상이어야 합니다: " + locCd);
        }
    }
}
