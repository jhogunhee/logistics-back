package com.project.wmsback.master.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.wmsback.master.entity.LocType;
import com.project.wmsback.master.entity.TempZone;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * 로케이션 코드는 채번 없이 사용자가 입력한다 (신규일 때만, 중복 검증은 서버에서).
 */
@Getter
@Setter
@NoArgsConstructor
public class LocSaveRequest {

    /** 그리드 행 상태 (C/U/D). JSON 필드명은 프론트 그리드 관례대로 _status */
    @JsonProperty("_status")
    private String status;

    private Long locId;
    private String locCd;
    private String zoneCd;
    private TempZone tempZone;
    private LocType locType;
    private Integer pickPrty;
}
