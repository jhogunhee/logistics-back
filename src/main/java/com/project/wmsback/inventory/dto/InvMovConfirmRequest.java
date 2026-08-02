package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 이동확정 요청. 지시가 이미 (재고, TO, 수량)을 확정해 놨으므로 확정수량만 받는다 (부분확정 허용) */
@Getter
@Setter
@NoArgsConstructor
public class InvMovConfirmRequest {

    private Long qty;
}
