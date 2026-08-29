package com.project.wmsback.worker.dto;

import com.project.wmsback.inventory.entity.RefDocTyp;
import com.project.wmsback.inventory.entity.TxTyp;

import java.time.LocalDate;

/**
 * 집계 쿼리의 원시 묶음 한 줄. 작업 종류 분류를 SQL의 CASE로 흩뿌리지 않으려고
 * {@code (tx_typ, rfn_doc_typ)}까지만 묶어서 꺼내고, 종류로 접는 것은 서비스가 한다
 * ({@code WrkrWorkTyp.of}가 유일한 분류 자리).
 *
 * @param workDt 일자별 추이에서만 채워진다 (작업자별 요약은 null)
 */
public record WrkrAcrstGroup(LocalDate workDt, String loginId, String usrNm,
                             TxTyp txTyp, RefDocTyp rfnDocTyp, long cnt, long qty) {
}
