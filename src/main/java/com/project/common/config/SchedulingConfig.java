package com.project.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 활성화. 현재 스케줄 작업은 정기 보충(SpmtScheduler) · 자동발주(AtoOdrScheduler) 둘 —
 * 주기는 각 작업의 프로퍼티가 갖는다(spmt.cron · ato-odr.cron, "-"면 비활성).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
