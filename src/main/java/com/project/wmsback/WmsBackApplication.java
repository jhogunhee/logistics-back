package com.project.wmsback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 부트스트랩. 스캔 3종(컴포넌트/엔티티/리포지터리)의 기본 기준점은 이 클래스의 패키지라서,
 * 그대로 두면 형제 패키지인 com.project.omsback이 통째로 빠진다
 * (빈 미등록 · 엔티티 매핑 누락 · 리포지터리 프록시 미생성).
 * 공통 상위인 com.project로 넓혀 omsback/wmsback 둘 다 잡는다.
 */
@SpringBootApplication(scanBasePackages = "com.project")
@EntityScan("com.project")
@EnableJpaRepositories("com.project")
public class WmsBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(WmsBackApplication.class, args);
    }
}
