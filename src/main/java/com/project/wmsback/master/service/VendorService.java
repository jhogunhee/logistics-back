package com.project.wmsback.master.service;

import com.project.wmsback.master.dto.VendorResponse;
import com.project.wmsback.master.dto.VendorSaveRequest;
import com.project.wmsback.master.dto.VendorSearchCond;
import com.project.wmsback.master.entity.Vendor;
import com.project.wmsback.master.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VendorService {

    private final VendorRepository vendorRepository;

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
        // FK 위반(주문이 참조 중인 벤더 삭제 등)을 커밋 시점이 아니라 여기서 터뜨려 예외 변환이 되게 한다
        vendorRepository.flush();
    }

    private void create(VendorSaveRequest row) {
        // 클라이언트가 보낸 코드는 받지 않는다 — 시퀀스로 채번 (VD-0001 형식)
        String vndrCd = String.format("VD-%04d", vendorRepository.nextVndrCdSeq());
        vendorRepository.save(Vendor.builder()
                .vndrCd(vndrCd)
                .vndrNm(row.getVndrNm())
                .mgrNm(row.getMgrNm())
                .telNo(row.getTelNo())
                .useYn(row.getUseYn())
                .build());
    }

    private void update(VendorSaveRequest row) {
        Vendor vendor = vendorRepository.findById(row.getVendorId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 벤더입니다: " + row.getVendorId()));
        vendor.update(row.getVndrNm(), row.getMgrNm(), row.getTelNo(), row.getUseYn());
    }

    private void delete(VendorSaveRequest row) {
        Vendor vendor = vendorRepository.findById(row.getVendorId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 벤더입니다: " + row.getVendorId()));
        vendorRepository.delete(vendor);
    }

    private void validate(VendorSaveRequest row) {
        if (row.getVndrNm() == null || row.getVndrNm().isBlank()) {
            throw new IllegalArgumentException("벤더명은 필수입니다.");
        }
        if (row.getUseYn() != null && !row.getUseYn().equals("Y") && !row.getUseYn().equals("N")) {
            throw new IllegalArgumentException("사용여부는 Y 또는 N이어야 합니다: " + row.getVndrNm());
        }
    }
}
