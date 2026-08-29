package com.project.wmsback.worker.dto;

import com.project.wmsback.worker.entity.WrkrWorkTyp;

import java.time.LocalDateTime;

/**
 * 드릴다운 한 행 = 집계에서 센 그 한 건. 요약과 같은 조건·같은 다리만 걸러 나오므로
 * 「합계 = 목록 건수」가 화면에서 어긋나지 않는다.
 */
public record WrkrAcrstDetailResponse(Long invHistId, LocalDateTime workDtm, String loginId, String usrNm,
                                      WrkrWorkTyp workTyp, String prodCd, String prodNm, String lotNo,
                                      String locCd, String fromLocCd, String toLocCd,
                                      long qty, String rfnDocNo) {
}
