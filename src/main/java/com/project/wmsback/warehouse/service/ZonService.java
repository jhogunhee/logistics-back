package com.project.wmsback.warehouse.service;

import com.project.wmsback.warehouse.dto.ZonResponse;
import com.project.wmsback.warehouse.dto.ZonSaveRequest;
import com.project.wmsback.warehouse.dto.ZonSearchCond;
import com.project.wmsback.warehouse.entity.Zon;
import com.project.wmsback.warehouse.repository.LocRepository;
import com.project.wmsback.warehouse.repository.ZonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ZonService {

    private final ZonRepository zonRepository;
    private final LocRepository locRepository;

    public List<ZonResponse> list(ZonSearchCond cond) {
        return zonRepository.search(cond).stream()
                .map(ZonResponse::from)
                .toList();
    }

    /** 신규(C)/수정(U)/삭제(D) 행 일괄 저장. 한 건이라도 실패하면 전체 롤백. */
    @Transactional
    public void saveAll(List<ZonSaveRequest> rows) {
        for (ZonSaveRequest row : rows) {
            switch (row.getStatus()) {
                case "C" -> { validate(row); create(row); }
                case "U" -> { validate(row); update(row); }
                case "D" -> delete(row);
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        zonRepository.flush();
    }

    private void create(ZonSaveRequest row) {
        if (zonRepository.existsByZonCd(row.getZonCd())) {
            throw new IllegalArgumentException("이미 존재하는 존 코드입니다: " + row.getZonCd());
        }
        zonRepository.save(Zon.builder()
                .zonCd(row.getZonCd())
                .zonNm(row.getZonNm())
                .tmpZon(row.getTmpZon())
                .strgTyp(row.getStrgTyp())
                .bizDvsn(row.getBizDvsn())
                .build());
    }

    /** 존 코드는 하위 로케이션(loc.zon_cd)이 문자열로 참조하므로 수정 대상에서 제외한다 */
    private void update(ZonSaveRequest row) {
        Zon zon = zonRepository.findById(row.getZonId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 존입니다: " + row.getZonId()));
        zon.update(row.getZonNm(), row.getTmpZon(), row.getStrgTyp(), row.getBizDvsn());
    }

    private void delete(ZonSaveRequest row) {
        Zon zon = zonRepository.findById(row.getZonId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 존입니다: " + row.getZonId()));
        // FK가 없어 DB가 막아주지 않는다 — 하위 로케이션 존재 여부를 여기서 직접 확인한다
        if (locRepository.existsByZonCd(zon.getZonCd())) {
            throw new IllegalArgumentException("하위 로케이션이 있는 존은 삭제할 수 없습니다: " + zon.getZonCd());
        }
        zonRepository.delete(zon);
    }

    private void validate(ZonSaveRequest row) {
        if (row.getZonCd() == null || row.getZonCd().isBlank()) {
            throw new IllegalArgumentException("존 코드는 필수입니다.");
        }
        if (row.getZonNm() == null || row.getZonNm().isBlank()) {
            throw new IllegalArgumentException("존 명은 필수입니다: " + row.getZonCd());
        }
        if (row.getTmpZon() == null) {
            throw new IllegalArgumentException("온도구분은 필수입니다: " + row.getZonCd());
        }
        if (row.getStrgTyp() == null) {
            throw new IllegalArgumentException("보관유형은 필수입니다: " + row.getZonCd());
        }
        if (row.getBizDvsn() == null) {
            throw new IllegalArgumentException("업무구분은 필수입니다: " + row.getZonCd());
        }
    }
}
