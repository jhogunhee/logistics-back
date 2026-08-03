package com.project.wmsback.outbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 할당해제 요청. 단위는 <b>할당 레코드</b>다 — 화면의 「라인 전체」·「웨이브 전체」 버튼도
 * 결국 이 목록을 채워 보낸다.
 *
 * <p>피킹이 시작된 행({@code pikng_qty > 0})은 해제할 수 없다. 참고 시스템의
 * 「마지막 차수의 미지시분만 취소 가능」이 이 프로젝트에서는 이 조건 하나로 표현되고,
 * 그래서 할당차수 컬럼이 필요 없다.
 */
@Getter
@Setter
@NoArgsConstructor
public class AllocReleaseRequest {

    private List<Long> allocIds;
}
