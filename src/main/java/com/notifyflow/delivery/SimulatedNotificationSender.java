package com.notifyflow.delivery;

import com.notifyflow.config.NotifyFlowProperties;
import com.notifyflow.entity.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Pretends to deliver a notification.
 * - A message containing "[fail]" ALWAYS fails, so you can test the retry limit on demand.
 * - Any other message fails randomly with probability notifyflow.simulated-failure-rate.
 */
@Component
public class SimulatedNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SimulatedNotificationSender.class);

    private final NotifyFlowProperties properties;

    public SimulatedNotificationSender(NotifyFlowProperties properties) {
        this.properties = properties;
    }

    @Override
    public void send(Notification notification) {
        if (notification.getMessage().contains("[fail]")) {
            throw new DeliveryFailedException("Simulated permanent failure ([fail] marker in message)");
        }
        if (ThreadLocalRandom.current().nextDouble() < properties.simulatedFailureRate()) {
            throw new DeliveryFailedException("Simulated temporary failure: provider unavailable");
        }
        log.info("Delivered notification {} via {} to {}",
                notification.getId(), notification.getChannel(), notification.getRecipient());
    }
}
