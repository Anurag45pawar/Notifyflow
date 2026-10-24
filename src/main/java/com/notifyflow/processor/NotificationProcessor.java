package com.notifyflow.processor;

import com.notifyflow.delivery.NotificationSender;
import com.notifyflow.entity.Notification;
import com.notifyflow.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Processes ONE notification: claim it, deliver it, record the outcome.
 *
 * The delivery call happens outside any database transaction on purpose - a slow email provider
 * must not keep a database connection and locks open. Each step is its own short transaction.
 */
@Component
public class NotificationProcessor {

    private static final Logger log = LoggerFactory.getLogger(NotificationProcessor.class);

    private final NotificationService notificationService;
    private final NotificationSender sender;

    public NotificationProcessor(NotificationService notificationService, NotificationSender sender) {
        this.notificationService = notificationService;
        this.sender = sender;
    }

    public void process(Long id) {
        // 1. Claim (PENDING -> PROCESSING). Only one worker can win this.
        Optional<Notification> claimed = notificationService.claimForProcessing(id);
        if (claimed.isEmpty()) {
            log.debug("Notification {} was already claimed or is no longer PENDING - skipping", id);
            return;
        }

        // 2. Deliver (no transaction open here).
        try {
            sender.send(claimed.get());
        } catch (Exception ex) {
            String reason = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            log.warn("Delivery of notification {} failed: {}", id, reason);
            notificationService.recordFailure(id, reason);
            return;
        }

        // 3. Record success (SENT).
        notificationService.recordSuccess(id);
    }
}
