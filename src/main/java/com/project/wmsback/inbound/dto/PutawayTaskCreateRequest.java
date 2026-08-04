package com.project.wmsback.inbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 적치지시 생성 요청. 전략 일괄 추천 결과와 수동 지시(로케이션 직접 선택)가 같은 형태로 들어온다 —
 * 수동은 배치 1건에 배정 1건일 뿐이다.
 * <p>
 * 한 배치가 여러 로케이션으로 쪼개질 수 있어(용량 부족 시 1:N 분할) 배정이 목록이다.
 * 전체가 한 트랜잭션이라 한 건이라도 검증에 걸리면 전량 롤백된다.
 */
@Getter
@Setter
@NoArgsConstructor
public class PutawayTaskCreateRequest {

    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        private Long ibLineId;
        private Long lotId;
        private List<Assignment> assignments;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Assignment {
        private Long locId;
        private Long qty;
    }
}
