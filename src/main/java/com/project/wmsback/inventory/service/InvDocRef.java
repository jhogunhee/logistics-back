package com.project.wmsback.inventory.service;

import com.project.wmsback.inventory.entity.RefDocTyp;

/**
 * 재고 이력에 남길 근거 문서 참조. {@link InvStore}가 inv_hist 행을 만들 때 채우는 값이다 —
 * 수량·로케이션·유형은 스냅샷 증감에서 나오므로, 호출자는 문서 쪽 컬럼만 넘긴다.
 */
public record InvDocRef(RefDocTyp rfnDocTyp, String rfnDocNo, Long ibLineId, Long cnclInvHistId) {

    public static InvDocRef of(RefDocTyp rfnDocTyp, String rfnDocNo) {
        return new InvDocRef(rfnDocTyp, rfnDocNo, null, null);
    }

    /** 입고 라인 단위 추적이 필요한 건 (RECEIVE와 그 취소 ADJUST) */
    public static InvDocRef ofIbLine(RefDocTyp rfnDocTyp, String rfnDocNo, Long ibLineId) {
        return new InvDocRef(rfnDocTyp, rfnDocNo, ibLineId, null);
    }

    /** 원본 이력을 되돌리는 취소 건 */
    public InvDocRef cancelling(Long cnclInvHistId) {
        return new InvDocRef(rfnDocTyp, rfnDocNo, ibLineId, cnclInvHistId);
    }
}
