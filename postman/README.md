# Postman Tests

Files:

- `patent-translation.postman_collection.json`: a collection in v2.1 format containing 26 distinct requests with test assertions.
- `local.postman_environment.json`: local connection and test settings.

## Running with Postman

1. Start the application from the project directory using `./dev.ps1` or `mvn spring-boot:run`.
2. Use **Import** in Postman to import both JSON files.
3. Select the **Patent Translation - Local** environment.
4. Update `baseUrl` and `apiKey` if needed.
5. Run the entire collection using **Run collection**, with one iteration and a request delay of `0`.

`01 Submit translation` saves the returned `requestId` as a collection variable. Subsequent requests use this ID. `04 Poll until completed` checks the status every 500 ms by default; the test fails if translation does not complete within 30 seconds of submission. Once complete, the result is checked against the reversed input, including Unicode characters.

Use the server's default 5–15 second delay so the pending tests can observe `PENDING` and `409`. Settings such as `MIN_DELAY_MS=0` are unsuitable for these tests. Avoid long manual pauses between the first three requests. Saving the request ID also works when sending requests individually with **Send**, but automatic polling requires the Collection Runner.

## Environment Variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `baseUrl` | `http://127.0.0.1:8080` | Application URL |
| `apiKey` | `dev-api-key` | Must match the server's `API_KEY` |
| `pollIntervalMs` | `500` | Polling interval |
| `pollTimeoutMs` | `30000` | Timeout measured from the initial submission |

`requestId`, `pollStartedAt`, and `expectedResult` are updated at collection scope with every submission. Do not create environment variables with these names; variable precedence can affect request URLs and test behavior. The collection can be run repeatedly, creating a new translation request each time.

## Test Coverage

| Folder | Checks |
| --- | --- |
| `01 Translation lifecycle` | `202`, UUID format, initial `PENDING`, `409` for an unavailable result, polling, `COMPLETED`, correct reversed text, and response fields |
| `02 Authentication` | `401` for missing and incorrect API keys on submit/status/result endpoints |
| `03 Input validation` | Each of the four required fields missing individually, blank text, oversized patentId, malformed JSON, null body, and unsupported content type |
| `04 Request lookups` | `404` for unknown UUIDs and `400` for malformed UUIDs on status/result endpoints |
| `05 Documentation` | Public OpenAPI and Swagger access, all three endpoint definitions, and the API key security scheme |

The collection contains 26 distinct request definitions. Polling repeats requests, so the total number of HTTP requests and assertions varies with the translation delay.

`FAILED` and executor rejection scenarios are verified in Java tests using controlled dependency failures. The production API does not include endpoints that trigger failures solely for Postman testing. Java integration tests verify the Kafka event payload and that it is published once. Postman cannot inspect these internal details directly because the event store is not exposed over HTTP.

## Automated Execution with Newman

With the server running and Node.js/npm installed, execute the following from the project directory:

```sh
npx --yes newman@6.2.1 run postman/patent-translation.postman_collection.json -e postman/local.postman_environment.json --timeout-script 35000
```

To use another port, append `--env-var baseUrl=http://127.0.0.1:9090`. Newman returns a nonzero exit code when an assertion fails, making this command suitable for CI.

To generate a JSON report, append `--reporters cli,json --reporter-json-export target/postman-report.json`.

References: [Postman workflows and setNextRequest](https://learning.postman.com/docs/tests-and-scripts/running-collections/building-workflows), [Installing and running Newman](https://learning.postman.com/docs/collections/using-newman-cli/installing-running-newman).
