package com.project.wmsback.inbound.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 입고 진행 단계 — 화면 표시 전용 <b>파생값</b>이다. 어디에도 저장하지 않는다.
 * <p>
 * 저장 상태({@link IbStatus})는 파생 불가능한 사건 셋(예정/진행/확정)만 담고, 그 사이의
 * 진행(검수 중인가, 적치지시가 나갔는가, 적치까지 끝나 확정만 남았는가)은 라인 수량과
 * 적치지시 존재 여부에서 그때그때 계산한다. 판정의 주인은 라인({@link IbLine#progressStatus})이고,
 * 헤더는 라인 단계를 모아 만든다({@code IbOrderRepositoryImpl#progressCode} — SQL 집계).
 * 저장하면 수량과 상태가 두 벌이 되어 어긋날 자리가 생긴다 (docs/design.md 「상태와 수량의 분담」).
 * <p>
 * 양끝 세 값(SCHEDULED/RECEIVING/CONFIRMED)은 {@link IbStatus}와 같은 토큰을 쓴다 —
 * 프론트 뱃지 맵이 토큰을 공유하기 위해서다. 중간 두 값만 이 enum에만 있다.
 */
@Getter
@RequiredArgsConstructor
public enum IbPrgr {
    SCHEDULED("입고예정", 0),
    RECEIVING("검수", 1),
    PTAWY_DRCT("적치지시", 2),
    PTAWY_CMPL("적치완료", 3),
    CONFIRMED("입고확정", 4);

    private final String label;

    /**
     * 단계의 앞뒤 순서. 헤더가 라인 단계의 min/max를 취할 때 비교 기준이 되고, SQL CASE도 이 숫자를
     * 그대로 심어 Java와 같은 번호를 쓴다. {@code ordinal()}을 쓰지 않는 이유는 상수 순서를 바꾸는
     * 순간 SQL에 박힌 숫자와 조용히 어긋나기 때문이다.
     */
    private final int rank;
}
