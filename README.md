# NotifyFlow

A Spring Boot REST API for creating, scheduling, processing, and tracking notifications. NotifyFlow stores notification records, processes eligible notifications in the background, records delivery attempts, and supports retry handling.

> **Delivery note:** The current project uses a simulated notification sender. A status of `SENT` means the application's simulated delivery flow completed successfully; it does not mean a real email or SMS was delivered.

## Features

- Create and retrieve notifications through REST APIs
- Store notifications and delivery-attempt history in MySQL
- Process eligible notifications using a scheduled background worker
- Track notification lifecycle states such as `PENDING`, `PROCESSING`, `SENT`, and `FAILED`
- Record attempt counts and retry scheduling information
- Retry failed notifications manually
- Support an optional `Idempotency-Key` to help prevent duplicate notification creation
- Validate request data and return structured API errors
- Test endpoints using the included Postman collection

## Tech Stack

- **Java 21**
- **Spring Boot**
- **Spring MVC** for REST endpoints
- **Spring Data JPA** for persistence
- **Hibernate** as the JPA provider in the current version
- **MySQL** for persistent storage
- **Maven** for dependency management and builds
- **Postman** for API testing
- **Docker Compose** (optional) for running MySQL

## Architecture
## Architecture

The following diagram illustrates the architecture of NotifyFlow, including the request flow and supporting components.

![NotifyFlow Architecture](https://raw.githubusercontent.com/Anurag45pawar/Notifyflow/main/docs/notifyflow-architecture.png)
The diagram below shows the main request flow and the supporting components used by the application.

![NotifyFlow architecture diagram](docs/notifyflow-architecture.png)

### Request flow

1. A client such as Postman sends an HTTP request to the REST controller.
2. The controller receives the request and returns JSON responses.
3. The service layer applies business rules and transaction boundaries.
4. Spring Data JPA repositories access the database through the configured JPA provider.
5. MySQL stores notification records and processing-attempt information.

### Supporting components

- **DTOs:** Request and response objects exchanged through the API.
- **Entities:** Java classes mapped to database tables.
- **Exception handler:** Converts application errors into consistent API error responses.

### Background processing

A scheduled worker periodically looks for notifications eligible for processing, invokes the notification sender, and records the outcome, status, and attempt information.

## Requirements

- JDK 21
- Maven 3.9+ (or the Maven Wrapper if included)
- MySQL 8+, or Docker with Docker Compose
- Eclipse IDE or another Java IDE
- Postman (optional, for testing the API)

## Run Locally

### 1. Get the project

Clone the repository:

```bash
git clone https://github.com/Anurag45pawar/Notifyflow.git
cd Notifyflow
```

### 2. Start MySQL

You can use an existing local MySQL installation, or start the database with Docker Compose if the project's `docker-compose.yml` is configured for your environment:

```bash
docker compose up -d
```

Check `docker-compose.yml` and `src/main/resources/application.properties` to confirm the database name, port, and credentials.

### 3. Configure database credentials

Set the database environment variables expected by `application.properties`:

- `DB_USERNAME`
- `DB_PASSWORD`

Do not commit real passwords, email credentials, API keys, or other secrets to GitHub. If your local configuration uses different variable names, use the names defined in your `application.properties`.

### 4. Run the application

From the project root:

```bash
mvn spring-boot:run
```

Or, in Eclipse:

1. Choose **File → Import → Existing Maven Projects** if the project is not already imported.
2. Select the folder containing `pom.xml`.
3. Wait for Maven dependencies to finish downloading.
4. Start MySQL.
5. Open `NotifyFlowApplication.java`.
6. Right-click it and select **Run As → Java Application**.

The application is expected to start on port `8080`, unless configured otherwise.

## API Endpoints

Base URL: `http://localhost:8080`

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/notifications` | Create a notification |
| `GET` | `/api/notifications` | List notifications |
| `GET` | `/api/notifications/{id}` | Retrieve a notification by ID |
| `GET` | `/api/notifications/status/{status}` | Filter notifications by status |
| `PUT` | `/api/notifications/{id}` | Update a notification when allowed by its current state |
| `DELETE` | `/api/notifications/{id}` | Delete a notification when allowed by its current state |
| `POST` | `/api/notifications/{id}/retry` | Request another attempt for a failed notification |
| `GET` | `/api/notifications/{id}/attempts` | Retrieve delivery-attempt history |

Check the controller mappings in the source code if an endpoint differs in your checked-out version.

## Example: Create a Notification

In Postman, create a request with:

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/notifications`
- **Header:** `Content-Type: application/json`
- **Body:** `raw` → `JSON`

```json
{
  "recipient": "customer@example.com",
  "message": "Your order has been confirmed.",
  "channel": "EMAIL"
}
```

A successful response should include a notification ID and an initial status such as `PENDING`. The scheduled worker can then process the notification in the background.

## Example: Retrieve a Notification

```http
GET http://localhost:8080/api/notifications/1
```

The response includes fields such as the notification ID, recipient, message, channel, status, attempt count, and timestamps.

## Notification Lifecycle

```text
PENDING -> PROCESSING -> SENT
                 |
                 +---- failure ----> PENDING (retry scheduled)
                                      |
                                      +---- attempts exhausted ----> FAILED
```

Exact transitions and retry timing depend on the application's configuration and implementation. The current sender is simulated, so this project demonstrates the notification-processing workflow rather than real email/SMS delivery.

## Configuration

Review `src/main/resources/application.properties` for:

- Database URL and credentials
- Server port
- Scheduler interval
- Batch size
- Maximum delivery attempts
- Retry delay
- Processing timeout
- Simulated failure settings

Use environment variables for sensitive values rather than hard-coding secrets.

## Testing

Run automated tests from the project root:

```bash
mvn test
```

If the project includes a Postman collection, import:

```text
postman/NotifyFlow.postman_collection.json
```

Then start the application and run the requests in Postman.

## Project Structure

```text
src/
├── main/
│   ├── java/com/notifyflow/
│   │   ├── controller/   # REST endpoints
│   │   ├── service/      # Business logic and transactions
│   │   ├── repository/   # Spring Data JPA repositories
│   │   ├── entity/       # JPA entity classes
│   │   ├── dto/          # Request and response objects
│   │   ├── enums/        # Status and channel values
│   │   ├── exception/    # Exception handling
│   │   ├── scheduler/    # Scheduled processing trigger
│   │   ├── processor/    # Notification processing workflow
│   │   ├── delivery/     # Notification sender abstraction and simulator
│   │   └── config/       # Application and scheduling configuration
│   └── resources/
└── test/
```

Folder names may vary slightly by project revision.

## Future Improvements

- Integrate a real email provider using Spring's `JavaMailSender`
- Add a real SMS provider
- Add authentication and authorization
- Add pagination and sorting to notification listing
- Use Flyway or Liquibase for database migrations
- Add Docker packaging for the Spring Boot application
- Add CI with GitHub Actions to run tests automatically
- Add metrics and structured logging for operational monitoring

## Author

**Anurag Pawar**

GitHub: [Anurag45pawar](https://github.com/Anurag45pawar)
