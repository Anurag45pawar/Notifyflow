package com.notifyflow.dto;

import com.notifyflow.enums.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST and PUT requests. */
public record NotificationRequest(

        @NotBlank(message = "recipient is required")
        @Size(max = 255, message = "recipient must be at most 255 characters")
        String recipient,

        @NotBlank(message = "message is required")
        @Size(max = 2000, message = "message must be at most 2000 characters")
        String message,

        @NotNull(message = "channel is required (EMAIL, SMS or IN_APP)")
        NotificationChannel channel) {
}
