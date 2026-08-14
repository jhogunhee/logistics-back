package com.project.wmsback.inbound.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 입고 진행 단계 — 화면 표시 전용 <b>파생값</b>이다. 어디에도 저장하지 않는다.
 * <p>
 * 저장 상태({@link IbStatus})는 파생 불가능한 사건 셋(예정/진행/확정)만 담고, 그 사이의
 * 진행(검수 중인가, 적치지시가 나갔는가, 적치까지 끝나 확정만 남았는가)은 라인 수량과
 * 적치지시 존재 여부에서 그때그때 계산한다({@code IbOrder#progress}, {@code IbLine#progressStatus}).
 * 저장하면 수량과 상태가 두 벌이 되어 어긋날 자리가 생긴다 (docs/design.md 「상태와 수량의 분담」).
 * <p>
 * 양끝 세 값(SCHEDULED/RECEIVING/CONFIRMED)은 {@link IbStatus}와 같은 토큰을 쓴다 —
 * 프론트 뱃지 맵이 토큰을 공유하기 위해서다. 중간 두 값만 이 enum에만 있다.
 */
@Getter
@RequiredArgsConstructor
public enum IbPrgr {
    SCHEDULED("입고예정"),
    RECEIVING("검수"),
    PTAWY_DRCT("적치지시"),
    PTAWY_CMPL("적치완료"),
    CONFIRMED("입고확정");

    private final String label;
}
