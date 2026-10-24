package com.notifyflow.delivery;

import com.notifyflow.entity.Notification;

/**
 * Delivers a notification to the outside world.
 * Today there is only a simulated implementation. To send real emails later, write another
 * implementation (for example one that uses JavaMailSender) - nothing else needs to change.
 */
public interface NotificationSender {

    /** @throws DeliveryFailedException if the notification could not be delivered */
    void send(Notification notification);
}
