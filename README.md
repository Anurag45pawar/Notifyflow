# NotifyFlow

A notification backend built with **Java 21, Spring Boot, Spring MVC, Spring Data JPA and MySQL**.

Other applications (an e-commerce app, for example) call one REST API to create a notification.
NotifyFlow saves it, delivers it in the background, tracks the status, retries failures with
back-off, and keeps a delivery history for every notification.

> Delivery is **simulated** (see `SimulatedNotificationSender`). Replacing it with a real email/SMS
> provider means writing one new class that implements `NotificationSender`.

## Run it

**Requirements:** Java 21, Maven 3.9+, MySQL 8 (or Docker).

1. Start MySQL - either your own installation, or: `docker compose up -d`
   (the compose file uses user `root`, password `root`).
2. Set your MySQL credentials as environment variables (never commit passwords to GitHub):
   - `DB_USERNAME` (default `root`)
   - `DB_PASSWORD` (default empty)
3. Start the application:
   ```
   mvn spring-boot:run
   ```
   In Eclipse: *File > Import > Existing Maven Projects*, then run `NotifyFlowApplication`
   (set the environment variables in *Run Configurations > Environment*).

The database `notifyflow_db` and its tables are created automatically on first start.

- API: http://localhost:8080/api/notifications
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

Run the tests (they use an in-memory H2 database, so MySQL is not needed): `mvn test`

## Try it

```bash
# 1. Create a notification (returns 201 with status PENDING)
curl -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" \
  -d '{"recipient":"customer@example.com","message":"Your order #1052 has been confirmed.","channel":"EMAIL"}'

# 2. A few seconds later the background worker has processed it
curl http://localhost:8080/api/notifications/1
curl http://localhost:8080/api/notifications/1/attempts

# 3. Make a delivery that always fails, to watch the retries (10s, 20s, then FAILED)
curl -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" \
  -d '{"recipient":"customer@example.com","message":"[fail] test retries","channel":"EMAIL"}'

# 4. Retry it manually once it is FAILED
curl -X POST http://localhost:8080/api/notifications/2/retry

# 5. Idempotency: the same key never creates a second notification
curl -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" -H "Idempotency-Key: order-1052-confirmation" \
  -d '{"recipient":"customer@example.com","message":"Order 1052 confirmed","channel":"EMAIL"}'
```

A Postman collection is in `postman/NotifyFlow.postman_collection.json`.

## API

| Method | Endpoint | Purpose | Success |
|--------|----------|---------|---------|
| POST | `/api/notifications` | Create (optional `Idempotency-Key` header) | 201 (200 if the key was already used) |
| GET | `/api/notifications` | List all, newest first | 200 |
| GET | `/api/notifications/{id}` | Get one | 200 |
| GET | `/api/notifications/status/{status}` | Filter by `PENDING`, `PROCESSING`, `SENT`, `FAILED` | 200 |
| PUT | `/api/notifications/{id}` | Update (only while `PENDING`) | 200 |
| DELETE | `/api/notifications/{id}` | Delete with its history (not while `PROCESSING`) | 204 |
| POST | `/api/notifications/{id}/retry` | Retry a `FAILED` notification | 200 |
| GET | `/api/notifications/{id}/attempts` | Delivery history | 200 |

Errors share one JSON shape. `400` = invalid input, `404` = not found, `409` = action not allowed
in the current status.

Validation rules: `recipient` and `message` are required; `channel` is `EMAIL`, `SMS` or `IN_APP`;
an `EMAIL` recipient must be a valid email address; an `SMS` recipient must be a phone number.

## How it works

```
Client --POST--> Controller --> Service --> Repository --> MySQL        (saves PENDING, returns at once)

Every 5 seconds:
Scheduler --> finds due PENDING notifications --> Processor (one at a time)
   1. claim:    PENDING -> PROCESSING   (single atomic UPDATE, so only one worker can win)
   2. deliver:  Sender.send(...)        (no database transaction is open during this call)
   3. record:   success -> SENT
                failure -> attempt saved, back to PENDING with a longer delay (10s, 20s, 40s ...)
                           or FAILED when the attempts are used up
```

Status lifecycle: `PENDING -> PROCESSING -> SENT`, or `PROCESSING -> PENDING` (retry) and finally `FAILED`.
A `FAILED` notification can go back to `PENDING` through the retry endpoint.

### Persistence note: JPA vs Hibernate

The entity classes use standard `jakarta.persistence` annotations, and repository queries use JPQL.
There are no direct `org.hibernate.*` API imports in the Java source. JPA is a specification, however,
not a standalone implementation; Spring Data JPA still needs a JPA provider at runtime (Spring Boot
selects Hibernate by default). The configuration in this version avoids Hibernate-specific properties.
Schema creation is provider-dependent; for production, use a migration tool such as Flyway or Liquibase.

### Design decisions worth explaining in an interview

- **No duplicate processing:** the claim step is `UPDATE ... SET status='PROCESSING' WHERE id=? AND status='PENDING'`.
  The database applies it atomically; the worker that gets `1` updated row owns the notification, any other gets `0`.
  This stays correct even if you run several instances of the application.
- **No transaction during delivery:** a slow provider must not hold a database connection open. Each step
  (claim, record result) is its own short transaction, which is why the logic lives in separate service methods.
- **Crash recovery:** if the application dies mid-delivery, the row would stay `PROCESSING` forever. The scheduler
  puts rows older than `notifyflow.processing-timeout-seconds` back in the queue. The trade-off is
  *at-least-once* delivery: in rare crash cases a message can be delivered twice.
- **Idempotency:** a unique `idempotency_key` column plus a catch for the unique-constraint violation, so
  two simultaneous identical requests still produce one row.
- **Why `attempt_count` / `max_attempts`:** attempt numbers keep increasing across manual retries, so the history
  stays consistent. A manual retry simply raises `max_attempts` by another round.
- **DTOs:** the API never exposes entities directly, so the database design can change without breaking clients.

## Configuration (`application.properties`)

| Property | Default | Meaning |
|----------|---------|---------|
| `notifyflow.max-attempts` | 3 | Delivery attempts before `FAILED` |
| `notifyflow.retry-delay-seconds` | 10 | First retry delay; doubles each time |
| `notifyflow.batch-size` | 20 | Notifications handled per scheduler run |
| `notifyflow.processing-timeout-seconds` | 60 | When a `PROCESSING` row counts as stuck |
| `notifyflow.simulated-failure-rate` | 0.3 | Chance that a simulated delivery fails |
| `notifyflow.scheduler.delay-ms` | 5000 | Pause between scheduler runs |

## Project structure

```
src/main/java/com/notifyflow
├── NotifyFlowApplication.java
├── controller/   NotificationController      (HTTP only)
├── service/      NotificationService         (business rules, transactions)
├── repository/   NotificationRepository, NotificationAttemptRepository
├── entity/       Notification, NotificationAttempt
├── dto/          NotificationRequest, NotificationResponse, AttemptResponse, ErrorResponse
├── enums/        NotificationStatus, NotificationChannel, AttemptStatus
├── exception/    custom exceptions + GlobalExceptionHandler
├── scheduler/    NotificationScheduler       (background worker trigger)
├── processor/    NotificationProcessor       (claim -> deliver -> record)
├── delivery/     NotificationSender + SimulatedNotificationSender
└── config/       NotifyFlowProperties, SchedulingConfig
```

## Ideas to take it further

- Replace the scheduler with **Redis Streams** (or RabbitMQ/Kafka) for event-driven processing
- Real email through `JavaMailSender` behind the `NotificationSender` interface
- **Flyway** migrations instead of `ddl-auto=update`
- Pagination on the list endpoints, Spring Security with API keys or JWT
- A `Dockerfile` for the application and a GitHub Actions workflow that runs `mvn test`
