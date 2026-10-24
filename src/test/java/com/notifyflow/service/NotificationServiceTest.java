package com.notifyflow.service;

import com.notifyflow.config.NotifyFlowProperties;
import com.notifyflow.dto.NotificationRequest;
import com.notifyflow.entity.Notification;
import com.notifyflow.entity.NotificationAttempt;
import com.notifyflow.enums.NotificationChannel;
import com.notifyflow.enums.NotificationStatus;
import com.notifyflow.exception.InvalidNotificationStateException;
import com.notifyflow.exception.InvalidRequestException;
import com.notifyflow.repository.NotificationAttemptRepository;
import com.notifyflow.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests: the repositories are mocked, so no database is needed. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationAttemptRepository attemptRepository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        NotifyFlowProperties properties = new NotifyFlowProperties(3, 10, 20, 60, 0.0);
        service = new NotificationService(notificationRepository, attemptRepository, properties);
    }

    @Test
    void create_savesNotificationAsPending() {
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationService.CreationResult result = service.create(
                new NotificationRequest("user@example.com", "Order confirmed", NotificationChannel.EMAIL), null);

        assertTrue(result.created());
        assertEquals(NotificationStatus.PENDING, result.notification().status());
        assertEquals(0, result.notification().attemptCount());
        assertEquals(3, result.notification().maxAttempts());
    }

    @Test
    void create_rejectsInvalidEmailRecipient() {
        NotificationRequest request =
                new NotificationRequest("not-an-email", "Hello", NotificationChannel.EMAIL);

        assertThrows(InvalidRequestException.class, () -> service.create(request, null));
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void create_rejectsInvalidPhoneRecipient() {
        NotificationRequest request =
                new NotificationRequest("12345", "Hello", NotificationChannel.SMS);

        assertThrows(InvalidRequestException.class, () -> service.create(request, null));
    }

    @Test
    void create_withKnownIdempotencyKey_returnsExistingNotification() {
        Notification existing = notification(NotificationStatus.PENDING, 0, 3);
        when(notificationRepository.findByIdempotencyKey("order-1052")).thenReturn(Optional.of(existing));

        NotificationService.CreationResult result = service.create(
                new NotificationRequest("user@example.com", "Order confirmed", NotificationChannel.EMAIL),
                "order-1052");

        assertEquals(false, result.created());
    }

    @Test
    void update_isRejectedWhenNotificationIsNotPending() {
        when(notificationRepository.findById(1L))
                .thenReturn(Optional.of(notification(NotificationStatus.SENT, 1, 3)));
        NotificationRequest request =
                new NotificationRequest("user@example.com", "New text", NotificationChannel.EMAIL);

        assertThrows(InvalidNotificationStateException.class, () -> service.update(1L, request));
    }

    @Test
    void retry_isRejectedWhenNotificationIsNotFailed() {
        when(notificationRepository.findById(1L))
                .thenReturn(Optional.of(notification(NotificationStatus.PENDING, 0, 3)));

        assertThrows(InvalidNotificationStateException.class, () -> service.retry(1L));
    }

    @Test
    void retry_movesFailedNotificationBackToPendingWithMoreAttempts() {
        Notification failed = notification(NotificationStatus.FAILED, 3, 3);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(failed));

        service.retry(1L);

        assertEquals(NotificationStatus.PENDING, failed.getStatus());
        assertEquals(6, failed.getMaxAttempts());
    }

    @Test
    void recordFailure_schedulesRetryWhenAttemptsRemain() {
        Notification processing = notification(NotificationStatus.PROCESSING, 0, 3);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(processing));

        service.recordFailure(1L, "provider down");

        assertEquals(NotificationStatus.PENDING, processing.getStatus());
        assertEquals(1, processing.getAttemptCount());
        assertTrue(processing.getNextAttemptAt().isAfter(LocalDateTime.now()));
        verify(attemptRepository).save(any(NotificationAttempt.class));
    }

    @Test
    void recordFailure_marksFailedWhenAttemptsAreExhausted() {
        Notification processing = notification(NotificationStatus.PROCESSING, 2, 3);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(processing));

        service.recordFailure(1L, "provider down");

        assertEquals(NotificationStatus.FAILED, processing.getStatus());
        assertEquals(3, processing.getAttemptCount());
    }

    @Test
    void recordSuccess_marksNotificationSent() {
        Notification processing = notification(NotificationStatus.PROCESSING, 1, 3);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(processing));

        service.recordSuccess(1L);

        assertEquals(NotificationStatus.SENT, processing.getStatus());
        assertEquals(2, processing.getAttemptCount());
        verify(attemptRepository).save(any(NotificationAttempt.class));
    }

    private Notification notification(NotificationStatus status, int attemptCount, int maxAttempts) {
        Notification n = new Notification();
        n.setRecipient("user@example.com");
        n.setMessage("Hello");
        n.setChannel(NotificationChannel.EMAIL);
        n.setStatus(status);
        n.setAttemptCount(attemptCount);
        n.setMaxAttempts(maxAttempts);
        n.setNextAttemptAt(LocalDateTime.now());
        return n;
    }
}
