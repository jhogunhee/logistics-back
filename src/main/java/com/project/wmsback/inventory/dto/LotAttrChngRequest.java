package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Lot 속성 정정 요청 (재고 속성변경 저장).
 *
 * 두 날짜는 「바꿀 값」이 아니라 **정정 후의 최종 값**이다 — 한쪽만 바꿔도 둘 다 실어 보낸다.
 * 서버가 현재 값과 비교해 실제로 달라진 필드를 이력에 남기고, 둘 다 그대로면 거부한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class LotAttrChngRequest {

    /** 정정 후 제조일자. 배치 재사용 키(상품+입고일자+제조일자)의 일부라 충돌 검증 대상 */
    private LocalDate mfgDt;

    /** 정정 후 유통기한. 제조일자+shelfLifeDays와 일치할 필요는 없다 (벤더 인쇄값 정정이 주 사용처) */
    private LocalDate expiryDt;

    /** 정정 사유 코드 (공통코드 LOT_ATTR_RSN) */
    private String rsnCd;

    /** 기타 사유 텍스트. rsnCd가 ETC일 때만 필수이고 그 외에는 서버가 무시한다 */
    private String rsnDscr;
}
