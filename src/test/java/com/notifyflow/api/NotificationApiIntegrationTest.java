package com.notifyflow.api;

import com.notifyflow.dto.NotificationRequest;
import com.notifyflow.entity.Notification;
import com.notifyflow.enums.AttemptStatus;
import com.notifyflow.enums.NotificationChannel;
import com.notifyflow.enums.NotificationStatus;
import com.notifyflow.processor.NotificationProcessor;
import com.notifyflow.repository.NotificationAttemptRepository;
import com.notifyflow.repository.NotificationRepository;
import com.notifyflow.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests: the whole application runs with an in-memory H2 database
 * (see src/test/resources/application-test.properties).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationAttemptRepository attemptRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationProcessor processor;

    @BeforeEach
    void cleanDatabase() {
        notificationRepository.deleteAll(); // also deletes attempts (cascade)
    }

    // ---------------- REST API ----------------

    @Test
    void createNotification_returns201AndPendingStatus() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"user@example.com","message":"Your order has been confirmed.","channel":"EMAIL"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attemptCount").value(0));
    }

    @Test
    void createNotification_withMissingRecipient_returns400() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"","message":"Hello","channel":"EMAIL"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.recipient").exists());
    }

    @Test
    void createNotification_withInvalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"not-an-email","message":"Hello","channel":"EMAIL"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createNotification_withUnknownChannel_returns400() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"user@example.com","message":"Hello","channel":"PIGEON"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getNotification_thatDoesNotExist_returns404() throws Exception {
        mockMvc.perform(get("/api/notifications/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Notification not found with id 99999"));
    }

    @Test
    void sameIdempotencyKey_doesNotCreateDuplicate() throws Exception {
        String body = """
                {"recipient":"user@example.com","message":"Order 1052 confirmed","channel":"EMAIL"}
                """;

        mockMvc.perform(post("/api/notifications")
                        .header("Idempotency-Key", "order-1052")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/notifications")
                        .header("Idempotency-Key", "order-1052")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertEquals(1, notificationRepository.count());
    }

    @Test
    void filterByStatus_returnsOnlyMatchingNotifications() throws Exception {
        notificationService.create(
                new NotificationRequest("user@example.com", "Hello", NotificationChannel.EMAIL), null);

        mockMvc.perform(get("/api/notifications/status/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/notifications/status/SENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/notifications/status/UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteNotification_returns204ThenNotFound() throws Exception {
        Long id = createNotification("user@example.com", "Hello");

        mockMvc.perform(delete("/api/notifications/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notifications/" + id)).andExpect(status().isNotFound());
    }

    // ---------------- Background processing ----------------

    @Test
    void processing_successfulDelivery_marksNotificationSentAndRecordsAttempt() {
        Long id = createNotification("user@example.com", "Your order has been confirmed.");

        processor.process(id);

        Notification saved = notificationRepository.findById(id).orElseThrow();
        assertEquals(NotificationStatus.SENT, saved.getStatus());
        assertEquals(1, saved.getAttemptCount());

        var attempts = attemptRepository.findByNotificationIdOrderByAttemptNumberAsc(id);
        assertEquals(1, attempts.size());
        assertEquals(AttemptStatus.SENT, attempts.get(0).getStatus());
    }

    @Test
    void processing_alwaysFailingDelivery_exhaustsAttemptsThenCanBeRetried() throws Exception {
        Long id = createNotification("user@example.com", "[fail] this one never gets delivered");

        processor.process(id); // attempt 1 -> back to PENDING
        assertEquals(NotificationStatus.PENDING, notificationRepository.findById(id).orElseThrow().getStatus());

        processor.process(id); // attempt 2 -> back to PENDING
        processor.process(id); // attempt 3 -> limit reached
        Notification failed = notificationRepository.findById(id).orElseThrow();
        assertEquals(NotificationStatus.FAILED, failed.getStatus());
        assertEquals(3, failed.getAttemptCount());
        assertEquals(3, attemptRepository.findByNotificationIdOrderByAttemptNumberAsc(id).size());

        // The delivery history is available through the API
        mockMvc.perform(get("/api/notifications/" + id + "/attempts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].status").value("FAILED"));

        // A FAILED notification can be retried manually; it gets another round of attempts
        mockMvc.perform(post("/api/notifications/" + id + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.maxAttempts").value(6));

        // ...but only when it is FAILED
        mockMvc.perform(post("/api/notifications/" + id + "/retry"))
                .andExpect(status().isConflict());
    }

    @Test
    void processing_aNotificationThatIsNotPending_isSkipped() {
        Long id = createNotification("user@example.com", "Hello");
        processor.process(id); // becomes SENT

        processor.process(id); // must be ignored, not delivered a second time

        assertEquals(1, attemptRepository.findByNotificationIdOrderByAttemptNumberAsc(id).size());
    }

    private Long createNotification(String recipient, String message) {
        return notificationService
                .create(new NotificationRequest(recipient, message, NotificationChannel.EMAIL), null)
                .notification()
                .id();
    }
}
