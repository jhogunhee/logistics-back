package com.project.wmsback.master.service;

import com.project.wmsback.master.dto.LocResponse;
import com.project.wmsback.master.dto.LocSaveRequest;
import com.project.wmsback.master.dto.LocSearchCond;
import com.project.wmsback.master.entity.Loc;
import com.project.wmsback.master.entity.LocType;
import com.project.wmsback.master.repository.LocRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocService {

    private final LocRepository locRepository;

    public List<LocResponse> list(LocSearchCond cond) {
        return locRepository.search(cond).stream()
                .map(LocResponse::from)
                .toList();
    }

    /** 신규(C)/수정(U)/삭제(D) 행 일괄 저장. 한 건이라도 실패하면 전체 롤백. */
    @Transactional
    public void saveAll(List<LocSaveRequest> rows) {
        for (LocSaveRequest row : rows) {
            switch (row.getStatus()) {
                case "C" -> { validate(row); create(row); }
                case "U" -> { validate(row); update(row); }
                case "D" -> delete(row);
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        // FK 위반(재고가 놓인 로케이션 삭제 등)을 커밋 시점이 아니라 여기서 터뜨려 예외 변환이 되게 한다
        locRepository.flush();
    }

    private void create(LocSaveRequest row) {
        if (locRepository.existsByLocCd(row.getLocCd())) {
            throw new IllegalArgumentException("이미 존재하는 로케이션 코드입니다: " + row.getLocCd());
        }
        locRepository.save(Loc.builder()
                .locCd(row.getLocCd())
                .zonCd(row.getZonCd())
                .tmpZon(row.getTmpZon())
                .locTyp(row.getLocTyp())
                .pikngPrty(row.getPikngPrty())
                .build());
    }

    private void update(LocSaveRequest row) {
        Loc loc = locRepository.findById(row.getLocId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로케이션입니다: " + row.getLocId()));
        loc.update(row.getZonCd(), row.getTmpZon(), row.getLocTyp(),
                row.getPikngPrty() != null ? row.getPikngPrty() : 0);
    }

    private void delete(LocSaveRequest row) {
        Loc loc = locRepository.findById(row.getLocId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로케이션입니다: " + row.getLocId()));
        locRepository.delete(loc);
    }

    private void validate(LocSaveRequest row) {
        if (row.getLocCd() == null || row.getLocCd().isBlank()) {
            throw new IllegalArgumentException("로케이션 코드는 필수입니다.");
        }
        if (row.getZonCd() == null || row.getZonCd().isBlank()) {
            throw new IllegalArgumentException("존은 필수입니다: " + row.getLocCd());
        }
        if (row.getTmpZon() == null) {
            throw new IllegalArgumentException("온도대는 필수입니다: " + row.getLocCd());
        }
        if (row.getLocTyp() == null) {
            throw new IllegalArgumentException("유형은 필수입니다: " + row.getLocCd());
        }
        // 보관 로케이션은 존=온도대여야 적치·이동 시 온도대 일치 검증이 성립한다
        if (row.getLocTyp() == LocType.STORAGE && !row.getZonCd().equals(row.getTmpZon().name())) {
            throw new IllegalArgumentException("보관 로케이션은 존과 온도대가 일치해야 합니다: " + row.getLocCd());
        }
        if (row.getPikngPrty() != null && row.getPikngPrty() < 0) {
            throw new IllegalArgumentException("피킹 우선순위는 0 이상이어야 합니다: " + row.getLocCd());
        }
    }
}
