package com.project.mdm.mnu.repository;

import com.project.mdm.mnu.entity.Mnu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MnuRepository extends JpaRepository<Mnu, String> {

    List<Mnu> findAllByOrderByGrpNmAscSrtSeqAsc();

    // api_prfx에는 중복 검사가 없다 — 같은 API를 나눠 쓰는 화면이 여럿인 것이 정상이다
    boolean existsByScrnPth(String scrnPth);
}
