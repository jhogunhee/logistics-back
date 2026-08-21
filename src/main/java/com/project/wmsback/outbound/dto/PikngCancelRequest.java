package com.project.wmsback.outbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 피킹지시 취소 요청 — 취소 단위 둘이 한 조작으로 모인다. <b>둘 중 하나만 채워 보낸다.</b>
 */
@Getter
@Setter
@NoArgsConstructor
public class PikngCancelRequest {

    /** 발행을 통째로 무른다 — 웨이브에 실적이 하나라도 있으면 거부된다 */
    private List<Long> wavIds;

    /** 지정한 지시만 무른다 — 판정은 그 지시 자신의 실적뿐이다 */
    private List<Long> taskIds;
}
