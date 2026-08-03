package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 보류 해제 요청. 특정 보류 건을 지목해 잔량 이내로 해제한다 (부분 해제 허용) */
@Getter
@Setter
@NoArgsConstructor
public class InvHldReleaseRequest {

    private Long qty;
    /** 해제 사유 코드 (공통코드 HLD_RLZ_RSN — 등록 사유와 별개 그룹) */
    private String rsnCd;
    /** 기타 사유 텍스트. rsnCd = ETC일 때만 필수, 그 외 코드에서는 무시된다 */
    private String rsnDscr;
}
