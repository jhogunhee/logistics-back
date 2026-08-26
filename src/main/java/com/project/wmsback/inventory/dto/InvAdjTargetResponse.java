package com.project.wmsback.inventory.dto;

import lombok.Getter;

import java.time.LocalDate;

/**
 * 재고조정 가용 라인 대상 1건 = 화면 1행. 행 단위는 inv 행(재고 키)이고, 화면은 여기서 담은 뒤
 * 조정수량을 입력한다. QueryDSL Projections.constructor로 직접 채워지므로 생성자가 public이다.
 *
 * <b>가용수량 0인 행도 내려간다</b> — 로트변경 대상 조회가 {@code avalQty > 0}으로 거르는 것과
 * 갈리는 지점이다. 조정은 (+) 방향이 있어 예약·보류로 가용이 0인 재고도 정당한 대상이다.
 * 조정전수량으로 쓰는 값은 가용이 아니라 <b>onHandQty</b>이고, 감소 한도만 avalQty가 된다.
 */
@Getter
public class InvAdjTargetResponse {

    private final Long prodId;
    private final String prodCd;
    private final String prodNm;
    private final Long locId;
    private final String locCd;
    private final Long lotId;
    private final String lotNo;
    private final LocalDate expiryDt;
    /** 조정전수량으로 화면에 표시되는 값 (조정후수량 = 이 값 + 조정수량) */
    private final Long onHandQty;
    private final Long alocQty;
    private final Long hldQty;
    /** 가용수량 = 보유 − 예약 − 보류 (파생값). 감소 조정의 상한 */
    private final Long avalQty;

    public InvAdjTargetResponse(Long prodId, String prodCd, String prodNm,
                                Long locId, String locCd, Long lotId, String lotNo, LocalDate expiryDt,
                                Long onHandQty, Long alocQty, Long hldQty, Long avalQty) {
        this.prodId = prodId;
        this.prodCd = prodCd;
        this.prodNm = prodNm;
        this.locId = locId;
        this.locCd = locCd;
        this.lotId = lotId;
        this.lotNo = lotNo;
        this.expiryDt = expiryDt;
        this.onHandQty = onHandQty;
        this.alocQty = alocQty;
        this.hldQty = hldQty;
        this.avalQty = avalQty;
    }
}
