package com.project.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 스케줄링 활성화. 현재 스케줄 작업은 정기 보충(SpmtScheduler) 하나 — 주기는 각 작업의 프로퍼티가 갖는다 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
