package com.project.wmsback.outbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 자동할당 실행 요청. 웨이브를 여러 건 고를 수 있지만 <b>한 트랜잭션이다</b> —
 * 도중 실패하면 이번 실행 전체가 롤백된다(부분 성공 없음).
 *
 * <p>웨이브별로 트랜잭션을 쪼개지 않는 이유는 웨이브 전략 실행({@code WaveStgyExecService})이
 * 여러 전략을 순회하면서도 「부분 편성 없음」을 택한 것과 같다. 대가가 작은 것은
 * <b>재고 부족이 실패가 아니기 때문</b>이다 — 부족분은 부분할당으로 정상 종료한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class AllocExecuteRequest {

    private List<Long> wavIds;
}
