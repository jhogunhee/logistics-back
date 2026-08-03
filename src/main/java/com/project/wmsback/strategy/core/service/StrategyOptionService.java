package com.project.wmsback.strategy.core.service;

import com.project.wmsback.warehouse.entity.BizDvsn;
import com.project.mdm.prod.entity.TmpZon;
import com.project.mdm.code.repository.CodeDetailRepository;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.mdm.vendor.repository.VendorRepository;
import com.project.wmsback.strategy.core.dto.OptionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * 조건값·지정값의 동적 선택지 (optionSource → 기준정보 조회).
 * 소스 이름은 ConditionField의 optionSource와 일치해야 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StrategyOptionService {

    private final VendorRepository vendorRepository;
    private final ProdRepository prodRepository;
    private final CodeDetailRepository codeDetailRepository;

    public List<OptionResponse> options(String source) {
        return switch (source) {
            case "tmpZones" -> Arrays.stream(TmpZon.values())
                    .map(z -> new OptionResponse(z.name(), z.getLabel())).toList();
            case "bizDvsns" -> Arrays.stream(BizDvsn.values())
                    .map(b -> new OptionResponse(b.name(), b.getLabel())).toList();
            // 적치 전략 적용대상. 반품(RTNGS)은 스코프 아웃이라 제외 — 재도입 시 필터만 풀면 된다
            case "odrDvsns" -> codeDetailRepository.findByGrpCdOrderBySrtSeq("ODR_DVSN").stream()
                    .filter(c -> !"RTNGS".equals(c.getCodeCd()))
                    .map(c -> new OptionResponse(c.getCodeCd(), c.getCodeNm())).toList();
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
