package com.notifyflow.controller;

import com.notifyflow.dto.AttemptResponse;
import com.notifyflow.dto.NotificationRequest;
import com.notifyflow.dto.NotificationResponse;
import com.notifyflow.enums.NotificationStatus;
import com.notifyflow.exception.InvalidRequestException;
import com.notifyflow.service.NotificationService;
import com.notifyflow.service.NotificationService.CreationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/** HTTP layer only: reads the request, calls the service, returns the right status code. */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "Create a notification",
            description = "Saves the notification as PENDING. Send an optional Idempotency-Key header "
                    + "to make repeated requests safe: the same key returns the same notification (200).")
    @PostMapping
    public ResponseEntity<NotificationResponse> create(
            @Valid @RequestBody NotificationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        CreationResult result = notificationService.create(request, idempotencyKey);
        if (!result.created()) {
            return ResponseEntity.ok(result.notification());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(result.notification().id())
                .toUri();
        return ResponseEntity.created(location).body(result.notification());
    }

    @Operation(summary = "Get all notifications (newest first)")
    @GetMapping
    public List<NotificationResponse> getAll() {
        return notificationService.getAll();
    }

    @Operation(summary = "Get one notification")
    @GetMapping("/{id}")
    public NotificationResponse getById(@PathVariable Long id) {
        return notificationService.getById(id);
    }

    @Operation(summary = "Filter by status (PENDING, PROCESSING, SENT, FAILED)")
    @GetMapping("/status/{status}")
    public List<NotificationResponse> getByStatus(@PathVariable String status) {
        return notificationService.getByStatus(parseStatus(status));
    }

    @Operation(summary = "Update a notification (only while it is PENDING)")
    @PutMapping("/{id}")
    public NotificationResponse update(@PathVariable Long id, @Valid @RequestBody NotificationRequest request) {
        return notificationService.update(id, request);
    }

    @Operation(summary = "Delete a notification and its delivery history (not while PROCESSING)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        notificationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Retry a FAILED notification",
            description = "Moves it back to PENDING and grants another round of delivery attempts.")
    @PostMapping("/{id}/retry")
    public NotificationResponse retry(@PathVariable Long id) {
        return notificationService.retry(id);
    }

    @Operation(summary = "Delivery history: every attempt made for this notification")
    @GetMapping("/{id}/attempts")
    public List<AttemptResponse> getAttempts(@PathVariable Long id) {
        return notificationService.getAttempts(id);
    }

    private NotificationStatus parseStatus(String value) {
        try {
            return NotificationStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("Invalid status '" + value
                    + "'. Allowed values: PENDING, PROCESSING, SENT, FAILED");
        }
    }
}
