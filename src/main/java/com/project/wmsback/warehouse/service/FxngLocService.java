package com.project.wmsback.warehouse.service;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.wmsback.warehouse.dto.FxngLocResponse;
import com.project.wmsback.warehouse.dto.FxngLocSaveRequest;
import com.project.wmsback.warehouse.dto.FxngLocSearchCond;
import com.project.wmsback.warehouse.entity.FxngLoc;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.repository.FxngLocRepository;
import com.project.wmsback.warehouse.repository.LocRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FxngLocService {

    private final FxngLocRepository fxngLocRepository;
    private final ProdRepository prodRepository;
    private final LocRepository locRepository;

    public List<FxngLocResponse> list(FxngLocSearchCond cond) {
        return fxngLocRepository.search(cond).stream()
                .map(FxngLocResponse::from)
                .toList();
    }

    /** 신규(C)/수정(U)/삭제(D) 행 일괄 저장. 한 건이라도 실패하면 전체 롤백. */
    @Transactional
    public void saveAll(List<FxngLocSaveRequest> rows) {
        for (FxngLocSaveRequest row : rows) {
            switch (row.getStatus()) {
                case "C" -> create(row);
                case "U" -> update(row);
                case "D" -> delete(row);
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        // 제약 위반(로케이션 중복 등)을 커밋 시점이 아니라 여기서 터뜨려 예외 변환이 되게 한다
        fxngLocRepository.flush();
    }

    private void create(FxngLocSaveRequest row) {
        Loc loc = findLoc(row.getLocCd());
        FxngLoc fxngLoc = row.toEntity(findProd(row.getProdCd(), loc.getLocCd()), loc);
        assertLocFree(loc, null);
        fxngLocRepository.save(fxngLoc);
    }

    private void update(FxngLocSaveRequest row) {
        FxngLoc fxngLoc = find(row.getFxngLocId());
        Loc loc = findLoc(row.getLocCd());
        assertLocFree(loc, fxngLoc.getId());
        row.updateEntity(fxngLoc, findProd(row.getProdCd(), loc.getLocCd()), loc);
    }

    /** 물리삭제. 어떤 문서도 fxng_loc_id를 참조하지 않는 순수 설정 마스터라 가드 없이 지운다 */
    private void delete(FxngLocSaveRequest row) {
        fxngLocRepository.delete(find(row.getFxngLocId()));
    }

    /** uq_fxng_loc(한 로케이션 = 한 상품 전용)를 커밋 전에 사용자 메시지로 돌려준다 */
    private void assertLocFree(Loc loc, Long selfId) {
        fxngLocRepository.findByLoc(loc)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "이미 다른 상품(%s)이 고정된 로케이션입니다: %s"
                                    .formatted(existing.getProd().getProdCd(), loc.getLocCd()));
                });
    }

    private FxngLoc find(Long fxngLocId) {
        return fxngLocRepository.findById(fxngLocId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 고정 로케이션입니다: " + fxngLocId));
    }

    /** 행은 상품을 코드로 보낸다 — 필수 검사도 여기다 (빈 코드를 조회로 보내면 원인을 잘못 짚는다) */
    private Prod findProd(String prodCd, String locCd) {
        if (prodCd == null || prodCd.isBlank()) {
            throw new IllegalArgumentException("상품은 필수입니다: " + locCd);
        }
        return prodRepository.findByProdCd(prodCd)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다: " + prodCd));
    }

    private Loc findLoc(String locCd) {
        if (locCd == null || locCd.isBlank()) {
            throw new IllegalArgumentException("로케이션은 필수입니다.");
        }
        return locRepository.findByLocCd(locCd)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로케이션입니다: " + locCd));
    }
}
