package com.notifyflow.entity;

import com.notifyflow.enums.AttemptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** One row per delivery attempt - this is the delivery history of a notification. */
@Entity
@Table(name = "notification_attempts")
public class NotificationAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttemptStatus status;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    protected NotificationAttempt() {
        // required by JPA
    }

    public NotificationAttempt(Notification notification, int attemptNumber,
                               AttemptStatus status, String errorMessage) {
        this.notification = notification;
        this.attemptNumber = attemptNumber;
        this.status = status;
        this.errorMessage = errorMessage;
        this.attemptedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Notification getNotification() {
        return notification;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public AttemptStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getAttemptedAt() {
        return attemptedAt;
    }
}
