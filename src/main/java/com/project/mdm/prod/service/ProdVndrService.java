package com.project.mdm.prod.service;

import com.project.mdm.prod.dto.ProdVndrResponse;
import com.project.mdm.prod.dto.ProdVndrSaveRequest;
import com.project.mdm.prod.dto.ProdVndrSearchCond;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.ProdVndr;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.mdm.prod.repository.ProdVndrRepository;
import com.project.mdm.vendor.entity.Vendor;
import com.project.mdm.vendor.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProdVndrService {

    private final ProdVndrRepository prodVndrRepository;
    private final ProdRepository prodRepository;
    private final VendorRepository vendorRepository;

    public List<ProdVndrResponse> list(ProdVndrSearchCond cond) {
        return prodVndrRepository.search(cond).stream()
                .map(ProdVndrResponse::from)
                .toList();
    }

    /** 신규(C)/수정(U)/삭제(D) 행 일괄 저장. 한 건이라도 실패하면 전체 롤백. */
    @Transactional
    public void saveAll(List<ProdVndrSaveRequest> rows) {
        for (ProdVndrSaveRequest row : rows) {
            switch (row.getStatus()) {
                case "C" -> create(row);
                case "U" -> update(row);
                case "D" -> delete(row);
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        // 제약 위반(짝 중복 등)을 커밋 시점이 아니라 여기서 터뜨려 예외 변환이 되게 한다
        prodVndrRepository.flush();
    }

    private void create(ProdVndrSaveRequest row) {
        Prod prod = findProd(row.getProdCd());
        Vendor vendor = findVendor(row.getVndrCd(), prod.getProdCd());
        assertPairFree(prod, vendor, null);
        prodVndrRepository.save(row.toEntity(prod, vendor));
    }

    private void update(ProdVndrSaveRequest row) {
        ProdVndr prodVndr = find(row.getProdVndrId());
        Prod prod = findProd(row.getProdCd());
        Vendor vendor = findVendor(row.getVndrCd(), prod.getProdCd());
        assertPairFree(prod, vendor, prodVndr.getId());
        row.updateEntity(prodVndr, prod, vendor);
    }

    /** 물리삭제. 어떤 문서도 prod_vndr_id를 참조하지 않는 순수 설정 마스터라 가드 없이 지운다 */
    private void delete(ProdVndrSaveRequest row) {
        prodVndrRepository.delete(find(row.getProdVndrId()));
    }

    /** uq_prod_vndr(상품 하나에 같은 벤더 두 번 금지)를 커밋 전에 사용자 메시지로 돌려준다 */
    private void assertPairFree(Prod prod, Vendor vendor, Long selfId) {
        prodVndrRepository.findByProdAndVendor(prod, vendor)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "이미 등록된 상품·거래처입니다: %s / %s"
                                    .formatted(prod.getProdCd(), vendor.getVndrCd()));
                });
    }

    private ProdVndr find(Long prodVndrId) {
        return prodVndrRepository.findById(prodVndrId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품 거래처입니다: " + prodVndrId));
    }

    /** 행은 상품·벤더를 코드로 보낸다 — 필수 검사도 여기다 (빈 코드를 조회로 보내면 원인을 잘못 짚는다) */
    private Prod findProd(String prodCd) {
        if (prodCd == null || prodCd.isBlank()) {
            throw new IllegalArgumentException("상품은 필수입니다.");
        }
        return prodRepository.findByProdCd(prodCd)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다: " + prodCd));
    }

    private Vendor findVendor(String vndrCd, String prodCd) {
        if (vndrCd == null || vndrCd.isBlank()) {
            throw new IllegalArgumentException("거래처는 필수입니다: " + prodCd);
        }
        return vendorRepository.findByVndrCd(vndrCd)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 거래처입니다: " + vndrCd));
    }
}
