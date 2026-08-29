package com.project.common.security;

import java.util.List;

/**
 * 메뉴 권한을 묻는 창구. 구현은 {@code mdm.mnu}에 있다 —
 * {@code common}은 DB를 보지 않는다는 규칙 때문에 인터페이스만 여기 둔다.
 */
public interface MnuAccessSource {

    /** 이 역할들 중 하나라도 이 경로를 관장하는 메뉴를 갖고 있나. 관장하는 메뉴가 없으면 true(통과) */
    boolean allows(List<String> roles, String path);
}
