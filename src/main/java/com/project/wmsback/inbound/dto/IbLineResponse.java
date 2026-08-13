package com.project.wmsback.inbound.dto;

import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.IbStatus;
import com.project.mdm.prod.entity.TmpZon;
import lombok.Getter;

@Getter
public class IbLineResponse {

    private final Long ibLineId;
    private final Long prodId;
    private final String prodCd;
    private final String prodNm;
    private final TmpZon tmpZon;
    /**
     * 이 라인이 어디까지 왔는지 (IbLine#progressStatus). 저장된 값이 아니라 수량 셋에서 파생한다 —
     * 라인에는 상태 컬럼이 없다. 헤더와 같은 IbStatus라 화면이 같은 뱃지를 그대로 쓴다.
     */
    private final IbStatus status;
    /** 유통기한(일). 검수 화면이 유통기한 기본값(검수일+일수)을 제안할 때 사용. NULL = 미관리 */
    private final Integer shelfLifeDays;
    private final Long expctQty;
    private final Long rcvdQty;
    private final Long ptawyQty;
    /** 검수 입력 단위 = 입고단위(발주단위). 화면이 검수수량 입력 칸 옆에 라벨로 붙인다 */
    private final String inbUomCd;
    /**
     * 입고단위 1개 = 낱개(EA) 몇 개. 수량 컬럼(expct/rcvd/ptawy)은 낱개(EA) 저장이라,
     * 화면이 이 값으로 나눠 입고단위로 환산해 보여준다. 낱개 환산 표시에도 그대로 쓴다.
     */
    private final Long inbEaQty;

    private IbLineResponse(IbLine line) {
        this.ibLineId = line.getId();
        this.prodId = line.getProd().getId();
        this.prodCd = line.getProd().getProdCd();
        this.prodNm = line.getProd().getProdNm();
        this.tmpZon = line.getProd().getTmpZon();
        this.status = line.progressStatus();
        this.shelfLifeDays = line.getProd().getShelfLifeDays();
        this.expctQty = line.getExpctQty();
        this.rcvdQty = line.getRcvdQty();
        this.ptawyQty = line.getPtawyQty();
        this.inbUomCd = line.getProd().getInbUomCd();
        this.inbEaQty = line.getProd().eaQtyOf(this.inbUomCd);
    }

    public static IbLineResponse from(IbLine line) {
        return new IbLineResponse(line);
    }
}
