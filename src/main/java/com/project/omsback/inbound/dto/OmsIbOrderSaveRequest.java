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
public class OmsIbOrderSaveRequest {

    private Long vendorId;
    private LocalDate expctDe;
    /** 발주구분 (공통코드 ODR_DVSN). 비워 보내면 서버가 NRML(정상)로 채운다 */
    private String odrDvsn;

    /** 발주 담당자명 (선택) */
    private String picNm;

    /** 비고 (선택) */
    private String rmk;

    private List<OmsIbLineSaveRequest> lines;
}
