package com.project.wmsback.master.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 보관 온도대. SKU(요구 온도대)와 Loc(존 온도대) 양쪽에서 사용하며,
 * 적치·이동 시 두 값의 일치를 검증한다 (온도대 제약).
 */
@Getter
@RequiredArgsConstructor
public enum TempZone {
    DRY("상온"),
    CHL("냉장"),
    FRZ("냉동");

    private final String label;
}
