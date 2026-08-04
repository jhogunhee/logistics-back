package com.project.wmsback.inbound.dto;

import com.project.wmsback.inbound.entity.IbLine;
import com.project.mdm.prod.entity.TmpZon;
import lombok.Getter;

@Getter
public class IbLineResponse {

    private final Long ibLineId;
    private final Long prodId;
    private final String prodCd;
    private final String prodNm;
    private final TmpZon tmpZon;
    /** 유통기한(일). 검수 화면이 유통기한 기본값(검수일+일수)을 제안할 때 사용. NULL = 미관리 */
    private final Integer shelfLifeDays;
    private final Long expctQty;
    private final Long rcvdQty;
    private final Long ptawyQty;
    /** 검수 입력 단위 = 입고단위(발주단위). 화면이 검수수량 입력 칸 옆에 라벨로 붙인다 */
    private final String inbUomCd;
    /** 입고단위 1개 = 낱개(EA) 몇 개. 화면의 낱개 환산 표시용 */
    private final Long inbEaQty;
    /**
     * 입고단위 1개 = 출고단위 몇 개 (입수). 수량 컬럼(expct/rcvd/ptawy)의 저장 단위가
     * 출고단위라서, 화면이 이 비율로 나눠 입고단위로 환산해 보여준다. 항상 정수 —
     * 나눗셈이 떨어지는 건 상품 저장 시점에 ProdService가 보장한다.
     */
    private final Long outbQtyPerInbUom;

    private IbLineResponse(IbLine line) {
        this.ibLineId = line.getId();
        this.prodId = line.getProd().getId();
        this.prodCd = line.getProd().getProdCd();
        this.prodNm = line.getProd().getProdNm();
        this.tmpZon = line.getProd().getTmpZon();
        this.shelfLifeDays = line.getProd().getShelfLifeDays();
        this.expctQty = line.getExpctQty();
        this.rcvdQty = line.getRcvdQty();
        this.ptawyQty = line.getPtawyQty();
        this.inbUomCd = line.getProd().getInbUomCd();
        this.inbEaQty = line.getProd().eaQtyOf(this.inbUomCd);
        this.outbQtyPerInbUom = this.inbEaQty / line.getProd().eaQtyOf(line.getProd().getOutbUomCd());
    }

    public static IbLineResponse from(IbLine line) {
        return new IbLineResponse(line);
    }
}
