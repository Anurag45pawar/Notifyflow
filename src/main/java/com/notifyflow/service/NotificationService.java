package com.notifyflow.service;

import com.notifyflow.config.NotifyFlowProperties;
import com.notifyflow.dto.AttemptResponse;
import com.notifyflow.dto.NotificationRequest;
import com.notifyflow.dto.NotificationResponse;
import com.notifyflow.entity.Notification;
import com.notifyflow.entity.NotificationAttempt;
import com.notifyflow.enums.AttemptStatus;
import com.notifyflow.enums.NotificationChannel;
import com.notifyflow.enums.NotificationStatus;
import com.notifyflow.exception.InvalidNotificationStateException;
import com.notifyflow.exception.InvalidRequestException;
import com.notifyflow.exception.NotificationNotFoundException;
import com.notifyflow.repository.NotificationAttemptRepository;
import com.notifyflow.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * All business rules live here. The controller only handles HTTP; the repositories only handle SQL.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{10,15}$");
    private static final int MAX_ERROR_LENGTH = 1000;

    private final NotificationRepository notificationRepository;
    private final NotificationAttemptRepository attemptRepository;
    private final NotifyFlowProperties properties;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationAttemptRepository attemptRepository,
                               NotifyFlowProperties properties) {
        this.notificationRepository = notificationRepository;
        this.attemptRepository = attemptRepository;
        this.properties = properties;
    }

    /** Result of a create call: the notification and whether a new row was really created. */
    public record CreationResult(NotificationResponse notification, boolean created) {
    }

    // ------------------------------------------------------------------
    // API operations
    // ------------------------------------------------------------------

    /**
     * Creates a notification with status PENDING.
     * If an idempotency key is supplied and a notification with that key already exists, the
     * existing one is returned instead of creating a duplicate.
     *
     * Deliberately NOT @Transactional: if two identical requests race, the database unique
     * constraint rejects the second insert. Without an outer transaction we can catch that
     * exception and simply return the notification the first request saved.
     */
    public CreationResult create(NotificationRequest request, String idempotencyKey) {
        validateRecipient(request.channel(), request.recipient());
        String key = normalizeKey(idempotencyKey);

        if (key != null) {
            Optional<Notification> existing = notificationRepository.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                return new CreationResult(NotificationResponse.from(existing.get()), false);
            }
        }

        Notification notification = new Notification();
        notification.setRecipient(request.recipient().trim());
        notification.setMessage(request.message().trim());
        notification.setChannel(request.channel());
        notification.setStatus(NotificationStatus.PENDING);
        notification.setAttemptCount(0);
        notification.setMaxAttempts(properties.maxAttempts());
        notification.setNextAttemptAt(LocalDateTime.now());
        notification.setIdempotencyKey(key);

        try {
            Notification saved = notificationRepository.saveAndFlush(notification);
            return new CreationResult(NotificationResponse.from(saved), true);
        } catch (DataIntegrityViolationException ex) {
            if (key != null) {
                return notificationRepository.findByIdempotencyKey(key)
                        .map(existing -> new CreationResult(NotificationResponse.from(existing), false))
                        .orElseThrow(() -> ex);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getAll() {
        return notificationRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationResponse getById(Long id) {
        return NotificationResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getByStatus(NotificationStatus status) {
        return notificationRepository.findByStatusOrderByCreatedAtDesc(status)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AttemptResponse> getAttempts(Long id) {
        if (!notificationRepository.existsById(id)) {
            throw new NotificationNotFoundException(id);
        }
        return attemptRepository.findByNotificationIdOrderByAttemptNumberAsc(id)
                .stream()
                .map(AttemptResponse::from)
                .toList();
    }

    /** Only a PENDING notification may be edited - once processing starts, its content is fixed. */
    @Transactional
    public NotificationResponse update(Long id, NotificationRequest request) {
        Notification notification = findOrThrow(id);
        if (notification.getStatus() != NotificationStatus.PENDING) {
            throw new InvalidNotificationStateException(
                    "Only PENDING notifications can be updated. Current status: " + notification.getStatus());
        }
        validateRecipient(request.channel(), request.recipient());
        notification.setRecipient(request.recipient().trim());
        notification.setMessage(request.message().trim());
        notification.setChannel(request.channel());
        return NotificationResponse.from(notification); // changes are saved automatically at commit
    }

    /** A notification that is being delivered right now cannot be deleted. */
    @Transactional
    public void delete(Long id) {
        Notification notification = findOrThrow(id);
        if (notification.getStatus() == NotificationStatus.PROCESSING) {
            throw new InvalidNotificationStateException("A notification that is PROCESSING cannot be deleted");
        }
        notificationRepository.delete(notification);
    }

    /** Puts a FAILED notification back in the queue and grants it another round of attempts. */
    @Transactional
    public NotificationResponse retry(Long id) {
        Notification notification = findOrThrow(id);
        if (notification.getStatus() != NotificationStatus.FAILED) {
            throw new InvalidNotificationStateException(
                    "Only FAILED notifications can be retried. Current status: " + notification.getStatus());
        }
        notification.setMaxAttempts(notification.getMaxAttempts() + properties.maxAttempts());
        notification.setStatus(NotificationStatus.PENDING);
        notification.setNextAttemptAt(LocalDateTime.now());
        return NotificationResponse.from(notification);
    }

    // ------------------------------------------------------------------
    // Operations used by the background processor
    // ------------------------------------------------------------------

    /** IDs of notifications that are PENDING and whose retry delay has passed. */
    @Transactional(readOnly = true)
    public List<Long> findDueNotificationIds() {
        return notificationRepository
                .findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        NotificationStatus.PENDING, LocalDateTime.now(), PageRequest.of(0, properties.batchSize()))
                .stream()
                .map(Notification::getId)
                .toList();
    }

    /**
     * Tries to claim a notification (PENDING -> PROCESSING).
     * Returns empty if somebody else claimed it first or it is no longer PENDING.
     */
    @Transactional
    public Optional<Notification> claimForProcessing(Long id) {
        int updated = notificationRepository.transitionStatus(
                id, NotificationStatus.PENDING, NotificationStatus.PROCESSING, LocalDateTime.now());
        if (updated == 0) {
            return Optional.empty();
        }
        return notificationRepository.findById(id);
    }

    @Transactional
    public void recordSuccess(Long id) {
        Notification notification = findOrThrow(id);
        int attemptNumber = notification.getAttemptCount() + 1;
        attemptRepository.save(new NotificationAttempt(notification, attemptNumber, AttemptStatus.SENT, null));
        notification.setAttemptCount(attemptNumber);
        notification.setStatus(NotificationStatus.SENT);
    }

    /**
     * Records a failed attempt. If attempts remain, the notification goes back to PENDING with an
     * exponentially growing delay (10s, 20s, 40s ...). Otherwise it becomes FAILED.
     */
    @Transactional
    public void recordFailure(Long id, String errorMessage) {
        Notification notification = findOrThrow(id);
        int attemptNumber = notification.getAttemptCount() + 1;
        attemptRepository.save(new NotificationAttempt(
                notification, attemptNumber, AttemptStatus.FAILED, truncate(errorMessage)));
        notification.setAttemptCount(attemptNumber);

        if (attemptNumber >= notification.getMaxAttempts()) {
            notification.setStatus(NotificationStatus.FAILED);
            log.warn("Notification {} failed permanently after {} attempts", id, attemptNumber);
        } else {
            long delaySeconds = properties.retryDelaySeconds() * (1L << Math.min(attemptNumber - 1, 10));
            notification.setStatus(NotificationStatus.PENDING);
            notification.setNextAttemptAt(LocalDateTime.now().plusSeconds(delaySeconds));
            log.info("Notification {} will be retried in {}s (attempt {} of {})",
                    id, delaySeconds, attemptNumber, notification.getMaxAttempts());
        }
    }

    /**
     * If the application crashed while a notification was PROCESSING, it would stay in that status
     * forever. This puts such notifications back in the queue. Delivery is therefore
     * "at least once": in rare crash cases a notification may be delivered twice.
     */
    @Transactional
    public int recoverStuckNotifications() {
        LocalDateTime now = LocalDateTime.now();
        int recovered = notificationRepository.resetStuck(
                NotificationStatus.PROCESSING, NotificationStatus.PENDING,
                now, now.minusSeconds(properties.processingTimeoutSeconds()));
        if (recovered > 0) {
            log.warn("Recovered {} notification(s) stuck in PROCESSING", recovered);
        }
        return recovered;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Notification findOrThrow(Long id) {
        return notificationRepository.findById(id).orElseThrow(() -> new NotificationNotFoundException(id));
    }

    private void validateRecipient(NotificationChannel channel, String recipient) {
        String value = recipient.trim();
        switch (channel) {
            case EMAIL -> {
                if (!EMAIL_PATTERN.matcher(value).matches()) {
                    throw new InvalidRequestException("recipient must be a valid email address for the EMAIL channel");
                }
            }
            case SMS -> {
                if (!PHONE_PATTERN.matcher(value).matches()) {
                    throw new InvalidRequestException(
                            "recipient must be a phone number (10-15 digits, optional leading +) for the SMS channel");
                }
            }
            case IN_APP -> {
                // any non-blank user identifier is fine
            }
        }
    }

    private String normalizeKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String key = idempotencyKey.trim();
        if (key.length() > 100) {
            throw new InvalidRequestException("Idempotency-Key must be at most 100 characters");
        }
        return key;
    }

    private String truncate(String text) {
        String value = (text == null || text.isBlank()) ? "Unknown error" : text;
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
