package com.notifyflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Type-safe settings read from the "notifyflow.*" keys in application.properties.
 */
@ConfigurationProperties(prefix = "notifyflow")
public record NotifyFlowProperties(
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("10") long retryDelaySeconds,
        @DefaultValue("20") int batchSize,
        @DefaultValue("60") long processingTimeoutSeconds,
        @DefaultValue("0.3") double simulatedFailureRate) {
}
