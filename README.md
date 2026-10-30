# Patent Document Translation Service

Java 21 + Spring Boot 3.5.14 backend for the assessment. Maven builds an executable JAR. No database or Kafka broker is required.

## Run

Install JDK 21+ and Maven 3.6.3+, set `JAVA_HOME`, then run from this folder:

```sh
mvn spring-boot:run
```

On Windows you can also use `./dev.ps1` to run, `./dev.ps1 test` to test, or `./dev.ps1 package` to build. The helper detects JDKs under your `.jdks` folder and IntelliJ's bundled Maven when they are not configured on PATH.

Or build, test and start the executable:

```sh
mvn clean verify
java -jar target/patent-translation-1.0.0.jar
```

Base URL: `http://127.0.0.1:8080`.

- Swagger UI: `http://127.0.0.1:8080/swagger-ui.html`
- OpenAPI JSON: `http://127.0.0.1:8080/openapi.json`

Click **Authorize** in Swagger and enter `dev-api-key`. All `/api/**` requests require the `X-API-Key` header. Documentation is public.

## Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `API_KEY` | `dev-api-key` | Required API key; change outside local demo use |
| `PORT` | `8080` | HTTP port |
| `HOST` | `127.0.0.1` | Bind address |
| `MIN_DELAY_MS` | `5000` | Minimum mock translation delay |
| `MAX_DELAY_MS` | `15000` | Maximum mock translation delay |

Example PowerShell configuration: `$env:API_KEY = 'my-key'` before starting the app.

## API examples (PowerShell)

Submit a request:

```powershell
$headers = @{ 'X-API-Key' = 'dev-api-key' }
$body = @{
    patentId = 'EP123'
    sourceLang = 'en'
    targetLang = 'tr'
    documentText = 'Hello patent'
} | ConvertTo-Json

$request = Invoke-RestMethod http://localhost:8080/api/translate `
    -Method Post -Headers $headers -ContentType 'application/json' -Body $body
$request
```

`POST /api/translate` returns **202 Accepted**:

```json
{"requestId":"5d8bf02b-c466-4cae-bd9e-c0f4ba81dd04","status":"PENDING"}
```

Fetch status:

```powershell
Invoke-RestMethod "http://localhost:8080/api/translate/$($request.requestId)/status" -Headers $headers
```

`GET /api/translate/{requestId}/status` returns **200 OK**:

```json
{"requestId":"5d8bf02b-c466-4cae-bd9e-c0f4ba81dd04","status":"COMPLETED"}
```

Possible status values: `PENDING`, `COMPLETED`, `FAILED`.

Fetch result after the random 5–15 second delay:

```powershell
Start-Sleep -Seconds 16
Invoke-RestMethod "http://localhost:8080/api/translate/$($request.requestId)/result" -Headers $headers
```

`GET /api/translate/{requestId}/result` returns **200 OK**:

```json
{"requestId":"5d8bf02b-c466-4cae-bd9e-c0f4ba81dd04","resultText":"tnetap olleH"}
```

The mock translator reverses text with `StringBuilder.reverse()`; no real language translation occurs.

### Errors

| HTTP | Meaning |
| --- | --- |
| 400 | Invalid JSON, malformed UUID, or validation failure |
| 401 | Missing or wrong API key |
| 404 | Request not found |
| 409 | Result is pending |
| 415 | Unsupported content type |
| 422 | Translation or event publication failed |
| 503 | Background executor cannot accept the task |

For example, a pending result returns:

```json
{"error":"NOT_READY","message":"Translation is pending"}
```

All input fields are required and non-blank. Maximum lengths: `patentId` 200 characters, each language 35 characters, `documentText` 1,000,000 characters.

## Package structure and design

The project is organized by business context (`translation`, `notification`) and then by responsibility (`api`, `application`, `domain`, `infrastructure`). Security, documentation, and shared HTTP handling have separate packages. Tests mirror their feature packages.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full package tree, the reasoning behind each placement, dependency direction, and the request lifecycle.

## Postman tests

Import both JSON files in [postman/](postman/), select the **Patent Translation - Local** environment, and run the complete collection. It includes 26 request definitions covering the asynchronous lifecycle, authentication, validation, lookups, and documentation. See [postman/README.md](postman/README.md) for instructions and Newman commands.

## Architecture and Kafka integration

- `TranslationController`: REST API and response DTOs.
- `TranslationService`: scheduling, translation lifecycle, and completion publication.
- `TranslationRepository`: application port implemented by `InMemoryTranslationRepository`, using a thread-safe `ConcurrentHashMap` holding immutable request snapshots.
- `Translator`: application port implemented by `MockTranslator` for text reversal, replaceable independently of HTTP and storage.
- `EventPublisher`: producer interface. Its contract requires acknowledgment before returning, and throwing on failure.
- `InMemoryEventPublisher`: stores topic/event pairs and logs them to simulate Kafka.
- `ApiKeyFilter`: header authentication; `ApiErrorHandler`: general validation/JSON errors; `TranslationErrorHandler`: translation application failure to HTTP mapping.

A four-thread scheduled executor handles work outside the request thread. Each request receives its own UUID and random delay. Shutdown cancels queued tasks. Translation or publication failures set status to `FAILED`; completion is exposed only after the publisher acknowledges the event.

Every successful translation publishes to **patent-updates** with this payload:

```json
{
  "eventType":"TRANSLATION_COMPLETED",
  "patentId":"EP123",
  "requestId":"5d8bf02b-c466-4cae-bd9e-c0f4ba81dd04",
  "targetLang":"tr",
  "timestamp":"2026-10-30T13:00:00Z"
}
```

To use a real broker, add `org.springframework.kafka:spring-kafka` (version managed by Spring Boot), configure a `KafkaTemplate<String, TranslationCompletedEvent>` with a JSON value serializer, and replace the in-memory bean with an `EventPublisher` adapter:

```java
public final class KafkaEventPublisher implements EventPublisher {
    private final KafkaTemplate<String, TranslationCompletedEvent> template;

    public KafkaEventPublisher(KafkaTemplate<String, TranslationCompletedEvent> template) {
        this.template = template;
    }

    @Override
    public void publish(String topic, TranslationCompletedEvent event) {
        try {
            template.send(topic, event.patentId(), event).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka publish interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Kafka publish failed", exception);
        }
    }
}
```

This adapter is an integration example; the delivered application intentionally uses the simulated publisher and has no Kafka dependency. Register only one `EventPublisher` bean when switching adapters.

Storage and event logs are process-local and unbounded for the assessment. Restarting loses all requests. Run one process. This demo does not retry failed publications and does not guarantee atomicity between event delivery and status updates. Production use would require persistent storage, bounded retention, and an outbox/idempotent consumers.

## Tests

```sh
mvn test
```

Spring Boot/MockMvc integration tests cover the HTTP lifecycle, pending results, event payload and uniqueness, authentication, validation, missing/invalid IDs, public Swagger/OpenAPI, and concurrent submissions. Service tests cover translator failure, publisher failure, scheduler rejection, and invalid configuration. Test delays are shortened through test-only configuration.

Version compatibility reference: [springdoc Spring Boot 3 compatibility matrix](https://springdoc.org/v2/).
