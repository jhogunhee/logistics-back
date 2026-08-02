package com.project.wmsback.strategy.core.service;

import com.project.wmsback.master.entity.BizDvsn;
import com.project.wmsback.master.entity.TempZone;
import com.project.wmsback.master.repository.CodeDetailRepository;
import com.project.wmsback.master.repository.ProdRepository;
import com.project.wmsback.master.repository.VendorRepository;
import com.project.wmsback.master.repository.ZonRepository;
import com.project.wmsback.strategy.core.dto.OptionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * 조건값·파라미터의 동적 선택지 (optionSource → 기준정보 조회).
 * 소스 이름은 ConditionField·ParamSpec의 optionSource와 일치해야 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StrategyOptionService {

    private final ZonRepository zonRepository;
    private final VendorRepository vendorRepository;
    private final ProdRepository prodRepository;
    private final CodeDetailRepository codeDetailRepository;

    public List<OptionResponse> options(String source) {
        return switch (source) {
            case "tmpZones" -> Arrays.stream(TempZone.values())
                    .map(z -> new OptionResponse(z.name(), z.getLabel())).toList();
            case "bizDvsns" -> Arrays.stream(BizDvsn.values())
                    .map(b -> new OptionResponse(b.name(), b.getLabel())).toList();
            case "zones" -> zonRepository.findAll().stream()
                    .map(z -> new OptionResponse(z.getZonCd(), z.getZonNm())).toList();
            case "vendors" -> vendorRepository.findAll().stream()
                    .map(v -> new OptionResponse(v.getVndrCd(), v.getVndrNm())).toList();
            case "prods" -> prodRepository.findAll().stream()
                    .map(p -> new OptionResponse(p.getProdCd(), p.getProdNm())).toList();
            case "uoms" -> codeDetailRepository.findByGrpCdOrderBySrtSeq("UOM").stream()
                    .map(c -> new OptionResponse(c.getCodeCd(), c.getCodeNm())).toList();
            default -> throw new IllegalArgumentException("없는 선택지 소스입니다: " + source);
        };
    }
}
