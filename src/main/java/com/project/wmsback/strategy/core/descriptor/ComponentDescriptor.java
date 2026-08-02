package com.project.wmsback.strategy.core.descriptor;

import java.util.List;

/**
 * 전략 구성요소(검수 규칙·적치 방식…)의 자기 기술. 관리자 화면 폼의 유일한 원천.
 * code는 DB에 저장되는 안정 식별자 — 절대 재사용·변경하지 않는다. 구현체를 은퇴시킬 때는
 * 삭제 대신 deprecated=true (화면에서 신규 선택 불가, 기존 정의는 계속 실행).
 */
public record ComponentDescriptor(
        String code,
        String name,
        String description,
        boolean deprecated,
        List<ParamSpec> params
) {

    public static ComponentDescriptor of(String code, String name, String description, List<ParamSpec> params) {
        return new ComponentDescriptor(code, name, description, false, params);
    }
}
