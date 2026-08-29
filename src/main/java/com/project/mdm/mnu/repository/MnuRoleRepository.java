package com.project.mdm.mnu.repository;

import com.project.mdm.mnu.entity.MnuRole;
import com.project.mdm.mnu.entity.MnuRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MnuRoleRepository extends JpaRepository<MnuRole, MnuRoleId> {

    List<MnuRole> findAllByMnuCdIn(Collection<String> mnuCds);

    void deleteByMnuCdIn(Collection<String> mnuCds);
}
