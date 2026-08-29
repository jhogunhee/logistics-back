package com.project.wmsback.worker.dto;

import com.project.wmsback.worker.entity.WrkrWorkTyp;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 작업자 실적 조회 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class WrkrAcrstSearchCond {

    /** 작업자 = 로그인 아이디. 감사 컬럼 {@code created_by}가 실적의 귀속 축이다 */
    private String loginId;

    private WrkrWorkTyp workTyp;

    /** 작업일 범위 (from ~ to). createdAt 기준 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
}
