package com.project.wmsback.warehouse.service;

import com.project.wmsback.warehouse.dto.LocResponse;
import com.project.wmsback.warehouse.dto.LocSaveRequest;
import com.project.wmsback.warehouse.dto.LocSearchCond;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.Zon;
import com.project.wmsback.inventory.repository.LocCapacityQueryRepository;
import com.project.wmsback.inventory.repository.LocRefQueryRepository;
import com.project.wmsback.warehouse.repository.LocRepository;
import com.project.wmsback.warehouse.repository.ZonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocService {

    private final LocRepository locRepository;
    private final ZonRepository zonRepository;
    private final LocCapacityQueryRepository locCapacityQueryRepository;
    private final LocRefQueryRepository locRefQueryRepository;

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
                case "C" -> create(row);
                case "U" -> update(row);
                case "D" -> delete(row);
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        // 제약 위반(코드 중복 등)을 커밋 시점이 아니라 여기서 터뜨려 예외 변환이 되게 한다
        locRepository.flush();
    }

    private void create(LocSaveRequest row) {
        Loc loc = row.toEntity(findZon(row.getZonCd(), row.getLocCd()));
        if (locRepository.existsByLocCd(loc.getLocCd())) {
            throw new IllegalArgumentException("이미 존재하는 로케이션 코드입니다: " + loc.getLocCd());
        }
        locRepository.save(loc);
    }

    private void update(LocSaveRequest row) {
        Loc loc = find(row.getLocId());
        boolean tmpZonOrTypChanges = loc.getTmpZon() != row.getTmpZon() || loc.getLocTyp() != row.getLocTyp();
        // 반영 뒤 저장될 상태로 검사한다 — 예외면 트랜잭션이 롤백되므로 필드 검사(updateEntity)가 먼저 걸리게 둔다
        row.updateEntity(loc, findZon(row.getZonCd(), loc.getLocCd()));
        // 재고가 놓인 로케이션의 온도대·유형을 바꾸면 이미 놓인 재고가 온도대 일치·할당 후보 판정의 전제를 깬다
        if (tmpZonOrTypChanges && locRefQueryRepository.existsInv(loc.getId())) {
            throw new IllegalArgumentException("재고가 있는 로케이션은 온도대·유형을 변경할 수 없습니다: " + loc.getLocCd());
        }
        // 사용량(현재고 + 미완료 지시 유입) 아래로 줄이면 적재가능수량이 음수가 되어 이동·적치 검증이 왜곡된다
        if (loc.getMaxQty() != null) {
            long used = locCapacityQueryRepository.onHandQty(loc.getId())
                    + locCapacityQueryRepository.openInflowQty(loc.getId());
            if (loc.getMaxQty() < used) {
                throw new IllegalArgumentException(
                        "최대 적재 수량은 현재 사용량(%d) 이상이어야 합니다: %s".formatted(used, loc.getLocCd()));
            }
        }
    }

    /**
     * 물리삭제. 재고·이력·작업지시가 참조 중이면 거부한다 — FK가 0건이라 DB가 막아주지 않아서
     * 그냥 지우면 재고가 없는 로케이션을 가리키게 되고 재고 조회·원장 검증이 로케이션 축을 잃는다.
     */
    private void delete(LocSaveRequest row) {
        Loc loc = find(row.getLocId());
        String usedBy = locRefQueryRepository.findAnyReference(loc.getId());
        if (usedBy != null) {
            throw new IllegalArgumentException(
                    "%s에서 사용 중이라 삭제할 수 없습니다: %s".formatted(usedBy, loc.getLocCd()));
        }
        locRepository.delete(loc);
    }

    private Loc find(Long locId) {
        return locRepository.findById(locId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로케이션입니다: " + locId));
    }

    /**
     * 행은 존을 코드로 보낸다 — 연관에 실을 존 엔티티를 찾는다 (loc.zon_id는 FK가 없어 여기서 존재를 검증한다).
     * 필수 검사도 여기다 — 빈 코드를 조회로 보내면 「존재하지 않는 존」이 나가 사용자가 원인을 잘못 짚는다.
     */
    private Zon findZon(String zonCd, String locCd) {
        if (zonCd == null || zonCd.isBlank()) {
            throw new IllegalArgumentException("존은 필수입니다: " + locCd);
        }
        return zonRepository.findByZonCd(zonCd)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 존입니다: " + zonCd));
    }
}
