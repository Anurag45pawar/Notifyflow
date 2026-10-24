package com.notifyflow.dto;

import com.notifyflow.entity.Notification;
import com.notifyflow.enums.NotificationChannel;
import com.notifyflow.enums.NotificationStatus;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String recipient,
        String message,
        NotificationChannel channel,
        NotificationStatus status,
        int attemptCount,
        int maxAttempts,
        LocalDateTime nextAttemptAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getRecipient(),
                n.getMessage(),
                n.getChannel(),
                n.getStatus(),
                n.getAttemptCount(),
                n.getMaxAttempts(),
                n.getNextAttemptAt(),
                n.getCreatedAt(),
                n.getUpdatedAt());
    }
}
