# Package Structure and Design Decisions

Packages are organized by business context first, then by responsibility within each context. Translation code lives under `translation`, while event publication lives under `notification`. These are modules within the same Spring Boot application, not separate microservices or independently deployable units.

```text
com.example.patent
├── PatentTranslationApplication
├── translation
│   ├── api
│   │   ├── TranslationController
│   │   ├── TranslationErrorHandler
│   │   └── dto
│   │       ├── SubmitTranslationRequest
│   │       ├── StatusResponse
│   │       └── ResultResponse
│   ├── application
│   │   ├── TranslationService
│   │   ├── exception
│   │   │   ├── TranslationNotFoundException
│   │   │   └── TranslationSchedulingException
│   │   └── port
│   │       ├── TranslationRepository
│   │       └── Translator
│   ├── domain
│   │   ├── TranslationDocument
│   │   ├── TranslationRecord
│   │   └── TranslationStatus
│   └── infrastructure
│       ├── config/TranslationExecutorConfiguration
│       ├── persistence/InMemoryTranslationRepository
│       └── translation/MockTranslator
├── notification
│   ├── application/port/EventPublisher
│   ├── domain/TranslationCompletedEvent
│   └── infrastructure/messaging/InMemoryEventPublisher
├── security/ApiKeyFilter
├── documentation/config/OpenApiConfiguration
└── shared/api
    ├── ApiException
    ├── ApiErrorHandler
    └── ErrorResponse
```

## Package Responsibilities and Rationale

| Package | Responsibility | Rationale |
| --- | --- | --- |
| `translation.api` | HTTP endpoints and translation-specific error mapping | URLs, HTTP status codes, and JSON contracts stay at the API boundary rather than entering the workflow. |
| `translation.api.dto` | Request/response models and Bean Validation | External API models are separate from internal models. Responses explicitly select fields to avoid exposing internal data such as the source document. |
| `translation.application` | Saving requests, scheduling work, translating, publishing events, and updating status | The use case is coordinated in one place rather than in the controller. |
| `translation.application.port` | Storage and translation engine interfaces | The service does not depend directly on `ConcurrentHashMap` or the reversal algorithm. A database or real translation provider can implement these interfaces. |
| `translation.application.exception` | Missing requests and scheduling failures | The service expresses failures without using `HttpStatus` or web exception types. |
| `translation.domain` | Documents, request status, and immutable records | These models do not import Spring, HTTP, Kafka, or storage implementations. Status and result are exposed together as a single snapshot. |
| `translation.infrastructure` | In-memory storage, mock translation, and executor configuration | Technology-specific details live in replaceable adapters. |
| `notification.application.port` | The `EventPublisher` publication contract | The translation workflow publishes events without depending on a concrete Kafka producer. |
| `notification.domain` | The event schema sent to downstream systems | The payload contract is separate from the producer implementation. The event does not import the translation record; the application service performs the mapping. |
| `notification.infrastructure.messaging` | In-memory event storage and logging | The simulated producer lives here. A real Kafka adapter would implement the same contract. |
| `security` | API key filtering | Authentication is checked before API requests reach a controller, avoiding repeated checks in each controller. |
| `documentation.config` | OpenAPI/Swagger configuration | Documentation settings remain separate from executor and workflow configuration. |
| `shared.api` | Common HTTP error models and validation/JSON error handling | Only shared web concerns belong here. This package does not import the translation or notification modules. |

Executor configuration belongs to `translation.infrastructure.config`, while Swagger configuration belongs to `documentation.config`, giving each configuration class one responsibility. `TranslationRepository` defines the storage port, implemented by `InMemoryTranslationRepository`. Similarly, `MockTranslator` implements the `Translator` port.

## Request Lifecycle

1. `ApiKeyFilter` checks the `X-API-Key` header for `/api/**` requests.
2. `TranslationController` validates the `SubmitTranslationRequest` DTO and converts it to a `TranslationDocument`.
3. `TranslationService` creates a UUID, saves a `PENDING` record through the repository port, and schedules delayed work on the executor. The API returns `202 Accepted` without waiting for translation.
4. The background task calls the `Translator` port. The current adapter reverses the document text.
5. The service creates a `TranslationCompletedEvent` and publishes it to `patent-updates` through `EventPublisher`.
6. Successful publication changes the record to `COMPLETED`. A translation or publication failure changes it to `FAILED`.
7. The status/result endpoints read the latest snapshot from the repository. A pending result returns `409`; a failed translation returns `422`.

Dependencies point from `api → application → domain/port` and from `infrastructure → port/domain`. Spring component scanning discovers the adapters, which are wired through constructor injection. The application entry point remains in the root package so all subpackages are scanned. The service is managed by Spring; domain models and port contracts remain independent of the framework.

## Error Handling and Concurrency

`TranslationErrorHandler` maps application failures to `404`/`503` HTTP responses. The general `ApiErrorHandler` handles JSON, UUID, and validation errors. This separation prevents shared web code from depending on a specific business context.

`ConcurrentHashMap` supports concurrent access. Because `TranslationRecord` is immutable, the `COMPLETED` status and result text become visible together, avoiding inconsistent reads caused by updating individual fields. A four-thread executor runs background work without blocking the request thread.

The service exposes `COMPLETED` only after publication is acknowledged. This demo does not guarantee that event publication and status updates happen atomically. In-memory storage and broker simulation meet the assessment requirements; persistent storage and real Kafka integration would require additional decisions around an outbox and idempotent processing.

## Test Organization

- `src/test/java/.../translation/api/TranslationApiTest`: Spring context, filters, controllers, JSON contracts, error codes, Swagger, and concurrent requests.
- `src/test/java/.../translation/application/TranslationServiceTest`: translation/publication failures, executor rejection, and invalid delay configuration.
- `postman/`: the collection and environment for testing the running application over HTTP. The README in that folder describes execution and coverage.

The API uses separate DTOs and domain models, repository/translator ports, and application exceptions independent of HTTP. These boundaries allow internal implementations to change while preserving endpoint paths and JSON contracts.
