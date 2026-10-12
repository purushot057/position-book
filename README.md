# Position Book

An in-memory application for processing trade events and tracking
positions by account and security.

## Requirements

Java 21. The project uses Spring Boot 4 and the Gradle wrapper, so Gradle does not need to be installed separately.

## Run

Set `JAVA_HOME` to your Java 21 installation, then run from the project root:

```powershell
.\gradlew.bat bootRun
```

On Linux/macOS, use `./gradlew bootRun`.

The application runs at `http://localhost:8081/position-book/v1`

- `POST /trade-events` accepts a JSON array of events.
- `GET /positions?accountId=ACC1&securityId=SEC1` returns the current position.

Use `Content-Type: application/json` for submission. Configuration is in `src/main/resources/application.yml`

## Tests

Run tests, formatting checks and PMD:

```powershell
.\gradlew.bat clean check
```

On Linux/macOS, use `./gradlew clean check`. Test results are in `build/reports/tests/test/index.html`.

Tests cover validation, service handling, position calculations, cancellation, rejected operations, concurrency, HTTP integration and JSON contracts. JSON fixtures are in `src/test/resources/json`

## API documentation

Start the application, then open (http://localhost:8081/position-book/v1/swagger-ui.html) to view and try the endpoints.

The (http://localhost:8081/position-book/v1/v3/api-docs) is also available.

## Load testing

A Locust script is included in `locust/locustfile.py`. It submits BUY batches and retrieves positions, checking event outcomes as well as HTTP status.

From the project root on Windows:

```powershell
python -m venv locust/.venv

.\locust\.venv\Scripts\python.exe -m pip install -r locust/requirements.txt

.\locust\.venv\Scripts\python.exe -m locust -f locust/locustfile.py --host http://localhost:8081
```

Open http://localhost:8089 to configure and start the test.

The script submits BUY batches and retrieves positions. It checks individual event outcomes as well as HTTP status.

The default batch size is 10. Add `--batch-size 100` to the Locust command to test batches of 100, or `--position-mode shared` to make all users target the same position. The default mode uses a separate position per user.

Restart the Java application between runs to clear stored state.

## Assumptions

- Requests and responses use JSON; the tables illustrate the expected behaviour.
- BUY/SELL event IDs are globally unique. Duplicate trades are rejected, including reuse after cancellation.
- CANCEL refers to a previously accepted trade. Its account must match the original trade; its security and quantity are ignored. Unknown or repeated cancellations are rejected.
- Negative positions are disabled by default and can be enabled in configuration. Zero positions are retained.
- Batches contain at most 100 events by default; the limit is configurable. Invalid events are rejected individually while valid events continue to process.
- Malformed JSON, incompatible field types, empty batches and oversized batches reject the request.
- Position retrieval returns quantity only. Accepted trades and cancellation events are retained internally, history retrieval is not exposed.
- State is lost on restart. The application runs as a single instance with no shared or durable storage.

## Design

One map stores the original trade and optional cancellation by event ID. Another stores quantity by account and security.

Updates and position reads use the same lock. Each event is applied atomically with respect to other operations, but the whole batch is not transactional. Concurrent requests may interleave between events. An unexpected failure stops the batch; earlier successful events remain applied. Replaying accepted trade IDs returns duplicate rejections.

Validation checks required fields, identifier formats and positive quantities. Business checks handle duplicates, cancellation, negative positions and arithmetic overflow. Authentication and deployment request-size controls are not considered.

## AI use

I used ChatGPT and GitHub Copilot for design discussions, coding assistance and tests. I critically reviewed and adapted their suggestions, applying my own judgement to the design, test coverage and implementation decisions.
