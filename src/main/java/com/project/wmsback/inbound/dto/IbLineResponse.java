package com.project.wmsback.inbound.dto;

import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.IbPrgr;
import com.project.mdm.prod.entity.TmpZon;
import lombok.Getter;

@Getter
public class IbLineResponse {

    private final Long ibLineId;
    private final Long prodId;
    private final String prodCd;
    private final String prodNm;
    /** 상품 이미지 URL. NULL = 이미지 없음 — 화면이 폴백을 그린다 */
    private final String prodImgUrl;
    private final TmpZon tmpZon;
    /**
     * 이 라인이 어디까지 왔는지 (IbLine#progressStatus). 저장된 값이 아니라 수량 셋에서 파생한다 —
     * 라인에는 상태 컬럼이 없다. 헤더의 5단계 진행과 같은 IbPrgr라 화면이 같은 뱃지를 그대로 쓴다.
     */
    private final IbPrgr status;
    /** 유통기한(일). 검수 화면이 유통기한 기본값(검수일+일수)을 제안할 때 사용. NULL = 미관리 */
    private final Integer shelfLifeDays;
    private final Long expctQty;
    private final Long rcvdQty;
    private final Long rjctQty;
    private final Long ptawyQty;
    /** 검수 입력 단위 — 정상 입고단위 · 반품 출고단위. 화면이 검수수량 입력 칸 옆에 라벨로 붙인다 */
    private final String inbUomCd;
    /**
     * 입고단위 1개 = 낱개(EA) 몇 개. 수량 컬럼(expct/rcvd/ptawy)은 낱개(EA) 저장이라,
     * 화면이 이 값으로 나눠 입고단위로 환산해 보여준다. 낱개 환산 표시에도 그대로 쓴다.
     */
    private final Long inbEaQty;

    private IbLineResponse(IbLine line, boolean hasOpenPtawyDrct) {
        this.ibLineId = line.getId();
        this.prodId = line.getProd().getId();
        this.prodCd = line.getProd().getProdCd();
        this.prodNm = line.getProd().getProdNm();
        this.prodImgUrl = line.getProd().getImgUrl();
        this.tmpZon = line.getProd().getTmpZon();
        this.status = line.progressStatus(hasOpenPtawyDrct);
        this.shelfLifeDays = line.getProd().getShelfLifeDays();
        this.expctQty = line.getExpctQty();
        this.rcvdQty = line.getRcvdQty();
        this.rjctQty = line.getRjctQty();
        this.ptawyQty = line.getPtawyQty();
        // 검수 입력 단위는 문서 구분이 정한다 — 정상 입고단위, 반품 출고단위 (IbOrder#rcvUomCd)
        this.inbUomCd = line.getIbOrder().rcvUomCd(line.getProd());
        this.inbEaQty = line.getProd().eaQtyOf(this.inbUomCd);
    }

    /** @param hasOpenPtawyDrct 이 라인에 미완료 적치지시가 있는가 — 진행단계 판정에 필요하다 */
    public static IbLineResponse from(IbLine line, boolean hasOpenPtawyDrct) {
        return new IbLineResponse(line, hasOpenPtawyDrct);
    }
}
