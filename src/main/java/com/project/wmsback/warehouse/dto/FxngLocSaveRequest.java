package com.project.wmsback.warehouse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.warehouse.entity.FxngLoc;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * <p>
 * 자기 필드만으로 판정할 수 있는 검사와 엔티티 생성·반영은 여기서 한다({@link #toEntity} · {@link #updateEntity}).
 * DB를 봐야 하는 일(상품·로케이션 존재 · 로케이션 중복)은 서비스 몫이다 —
 * 그래서 상품·로케이션은 코드로 받고, 서비스가 찾은 엔티티를 넘겨받아 싣는다.
 * STORAGE 여부 · 온도대 일치 · max ≤ loc.max_qty는 넘겨받은 둘만 보면 되므로 여기서 검사한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class FxngLocSaveRequest {

    /** 그리드 행 상태 (C/U/D). JSON 필드명은 프론트 그리드 관례대로 _status */
    @JsonProperty("_status")
    private String status;

    private Long fxngLocId;
    private String prodCd;
    private String locCd;
    private Long minQty;
    private Long maxQty;

    /** 신규 행 → 엔티티 */
    public FxngLoc toEntity(Prod prod, Loc loc) {
        validateFields(prod, loc);
        return FxngLoc.builder()
                .prod(prod)
                .loc(loc)
                .minQty(minQty)
                .maxQty(maxQty)
                .build();
    }

    /** 수정 행 → 기존 엔티티에 반영 */
    public void updateEntity(FxngLoc fxngLoc, Prod prod, Loc loc) {
        validateFields(prod, loc);
        fxngLoc.update(prod, loc, minQty, maxQty);
    }

    private void validateFields(Prod prod, Loc loc) {
        // STAGE는 적치·할당 후보 모집단 밖이라 지정해도 영영 쓰이지 않는다 — 등록 자체를 막는다
        if (loc.getLocTyp() != LocTyp.STORAGE) {
            throw new IllegalArgumentException("보관 로케이션만 고정할 수 있습니다: " + loc.getLocCd());
        }
        // 온도대가 다르면 적치·이동이 이 로케이션을 차단해 고정이 성립하지 않는다
        if (prod.getTmpZon() != loc.getTmpZon()) {
            throw new IllegalArgumentException(
                    "상품과 로케이션의 온도대가 다릅니다: %s ↔ %s".formatted(prod.getProdCd(), loc.getLocCd()));
        }
        // DB 제약(ck_fxng_loc_qty)을 커밋 전에 사용자 메시지로 돌려준다
        if (minQty == null || minQty < 0) {
            throw new IllegalArgumentException("재보충점은 0 이상 필수입니다: " + loc.getLocCd());
        }
        if (maxQty == null || maxQty < 1) {
            throw new IllegalArgumentException("보충 상한은 1 이상 필수입니다: " + loc.getLocCd());
        }
        if (minQty > maxQty) {
            throw new IllegalArgumentException("재보충점은 보충 상한 이하여야 합니다: " + loc.getLocCd());
        }
        // 로케이션이 물리적으로 담지 못하는 상한은 보충이 영영 도달 못 하는 목표가 된다
        if (loc.getMaxQty() != null && maxQty > loc.getMaxQty()) {
            throw new IllegalArgumentException(
                    "보충 상한은 로케이션 최대 적재 수량(%d) 이하여야 합니다: %s"
                            .formatted(loc.getMaxQty(), loc.getLocCd()));
        }
    }
}
