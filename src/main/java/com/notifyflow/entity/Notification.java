package com.notifyflow.entity;

import com.notifyflow.enums.NotificationChannel;
import com.notifyflow.enums.NotificationStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "notifications", indexes = {
        // The scheduler looks up "PENDING and due" rows, so this index keeps that query fast.
        @Index(name = "idx_notifications_status_next_attempt", columnList = "status, next_attempt_at")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false, length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    /** Number of delivery attempts made so far (successful or not). */
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    /** Attempts allowed before the notification is marked FAILED. A manual retry grants more. */
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    /** The notification is not picked up by the scheduler before this time (retry back-off). */
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    /** Optional client-supplied key so that a repeated request does not create a duplicate. */
    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Deleting a notification also deletes its delivery history.
    @OneToMany(mappedBy = "notification", cascade = CascadeType.REMOVE)
    private List<NotificationAttempt> attempts = new ArrayList<>();

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public void setChannel(NotificationChannel channel) {
        this.channel = channel;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(LocalDateTime nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<NotificationAttempt> getAttempts() {
        return attempts;
    }
}
