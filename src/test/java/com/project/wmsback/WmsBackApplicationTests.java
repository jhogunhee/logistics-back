package com.project.wmsback;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 컨텍스트 로드 테스트. ddl-auto=validate라서 이 테스트가 통과하면
 * 전체 엔티티 매핑이 실제 스키마(docs/schema.sql)와 일치함이 보장된다.
 * 로컬 Oracle(XEPDB1)이 떠 있어야 한다.
 */
@SpringBootTest
class WmsBackApplicationTests {

    @Test
    void contextLoads() {
    }
}
