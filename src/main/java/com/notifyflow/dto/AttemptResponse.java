package com.notifyflow.dto;

import com.notifyflow.entity.NotificationAttempt;
import com.notifyflow.enums.AttemptStatus;

import java.time.LocalDateTime;

public record AttemptResponse(
        Long id,
        int attemptNumber,
        AttemptStatus status,
        String errorMessage,
        LocalDateTime attemptedAt) {

    public static AttemptResponse from(NotificationAttempt a) {
        return new AttemptResponse(
                a.getId(),
                a.getAttemptNumber(),
                a.getStatus(),
                a.getErrorMessage(),
                a.getAttemptedAt());
    }
}
