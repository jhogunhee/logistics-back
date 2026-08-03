package com.project.mdm.vendor.controller;

import com.project.mdm.vendor.dto.VendorResponse;
import com.project.mdm.vendor.dto.VendorSaveRequest;
import com.project.mdm.vendor.dto.VendorSearchCond;
import com.project.mdm.vendor.service.VendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/master/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;

    @GetMapping
    public List<VendorResponse> list(@ModelAttribute VendorSearchCond cond) {
        return vendorService.list(cond);
    }

    @PostMapping("/bulk")
    public void saveAll(@RequestBody List<VendorSaveRequest> rows) {
        vendorService.saveAll(rows);
    }
}
