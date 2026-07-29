package com.project.omsback.inbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/** 입고주문 등록 요청. 주문번호는 서버가 채번한다 (PO-YYYYMMDD-NNN). */
@Getter
@Setter
@NoArgsConstructor
public class OmsIbOrderCreateRequest {

    private Long vendorId;
    private LocalDate expctDe;
    private List<OmsIbLineCreateRequest> lines;
}
