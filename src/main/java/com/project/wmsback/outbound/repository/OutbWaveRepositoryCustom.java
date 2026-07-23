package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.dto.OutbWaveSearchCond;
import com.project.wmsback.outbound.entity.OutbWave;

import java.util.List;

public interface OutbWaveRepositoryCustom {

    List<OutbWave> search(OutbWaveSearchCond cond);
}
