package com.project.wmsback.warehouse.service;

import com.project.wmsback.warehouse.dto.ZonResponse;
import com.project.wmsback.warehouse.dto.ZonSaveRequest;
import com.project.wmsback.warehouse.dto.ZonSearchCond;
import com.project.wmsback.warehouse.entity.LocTyp;
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
                case "C" -> create(row);
                case "U" -> update(row);
                case "D" -> delete(row);
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        zonRepository.flush();
    }

    private void create(ZonSaveRequest row) {
        Zon zon = row.toEntity();
        if (zonRepository.existsByZonCd(zon.getZonCd())) {
            throw new IllegalArgumentException("이미 존재하는 존 코드입니다: " + zon.getZonCd());
        }
        zonRepository.save(zon);
    }

    private void update(ZonSaveRequest row) {
        Zon zon = find(row.getZonId());
        boolean tmpZonChanges = zon.getTmpZon() != row.getTmpZon();
        row.updateEntity(zon);
        // 보관 로케이션은 존과 온도대가 같아야 한다 — 하위 보관 로케이션을 둔 채 존 온도대만 바꾸면
        // 그 로케이션들이 전부 불일치가 되어 이후 수정 저장이 막힌다 (LocService의 온도대 일치 검증).
        // 반영 뒤에 검사해도 예외면 트랜잭션이 롤백되므로 필드 검사(updateEntity)가 먼저 걸리게 둔다.
        if (tmpZonChanges && locRepository.existsByZonCdAndLocTyp(zon.getZonCd(), LocTyp.STORAGE)) {
            throw new IllegalArgumentException(
                    "하위 보관 로케이션이 있는 존은 온도구분을 변경할 수 없습니다: " + zon.getZonCd());
        }
    }

    private void delete(ZonSaveRequest row) {
        Zon zon = find(row.getZonId());
        // FK가 없어 DB가 막아주지 않는다 — 하위 로케이션 존재 여부를 여기서 직접 확인한다
        if (locRepository.existsByZonCd(zon.getZonCd())) {
            throw new IllegalArgumentException("하위 로케이션이 있는 존은 삭제할 수 없습니다: " + zon.getZonCd());
        }
        zonRepository.delete(zon);
    }

    private Zon find(Long zonId) {
        return zonRepository.findById(zonId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 존입니다: " + zonId));
    }
}
