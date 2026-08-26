package com.project.mdm.usr.repository;

import com.project.mdm.usr.entity.Role;
import com.project.mdm.usr.entity.Usr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsrRepository extends JpaRepository<Usr, Long>, UsrRepositoryCustom {

    Optional<Usr> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    /** 마지막 관리자를 지우거나 역할을 뺄 수 없게 하는 가드가 쓴다 */
    @Query("select count(u) from Usr u join u.roles r where r = :role")
    long countByRole(@Param("role") Role role);
}
