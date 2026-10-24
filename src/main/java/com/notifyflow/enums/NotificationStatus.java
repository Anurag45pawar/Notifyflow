package com.notifyflow.enums;

public enum NotificationStatus {
    /** Saved and waiting for the background worker (or waiting for its next retry). */
    PENDING,
    /** Claimed by a worker; delivery is in progress. */
    PROCESSING,
    /** Delivered successfully. */
    SENT,
    /** All permitted attempts were used without success. */
    FAILED
}
