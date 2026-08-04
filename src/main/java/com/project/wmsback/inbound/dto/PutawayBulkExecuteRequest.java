package com.project.wmsback.inbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 적치 일괄 실행 요청 — 한 상품의 지시 여러 건을 한 번에 소진할 때 쓴다
 * (용량 부족으로 1:N 분할된 지시를 작업자가 한 번 들고 나가 나눠 넣는 경우).
 * <p>
 * 건별 실행을 화면이 N번 호출하지 않고 엔드포인트를 따로 두는 이유는 <b>부분 실패</b> 때문이다.
 * 3건 중 2건이 반영된 뒤 실패하면 화면이 무엇이 반영됐는지 알 수 없다 — 전량 롤백이 맞다
 * (검수 저장·지시 생성도 같은 규칙).
 */
@Getter
@Setter
@NoArgsConstructor
public class PutawayBulkExecuteRequest {

    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        private Long taskId;
        /** 이번에 실행할 수량. 지시 잔여수량 이내 (부분 실행 허용) */
        private Long qty;
    }
}
