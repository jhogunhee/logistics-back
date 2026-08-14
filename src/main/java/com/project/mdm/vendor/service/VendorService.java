package com.project.mdm.vendor.service;

import com.project.mdm.nbr.service.NbrService;
import com.project.mdm.vendor.dto.VendorResponse;
import com.project.mdm.vendor.dto.VendorSaveRequest;
import com.project.mdm.vendor.dto.VendorSearchCond;
import com.project.mdm.vendor.entity.Vendor;
import com.project.mdm.vendor.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VendorService {

    private final VendorRepository vendorRepository;
    private final NbrService nbrService;
    /** 벤더를 참조하는 앱들의 신고 창구. @Order 순으로 주입된다 (WMS → OMS) */
    private final List<VendorRefChecker> vendorRefCheckers;

    public List<VendorResponse> list(VendorSearchCond cond) {
        return vendorRepository.search(cond).stream()
                .map(VendorResponse::from)
                .toList();
    }

    /** 신규(C)/수정(U)/삭제(D) 행 일괄 저장. 한 건이라도 실패하면 전체 롤백. */
    @Transactional
    public void saveAll(List<VendorSaveRequest> rows) {
        for (VendorSaveRequest row : rows) {
            switch (row.getStatus()) {
                case "C" -> { validate(row); create(row); }
                case "U" -> { validate(row); update(row); }
                case "D" -> delete(row);
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        // 제약 위반(코드 중복 등)을 커밋 시점이 아니라 여기서 터뜨려 예외 변환이 되게 한다
        vendorRepository.flush();
    }

    private void create(VendorSaveRequest row) {
        // 클라이언트가 보낸 코드는 받지 않는다 — 채번 규칙 VNDR_CD로 발급 (VD-0001 형식)
        String vndrCd = nbrService.issue("VNDR_CD");
        vendorRepository.save(Vendor.builder()
                .vndrCd(vndrCd)
                .vndrNm(row.getVndrNm())
                .picNm(row.getPicNm())
                .telNo(row.getTelNo())
                .build());
    }

    private void update(VendorSaveRequest row) {
        Vendor vendor = vendorRepository.findById(row.getVendorId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 벤더입니다: " + row.getVendorId()));
        vendor.update(row.getVndrNm(), row.getPicNm(), row.getTelNo());
    }

    /**
     * 물리삭제. 입고 문서가 참조 중이면 거부한다 — FK가 0건이라 DB가 막아주지 않아서
     * 그냥 지우면 주문이 없는 벤더를 가리키게 된다.
     * <p>
     * 참조 검사는 {@link VendorRefChecker} 구현체가 한다 — mdm은 자기 데이터를 누가 쓰는지 모른다.
     */
    private void delete(VendorSaveRequest row) {
        Vendor vendor = vendorRepository.findById(row.getVendorId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 벤더입니다: " + row.getVendorId()));
        String usedBy = findAnyReference(vendor.getId());
        if (usedBy != null) {
            throw new IllegalArgumentException(
                    "%s에서 사용 중이라 삭제할 수 없습니다: %s".formatted(usedBy, vendor.getVndrNm()));
        }
        vendorRepository.delete(vendor);
    }

    /** 첫 참조에서 멈춘다 — 어느 앱이 먼저 걸리든 삭제가 막히는 결과는 같다 */
    private String findAnyReference(Long vendorId) {
        for (VendorRefChecker checker : vendorRefCheckers) {
            String usedBy = checker.findReference(vendorId);
            if (usedBy != null) return usedBy;
        }
        return null;
    }

    private void validate(VendorSaveRequest row) {
        if (row.getVndrNm() == null || row.getVndrNm().isBlank()) {
            throw new IllegalArgumentException("벤더명은 필수입니다.");
        }
    }
}
