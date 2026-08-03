package com.project.wmsback.strategy.allocation.dto;

import java.util.List;

/**
 * 할당 미리보기 결과. 실전과 <b>같은 산정 함수</b>의 출력이라 구조가 실행 로그의 판정 근거와 같다 —
 * 화면이 두 곳을 한 컴포넌트로 렌더링할 수 있는 이유다.
 *
 * <p>{@code estimated}가 항상 true인 것은 락을 잡지 않기 때문이다: 미리보기 시점의 가용재고가
 * 실행 시점까지 다른 트랜잭션에 의해 바뀔 수 있다. 실전과 갈리는 지점은 이것 하나뿐이다.
 */
public record AlocPreviewResponse(
        String stgyNm,
        Long alocStgyId,
        Long rvsnNo,
        int lineCount,
        long reqQty,
        long asgnQty,
        long shortQty,
        boolean estimated,
        List<AllocGroupPlan> groups
) {

    public static AlocPreviewResponse of(String stgyNm, Long alocStgyId, Long rvsnNo,
                                         List<AllocGroupPlan> groups) {
        int lineCount = groups.stream().mapToInt(group -> group.lines().size()).sum();
        long reqQty = groups.stream().mapToLong(AllocGroupPlan::reqQty).sum();
        long asgnQty = groups.stream().mapToLong(AllocGroupPlan::asgnQty).sum();
        return new AlocPreviewResponse(stgyNm, alocStgyId, rvsnNo, lineCount,
                reqQty, asgnQty, reqQty - asgnQty, true, groups);
    }
}
