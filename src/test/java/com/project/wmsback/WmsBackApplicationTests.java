package com.project.wmsback;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 컨텍스트 로드 테스트 — 전체 빈 배선과 엔티티 매핑이 실제 DB와 맞물리는지 본다.
 * 실제 PostgreSQL 접속이 필요하므로 {@code DB_URL}이 설정된 환경에서만 돈다 — 없는 환경(단위 테스트만
 * 돌리는 로컬·CI)에서는 건너뛴다. 접속 불가를 실패로 세면 다음 회귀가 빨간 배경에 묻힌다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class WmsBackApplicationTests {

    @Test
    void contextLoads() {
    }
}
