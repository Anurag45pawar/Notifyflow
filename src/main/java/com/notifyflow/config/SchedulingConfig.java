package com.notifyflow.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled support. It can be disabled with notifyflow.scheduler.enabled=false
 * (the integration tests do this so they control processing themselves).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "notifyflow.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
