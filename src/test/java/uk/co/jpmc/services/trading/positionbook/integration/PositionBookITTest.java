package uk.co.jpmc.services.trading.positionbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.ACCOUNT_ID;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.ACCOUNT_ID_FIELD;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.INVALID_ACCOUNT_ID_CODE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.PROCESSED_STATUS;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.REJECTED_STATUS;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.SECURITY_ID;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.SECURITY_ID_FIELD;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.tradeEventsValidJson;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import uk.co.jpmc.services.trading.positionbook.filter.RequestIdFilter;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "server.servlet.context-path=/position-book/v1",
            "position-book.max-events-per-request=2",
            "position-book.allow-negative-positions=false"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
class PositionBookITTest {

    private static final String CONTEXT_PATH = "/position-book/v1";
    private static final String PROCESS_TRADE_EVENT_BATCH_URI = "/trade-events";
    private static final String RETRIEVE_POSITION_URI = "/positions";
    private static final String REQUEST_ID = UUID.randomUUID().toString();
    private static final String ACCEPT_HEADER = "Accept";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();

    @LocalServerPort private int port;

    @Test
    void shouldProcessEventBatchAndReturnUpdatedPosition() throws Exception {
        final HttpResponse<String> response = submitEvents(tradeEventsValidJson());

        assertBatchSummary(response, 2, 0);

        final var json = JsonPath.parse(response.body());

        assertThat(json.read("$.results.length()", Integer.class)).isEqualTo(2);
        assertThat(json.read("$.results[0].eventId", Long.class)).isEqualTo(1L);
        assertThat(json.read("$.results[0].eventStatus", String.class)).isEqualTo(PROCESSED_STATUS);
        assertThat(json.read("$.results[1].eventId", Long.class)).isEqualTo(2L);
        assertThat(json.read("$.results[1].eventStatus", String.class)).isEqualTo(PROCESSED_STATUS);

        assertPositionQuantity(150L);
    }

    @Test
    void shouldProcessSellEventAndReverseItOnCancellation() throws Exception {
        assertBatchSummary(
                submitEvents(
                        """
                        [
                          {"id": 1, "action": "BUY", "accountId": "ACC1",
                           "securityId": "SEC1", "quantity": 100},
                          {"id": 2, "action": "SELL", "accountId": "ACC1",
                           "securityId": "SEC1", "quantity": 40}
                        ]
                        """),
                2,
                0);

        assertPositionQuantity(60L);

        // Cancellation must use the original security and quantity.
        assertBatchSummary(
                submitEvents(
                        """
                        [
                          {"id": 2, "action": "CANCEL", "accountId": "ACC1",
                           "securityId": "OTHER", "quantity": 999}
                        ]
                        """),
                1,
                0);

        assertPositionQuantity(100L);

        // Missing security and quantity are also valid for cancellation.
        assertBatchSummary(
                submitEvents(
                        """
                        [
                          {"id": 1, "action": "CANCEL", "accountId": "ACC1"}
                        ]
                        """),
                1,
                0);

        assertPositionQuantity(0L);
    }

    @Test
    void shouldReturnBadRequestWhenQueryParamIsMissing() throws Exception {
        final HttpRequest request =
                HttpRequest.newBuilder(
                                uri(
                                        RETRIEVE_POSITION_URI
                                                + "?"
                                                + ACCOUNT_ID_FIELD
                                                + "="
                                                + ACCOUNT_ID))
                        .timeout(REQUEST_TIMEOUT)
                        .header(ACCEPT_HEADER, MediaType.APPLICATION_JSON_VALUE)
                        .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                        .GET()
                        .build();

        assertApiError(send(request), HttpStatus.BAD_REQUEST, "MISSING_REQUEST_PARAMETER");
    }

    @Test
    void shouldReturnBadRequestWhenTradeEventRequestBodyIsMalformed() throws Exception {
        assertApiError(submitEvents("[{\"id\": 1"), HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY");
    }

    @Test
    void shouldRejectInvalidEventAndProcessRemainingEvent() throws Exception {
        final HttpResponse<String> response =
                submitEvents(
                        """
                [
                  {"id": 1, "action": "BUY", "accountId": "ACC@1",
                   "securityId": "SEC1", "quantity": 100},
                  {"id": 2, "action": "BUY", "accountId": "ACC1",
                   "securityId": "SEC1", "quantity": 50}
                ]
                """);

        assertBatchSummary(response, 1, 1);
        assertFirstEventRejected(response, INVALID_ACCOUNT_ID_CODE);

        final var json = JsonPath.parse(response.body());

        assertThat(json.read("$.results[1].index", Integer.class)).isEqualTo(1);
        assertThat(json.read("$.results[1].eventStatus", String.class)).isEqualTo(PROCESSED_STATUS);

        assertPositionQuantity(50L);
    }

    @Test
    void shouldRejectDuplicateWithoutDoubleCountingAndContinueProcessing() throws Exception {

        assertBatchSummary(submitEvents(tradeEventsValidJson()), 2, 0);

        final HttpResponse<String> response =
                submitEvents(
                        """
                [
                  {"id": 1, "action": "BUY", "accountId": "ACC1",
                   "securityId": "SEC1", "quantity": 100},
                  {"id": 3, "action": "BUY", "accountId": "ACC1",
                   "securityId": "SEC1", "quantity": 25}
                ]
                """);

        assertBatchSummary(response, 1, 1);
        assertFirstEventRejected(response, "DUPLICATE_EVENT_ID");

        assertPositionQuantity(175L);
    }

    @Test
    void shouldRejectNegativePositionAndProcessRemainingEvent() throws Exception {
        assertBatchSummary(submitEvents(tradeEventsValidJson()), 2, 0);

        final HttpResponse<String> response =
                submitEvents(
                        """
                [
                  {"id": 3, "action": "SELL", "accountId": "ACC1",
                   "securityId": "SEC1", "quantity": 151},
                  {"id": 4, "action": "BUY", "accountId": "ACC1",
                   "securityId": "SEC1", "quantity": 25}
                ]
                """);

        assertBatchSummary(response, 1, 1);
        assertFirstEventRejected(response, "NEGATIVE_POSITION");

        assertPositionQuantity(175L);
    }

    @Test
    void shouldRejectOversizedBatchWithoutProcessingAnyEvents() throws Exception {
        final HttpResponse<String> response =
                submitEvents(
                        """
                [
                  {"id": 1, "action": "BUY", "accountId": "ACC1",
                   "securityId": "SEC1", "quantity": 10},
                  {"id": 2, "action": "BUY", "accountId": "ACC1",
                   "securityId": "SEC1", "quantity": 10},
                  {"id": 3, "action": "BUY", "accountId": "ACC1",
                   "securityId": "SEC1", "quantity": 10}
                ]
                """);

        assertApiError(response, HttpStatus.BAD_REQUEST, "BATCH_LIMIT_EXCEEDED");

        assertApiError(retrievePosition(), HttpStatus.NOT_FOUND, "POSITION_NOT_FOUND");

        // The rejected batch must not reserve its event IDs either.
        assertBatchSummary(submitEvents(tradeEventsValidJson()), 2, 0);
        assertPositionQuantity(150L);
    }

    @Test
    void shouldReturnBadRequestForEmptyBatch() throws Exception {
        assertApiError(submitEvents("[]"), HttpStatus.BAD_REQUEST, "EMPTY_BATCH");
    }

    @Test
    void shouldReturnNotFoundWhenPositionDoesNotExist() throws Exception {

        final HttpRequest request =
                HttpRequest.newBuilder(uri(positionQuery()))
                        .timeout(REQUEST_TIMEOUT)
                        .header(ACCEPT_HEADER, MediaType.APPLICATION_JSON_VALUE)
                        .GET()
                        .build();

        final HttpResponse<String> response = send(request);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());

        final String generatedRequestId =
                response.headers().firstValue(RequestIdFilter.HEADER_NAME).orElseThrow();

        final var json = JsonPath.parse(response.body());

        assertThat(generatedRequestId).isNotBlank();
        assertThat(json.read("$.code", String.class)).isEqualTo("POSITION_NOT_FOUND");
        assertThat(json.read("$.id", String.class)).isEqualTo(generatedRequestId);
        assertThat(json.read("$.message", String.class)).isNotBlank();
    }

    private HttpResponse<String> submitEvents(final String body) throws Exception {
        final HttpRequest request =
                HttpRequest.newBuilder(uri(PROCESS_TRADE_EVENT_BATCH_URI))
                        .timeout(REQUEST_TIMEOUT)
                        .header(CONTENT_TYPE_HEADER, MediaType.APPLICATION_JSON_VALUE)
                        .header(ACCEPT_HEADER, MediaType.APPLICATION_JSON_VALUE)
                        .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

        return send(request);
    }

    private HttpResponse<String> retrievePosition() throws Exception {
        final HttpRequest request =
                HttpRequest.newBuilder(uri(positionQuery()))
                        .timeout(REQUEST_TIMEOUT)
                        .header(ACCEPT_HEADER, MediaType.APPLICATION_JSON_VALUE)
                        .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                        .GET()
                        .build();

        return send(request);
    }

    private void assertBatchSummary(
            final HttpResponse<String> response, final int processed, final int rejected) {

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());

        final var json = JsonPath.parse(response.body());

        assertThat(json.read("$.processed", Integer.class)).isEqualTo(processed);
        assertThat(json.read("$.rejected", Integer.class)).isEqualTo(rejected);
        assertThat(json.read("$.results.length()", Integer.class)).isEqualTo(processed + rejected);
        assertThat(response.headers().firstValue(RequestIdFilter.HEADER_NAME)).contains(REQUEST_ID);
    }

    private void assertFirstEventRejected(
            final HttpResponse<String> response, final String expectedCode) {

        final var json = JsonPath.parse(response.body());

        assertThat(json.read("$.results[0].index", Integer.class)).isZero();
        assertThat(json.read("$.results[0].eventStatus", String.class)).isEqualTo(REJECTED_STATUS);

        final List<String> codes = json.read("$.results[0].errors[*].code");
        assertThat(codes).contains(expectedCode);
    }

    private void assertPositionQuantity(final long expectedQuantity) throws Exception {
        final HttpResponse<String> response = retrievePosition();

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());

        final var json = JsonPath.parse(response.body());

        assertThat(json.read("$.accountId", String.class)).isEqualTo(ACCOUNT_ID);
        assertThat(json.read("$.securityId", String.class)).isEqualTo(SECURITY_ID);
        assertThat(json.read("$.quantity", Long.class)).isEqualTo(expectedQuantity);
        assertThat(response.headers().firstValue(RequestIdFilter.HEADER_NAME)).contains(REQUEST_ID);
    }

    private void assertApiError(
            final HttpResponse<String> response,
            final HttpStatus expectedStatus,
            final String expectedCode) {

        assertThat(response.statusCode()).isEqualTo(expectedStatus.value());

        final var json = JsonPath.parse(response.body());

        assertThat(json.read("$.code", String.class)).isEqualTo(expectedCode);
        assertThat(json.read("$.message", String.class)).isNotBlank();
        assertThat(json.read("$.id", String.class)).isEqualTo(REQUEST_ID);
        assertThat(response.headers().firstValue(RequestIdFilter.HEADER_NAME)).contains(REQUEST_ID);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + CONTEXT_PATH + path);
    }

    private String positionQuery() {
        return RETRIEVE_POSITION_URI
                + "?"
                + ACCOUNT_ID_FIELD
                + "="
                + ACCOUNT_ID
                + "&"
                + SECURITY_ID_FIELD
                + "="
                + SECURITY_ID;
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
