package com.project.wmsback.warehouse.service;

import com.project.wmsback.warehouse.dto.LocResponse;
import com.project.wmsback.warehouse.dto.LocSaveRequest;
import com.project.wmsback.warehouse.dto.LocSearchCond;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Zon;
import com.project.wmsback.inventory.repository.LocCapacityQueryRepository;
import com.project.wmsback.warehouse.repository.LocRepository;
import com.project.wmsback.warehouse.repository.ZonRepository;
import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.project.wmsback.inbound.entity.QPutawayTask.putawayTask;
import static com.project.wmsback.inventory.entity.QInv.inv;
import static com.project.wmsback.inventory.entity.QInvHist.invHist;
import static com.project.wmsback.inventory.entity.QInvMovTask.invMovTask;
import static com.project.wmsback.inventory.entity.QInvStktkLn.invStktkLn;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocService {

    private final LocRepository locRepository;
    private final ZonRepository zonRepository;
    private final LocCapacityQueryRepository locCapacityQueryRepository;
    private final JPAQueryFactory queryFactory;

    public List<LocResponse> list(LocSearchCond cond) {
        return locRepository.search(cond).stream()
                .map(LocResponse::from)
                .toList();
    }

    /** 신규(C)/수정(U)/삭제(D) 행 일괄 저장. 한 건이라도 실패하면 전체 롤백. */
    @Transactional
    public void saveAll(List<LocSaveRequest> rows) {
        // 존 마스터는 작고 엑셀 업로드는 수백 행을 한 번에 보내므로, 행마다 조회하지 않고 한 번만 읽어 쓴다
        Map<String, Zon> zonMap = zonRepository.findAll().stream()
                .collect(Collectors.toMap(Zon::getZonCd, Function.identity()));

        for (LocSaveRequest row : rows) {
            switch (row.getStatus()) {
                case "C" -> { validate(row, zonMap); create(row); }
                case "U" -> { validate(row, zonMap); update(row); }
                case "D" -> delete(row);
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        // 제약 위반(코드 중복 등)을 커밋 시점이 아니라 여기서 터뜨려 예외 변환이 되게 한다
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
                .ptawyPrty(row.getPtawyPrty())
                .maxQty(row.getMaxQty())
                .build());
    }

    private void update(LocSaveRequest row) {
        Loc loc = locRepository.findById(row.getLocId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로케이션입니다: " + row.getLocId()));
        // 재고가 놓인 로케이션의 온도대·유형을 바꾸면 이미 놓인 재고가 온도대 일치·할당 후보 판정의 전제를 깬다
        if ((loc.getTmpZon() != row.getTmpZon() || loc.getLocTyp() != row.getLocTyp())
                && exists(inv.loc.id.eq(loc.getId()), inv)) {
            throw new IllegalArgumentException("재고가 있는 로케이션은 온도대·유형을 변경할 수 없습니다: " + loc.getLocCd());
        }
        // 사용량(현재고 + 미완료 지시 유입) 아래로 줄이면 적재가능수량이 음수가 되어 이동·적치 검증이 왜곡된다
        if (row.getMaxQty() != null) {
            long used = locCapacityQueryRepository.onHandQty(loc.getId())
                    + locCapacityQueryRepository.openInflowQty(loc.getId());
            if (row.getMaxQty() < used) {
                throw new IllegalArgumentException(
                        "최대 적재 수량은 현재 사용량(%d) 이상이어야 합니다: %s".formatted(used, loc.getLocCd()));
            }
        }
        // null→0 기본값 처리는 빌더와 함께 엔티티(update)가 맡는다 — 두 경로가 갈라지지 않게
        loc.update(row.getZonCd(), row.getTmpZon(), row.getLocTyp(),
                row.getPikngPrty(), row.getPtawyPrty(), row.getMaxQty());
    }

    /**
     * 물리삭제. 재고·이력·작업지시가 참조 중이면 거부한다 — FK가 0건이라 DB가 막아주지 않아서
     * 그냥 지우면 재고가 없는 로케이션을 가리키게 되고 재고 조회·원장 검증이 로케이션 축을 잃는다.
     */
    private void delete(LocSaveRequest row) {
        Loc loc = locRepository.findById(row.getLocId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로케이션입니다: " + row.getLocId()));
        String usedBy = findAnyReference(loc.getId());
        if (usedBy != null) {
            throw new IllegalArgumentException(
                    "%s에서 사용 중이라 삭제할 수 없습니다: %s".formatted(usedBy, loc.getLocCd()));
        }
        locRepository.delete(loc);
    }

    /** 첫 참조에서 멈춘다. 순서는 사용자가 납득하기 쉬운 쪽부터다 (재고 → 이력 → 지시 → 실사) */
    private String findAnyReference(Long locId) {
        if (exists(inv.loc.id.eq(locId), inv)) return "재고";
        if (exists(invHist.loc.id.eq(locId), invHist)) return "재고 이력";
        if (exists(invMovTask.fromLoc.id.eq(locId).or(invMovTask.toLoc.id.eq(locId)), invMovTask)) return "이동지시";
        if (exists(putawayTask.toLoc.id.eq(locId), putawayTask)) return "적치지시";
        if (exists(invStktkLn.loc.id.eq(locId), invStktkLn)) return "재고실사";
        return null;
    }

    private boolean exists(BooleanExpression where, EntityPath<?> from) {
        return queryFactory.selectOne().from(from).where(where).fetchFirst() != null;
    }

    private void validate(LocSaveRequest row, Map<String, Zon> zonMap) {
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
        // 모든 로케이션은 존 마스터에 등록된 존에 속해야 한다 (loc.zon_cd는 FK가 없어 DB가 막지 않는다)
        Zon zon = zonMap.get(row.getZonCd());
        if (zon == null) {
            throw new IllegalArgumentException("존재하지 않는 존입니다: " + row.getZonCd());
        }
        // 보관 로케이션은 존과 온도대가 같아야 적치·이동 시 온도대 일치 검증이 성립한다
        // (스테이징은 전 온도대 재고가 거쳐 가는 지점이라 예외)
        if (row.getLocTyp() == LocTyp.STORAGE && zon.getTmpZon() != row.getTmpZon()) {
            throw new IllegalArgumentException("보관 로케이션의 온도대는 존의 온도대와 같아야 합니다: " + row.getLocCd());
        }
        // DB 제약(ck_loc_storage_capacity · ck_loc_max_qty)을 커밋 전에 사용자 메시지로 돌려준다
        if (row.getLocTyp() == LocTyp.STORAGE && row.getMaxQty() == null) {
            throw new IllegalArgumentException("보관 로케이션은 최대 적재 수량이 필수입니다: " + row.getLocCd());
        }
        if (row.getMaxQty() != null && row.getMaxQty() < 1) {
            throw new IllegalArgumentException("최대 적재 수량은 1 이상이어야 합니다: " + row.getLocCd());
        }
        if (row.getPikngPrty() != null && row.getPikngPrty() < 0) {
            throw new IllegalArgumentException("피킹 우선순위는 0 이상이어야 합니다: " + row.getLocCd());
        }
        if (row.getPtawyPrty() != null && row.getPtawyPrty() < 0) {
            throw new IllegalArgumentException("적치 우선순위는 0 이상이어야 합니다: " + row.getLocCd());
        }
    }
}
