package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<Vendor, Long>, VendorRepositoryCustom {
}
