package com.project.mdm.store.service;

import com.project.mdm.store.dto.StoreResponse;
import com.project.mdm.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 점포(납품처) 조회.
 *
 * <p>조회만 있다. 점포 관리 화면이 아직 없어 등록·수정 경로를 만들 이유가 없고, 지금 필요한 것은
 * 출고주문의 납품처 선택 팝업이 쓸 목록 하나다 — 화면이 생길 때 벤더와 같은 형태(일괄 저장)로
 * 늘리면 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    private final StoreRepository storeRepository;

    /** 전체 목록 (점포코드 순). 마스터라 건수가 적어 페이징 없이 내려주고 검색은 화면이 건다 */
    public List<StoreResponse> list() {
        return storeRepository.findAllByOrderByStoreCdAsc().stream()
                .map(StoreResponse::from)
                .toList();
    }
}
