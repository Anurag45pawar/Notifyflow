package com.notifyflow.repository;

import com.notifyflow.entity.Notification;
import com.notifyflow.enums.NotificationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByStatusOrderByCreatedAtDesc(NotificationStatus status);

    Optional<Notification> findByIdempotencyKey(String idempotencyKey);

    /** Notifications that are waiting and whose retry delay has passed - oldest first. */
    List<Notification> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            NotificationStatus status, LocalDateTime now, Pageable pageable);

    /**
     * Atomically moves ONE notification from currentStatus to newStatus.
     * The WHERE clause contains the current status, so if two workers try to claim the same
     * notification, the database lets only one of them succeed (returns 1); the other gets 0.
     * This is what prevents duplicate processing.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.status = :newStatus, n.updatedAt = :now "
            + "where n.id = :id and n.status = :currentStatus")
    int transitionStatus(@Param("id") Long id,
                         @Param("currentStatus") NotificationStatus currentStatus,
                         @Param("newStatus") NotificationStatus newStatus,
                         @Param("now") LocalDateTime now);

    /** Puts notifications that have been in currentStatus since before the cutoff back in the queue. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.status = :newStatus, n.nextAttemptAt = :now, n.updatedAt = :now "
            + "where n.status = :currentStatus and n.updatedAt < :cutoff")
    int resetStuck(@Param("currentStatus") NotificationStatus currentStatus,
                   @Param("newStatus") NotificationStatus newStatus,
                   @Param("now") LocalDateTime now,
                   @Param("cutoff") LocalDateTime cutoff);
}
