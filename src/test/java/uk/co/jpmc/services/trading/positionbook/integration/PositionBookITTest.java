package uk.co.jpmc.services.trading.positionbook.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import uk.co.jpmc.services.trading.positionbook.filter.RequestIdFilter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.co.jpmc.services.trading.positionbook.utilities.TestHelper.createTradeEventsValidJson;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.context-path=/position-book/v1"
)
class PositionBookITTest {

    private static final String CONTEXT_PATH = "/position-book/v1";
    private static final String PROCESS_TRADE_EVENT_BATCH_URI = "/trade-events";
    private static final String RETRIEVE_POSITION_URI = "/positions";
    private static final String ACCOUNT_ID = "ACC1";
    private static final String SECURITY_ID = "SEC1";
    private static final String REQUEST_ID = UUID.randomUUID().toString();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void shouldAcceptTradeEventBatch() throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(uri(PROCESS_TRADE_EVENT_BATCH_URI))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .POST(HttpRequest.BodyPublishers.ofString(
                        createTradeEventsValidJson()))
                .build();

        final HttpResponse<String> response = send(request);
        final var json = JsonPath.parse(response.body());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(json.read("$.received", Integer.class)).isEqualTo(2);
        assertThat(response.headers().firstValue(RequestIdFilter.HEADER_NAME))
                .contains(REQUEST_ID);
    }

    @Test
    void shouldReturnPositionForAccountAndSecurity() throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(
                        uri(RETRIEVE_POSITION_URI + "?accountId=" + ACCOUNT_ID
                                + "&securityId=" + SECURITY_ID))
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .GET()
                .build();

        final HttpResponse<String> response = send(request);
        final var json = JsonPath.parse(response.body());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(json.read("$.accountId", String.class)).isEqualTo(ACCOUNT_ID);
        assertThat(json.read("$.securityId", String.class)).isEqualTo(SECURITY_ID);
        assertThat(json.read("$.quantity", Long.class)).isEqualTo(150L);

        final String requestId = response.headers()
                .firstValue(RequestIdFilter.HEADER_NAME)
                .orElseThrow();

        assertThat(requestId).isNotBlank();
    }

    @Test
    void shouldReturnBadRequestWhenQueryParamIsMissing()
            throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(
                        uri(RETRIEVE_POSITION_URI + "?accountId=" + ACCOUNT_ID))
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .GET()
                .build();

        final HttpResponse<String> response = send(request);
        final var json = JsonPath.parse(response.body());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(json.read("$.code", String.class))
                .isEqualTo("MISSING_REQUEST_PARAMETER");
        assertThat(json.read("$.id", String.class))
                .isEqualTo(REQUEST_ID);
        assertThat(response.headers().firstValue(RequestIdFilter.HEADER_NAME))
                .contains(REQUEST_ID);
    }

    @Test
    void shouldReturnBadRequestWhenTradeEventRequestBodyIsMalformed() throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(
                        uri(PROCESS_TRADE_EVENT_BATCH_URI))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .POST(HttpRequest.BodyPublishers.ofString("[{\"id\": 1"))
                .build();

        final HttpResponse<String> response = send(request);

        assertThat(response.statusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());

        final var json = JsonPath.parse(response.body());

        assertThat(json.read("$.code", String.class))
                .isEqualTo("INVALID_REQUEST_BODY");
        assertThat(json.read("$.message", String.class))
                .isNotBlank();
        assertThat(json.read("$.id", String.class))
                .isEqualTo(REQUEST_ID);
        assertThat(response.headers().firstValue(RequestIdFilter.HEADER_NAME))
                .contains(REQUEST_ID);
    }

    private URI uri(String path) {
        return URI.create(
                "http://localhost:" + port + CONTEXT_PATH + path
        );
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return HTTP_CLIENT.send(
                request, HttpResponse.BodyHandlers.ofString()
        );
    }
}
