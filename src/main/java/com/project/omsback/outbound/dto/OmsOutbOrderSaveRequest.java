package com.project.omsback.outbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/** 출고주문 등록 요청. 주문번호는 서버가 채번한다 (SO-YYYYMMDD-NNN). */
@Getter
@Setter
@NoArgsConstructor
public class OmsOutbOrderSaveRequest {

    /** 납품처 점포 */
    private Long storeId;

    /** 출고유형 (공통코드 OUTB_TYP). 비워 보내면 서버가 NRML(일반출고)로 채운다 */
    private String outbTyp;

    /** 차량편수 (공통코드 VHCL_FLTNO). 비우면 배차 미정 */
    private String vhclFltno;

    /** 출고 예정일 */
    private LocalDate expctDe;

    /** 수주 담당자명 (선택) */
    private String picNm;

    /** 비고 (선택) */
    private String rmk;

    private List<OmsOutbLineSaveRequest> lines;
}
