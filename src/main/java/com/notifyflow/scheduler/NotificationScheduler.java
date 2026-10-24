package com.notifyflow.scheduler;

import com.notifyflow.processor.NotificationProcessor;
import com.notifyflow.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The background worker. Every few seconds it looks for due notifications and processes them.
 * fixedDelay means: wait N ms after the previous run FINISHED, so runs never overlap.
 */
@Component
@ConditionalOnProperty(name = "notifyflow.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final NotificationService notificationService;
    private final NotificationProcessor processor;

    public NotificationScheduler(NotificationService notificationService, NotificationProcessor processor) {
        this.notificationService = notificationService;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${notifyflow.scheduler.delay-ms:5000}")
    public void processDueNotifications() {
        notificationService.recoverStuckNotifications();

        List<Long> dueIds = notificationService.findDueNotificationIds();
        if (!dueIds.isEmpty()) {
            log.info("Scheduler found {} notification(s) to process", dueIds.size());
        }
        for (Long id : dueIds) {
            try {
                processor.process(id);
            } catch (Exception ex) {
                // One bad notification must never stop the others from being processed.
                log.error("Unexpected error while processing notification {}", id, ex);
            }
        }
    }
}
