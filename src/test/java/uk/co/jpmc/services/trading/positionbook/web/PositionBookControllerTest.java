package uk.co.jpmc.services.trading.positionbook.web;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.tradeEventsValidJson;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.co.jpmc.services.trading.positionbook.filter.RequestIdFilter;
import uk.co.jpmc.services.trading.positionbook.service.TradeEventService;

@WebMvcTest(
        controllers = PositionBookController.class,
        properties = {"server.servlet.context-path=", "position-book.max-events-per-request=2"})
@Import(TradeEventService.class)
@SuppressWarnings({"PMD.UnitTestShouldIncludeAssert", "PMD.AvoidDuplicateLiterals"})
class PositionBookControllerTest {

    private static final String PROCESS_TRADE_EVENTS_URI = "/trade-events";
    private static final String RETRIEVE_POSITION_URI = "/positions";
    private static final String ACCOUNT_ID_QUERY_PARAM = "accountId";
    private static final String SECURITY_ID_QUERY_PARAM = "securityId";
    private static final String ACCOUNT_ID_VALUE = "ACC1";
    private static final String SECURITY_ID_VALUE = "SEC1";
    private static final String CODE_JSON_PATH = "$.code";
    private static final String METHOD_NOT_ALLOWED_CODE = "METHOD_NOT_ALLOWED";
    private static final String REQUEST_ID = UUID.randomUUID().toString();

    @Autowired private MockMvc mockMvc;

    @Test
    void shouldPostTradeEventsReturnSuccessfulBatchSummary() throws Exception {
        mockMvc.perform(
                        post(PROCESS_TRADE_EVENTS_URI)
                                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(tradeEventsValidJson()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.processed").value(2))
                .andExpect(jsonPath("$.rejected").value(0))
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].eventId").value(1))
                .andExpect(jsonPath("$.results[0].eventStatus").value("PROCESSED"))
                .andExpect(jsonPath("$.results[0].errors").doesNotExist())
                .andExpect(jsonPath("$.results[1].eventId").value(2))
                .andExpect(jsonPath("$.results[1].eventStatus").value("PROCESSED"))
                .andExpect(jsonPath("$.results[1].errors").doesNotExist())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID));
    }

    @Test
    void shouldGetPositionReturnPositionForAccountAndSecurity() throws Exception {
        mockMvc.perform(
                        get(RETRIEVE_POSITION_URI)
                                .param(ACCOUNT_ID_QUERY_PARAM, ACCOUNT_ID_VALUE)
                                .param(SECURITY_ID_QUERY_PARAM, SECURITY_ID_VALUE)
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID_VALUE))
                .andExpect(jsonPath("$.securityId").value(SECURITY_ID_VALUE))
                .andExpect(jsonPath("$.quantity").value(150))
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, not(emptyOrNullString())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"accountId", "securityId"})
    void shouldGetPositionRejectMissingQueryParameter(final String missingParameter)
            throws Exception {

        final var request =
                get(RETRIEVE_POSITION_URI)
                        .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                        .accept(MediaType.APPLICATION_JSON);

        if (!ACCOUNT_ID_QUERY_PARAM.equals(missingParameter)) {
            request.param(ACCOUNT_ID_QUERY_PARAM, ACCOUNT_ID_VALUE);
        }
        if (!SECURITY_ID_QUERY_PARAM.equals(missingParameter)) {
            request.param(SECURITY_ID_QUERY_PARAM, SECURITY_ID_VALUE);
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODE_JSON_PATH).value("MISSING_REQUEST_PARAMETER"))
                .andExpect(jsonPath("$.id").value(REQUEST_ID))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Required query parameter '"
                                                + missingParameter
                                                + "' is missing."))
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "[{\"id\": 1", "{}", "[{\"quantity\": \"abc\"}]"})
    void shouldPostTradeEventsRejectUnreadableRequestBody(final String body) throws Exception {
        mockMvc.perform(
                        post(PROCESS_TRADE_EVENTS_URI)
                                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODE_JSON_PATH).value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.id").value(REQUEST_ID))
                .andExpect(jsonPath("$.message").value(not(emptyOrNullString())));
    }

    @Test
    void shouldPostTradeEventsRejectUnsupportedContentType() throws Exception {
        mockMvc.perform(
                        post(PROCESS_TRADE_EVENTS_URI)
                                .contentType(MediaType.APPLICATION_XML)
                                .accept(MediaType.APPLICATION_JSON)
                                .content("<events/>"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath(CODE_JSON_PATH).value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void shouldPostTradeEventsRejectUnsupportedHttpMethod() throws Exception {
        mockMvc.perform(put(PROCESS_TRADE_EVENTS_URI).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath(CODE_JSON_PATH).value(METHOD_NOT_ALLOWED_CODE));
    }

    @Test
    void shouldGetPositionRejectUnsupportedHttpMethod() throws Exception {
        mockMvc.perform(put(RETRIEVE_POSITION_URI).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath(CODE_JSON_PATH).value(METHOD_NOT_ALLOWED_CODE));
    }

    @Test
    void shouldGetPositionRejectUnsupportedResponseFormat() throws Exception {
        mockMvc.perform(
                        get(RETRIEVE_POSITION_URI)
                                .param(ACCOUNT_ID_QUERY_PARAM, ACCOUNT_ID_VALUE)
                                .param(SECURITY_ID_QUERY_PARAM, SECURITY_ID_VALUE)
                                .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void shouldPostTradeEventsRejectUnsupportedResponseFormat() throws Exception {
        mockMvc.perform(
                        post(PROCESS_TRADE_EVENTS_URI)
                                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_XML)
                                .content(tradeEventsValidJson()))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void shouldPostTradeEventsRejectEmptyBatch() throws Exception {
        mockMvc.perform(
                        post(PROCESS_TRADE_EVENTS_URI)
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODE_JSON_PATH).value("EMPTY_BATCH"));
    }

    @Test
    void shouldPostTradeEventsRejectBatchExceedingConfiguredLimit() throws Exception {
        final String body =
                """
            [
              {"id": 1, "action": "BUY", "accountId": "ACC1", "securityId": "SEC1", "quantity": 10},
              {"id": 2, "action": "BUY", "accountId": "ACC1", "securityId": "SEC1", "quantity": 10},
              {"id": 3, "action": "BUY", "accountId": "ACC1", "securityId": "SEC1", "quantity": 10}
            ]
            """;

        mockMvc.perform(
                        post(PROCESS_TRADE_EVENTS_URI)
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODE_JSON_PATH).value("BATCH_LIMIT_EXCEEDED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"BUY", "SELL"})
    void shouldPostTradeEventsRejectBuyOrSellWhenRequiredFieldsAreMissing(final String action)
            throws Exception {
        final String body =
                """
            [
              {
                "action": "%s"
              }
            ]
            """
                        .formatted(action);

        mockMvc.perform(
                        post(PROCESS_TRADE_EVENTS_URI)
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(0))
                .andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.results[0].eventStatus").value("REJECTED"))
                .andExpect(
                        jsonPath("$.results[0].errors[*].code")
                                .value(
                                        hasItems(
                                                "INVALID_EVENT_ID",
                                                "INVALID_ACCOUNT_ID",
                                                "INVALID_SECURITY_ID",
                                                "INVALID_QUANTITY")));
    }

    @Test
    void shouldPostTradeEventsAcceptCancelWithoutSecurityIdOrQuantity() throws Exception {
        final String body =
                """
            [
              {
                "id": 1,
                "action": "CANCEL",
                "accountId": "ACC1"
              }
            ]
            """;

        mockMvc.perform(
                        post(PROCESS_TRADE_EVENTS_URI)
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.rejected").value(0))
                .andExpect(jsonPath("$.results[0].eventStatus").value("PROCESSED"));
    }

    @Test
    void shouldPostTradeEventsContinueProcessingAfterInvalidEvent() throws Exception {
        final String body =
                """
            [
              {
                "id": 1,
                "action": "BUY",
                "accountId": "ACC@1",
                "securityId": "SEC1",
                "quantity": 100
              },
              {
                "id": 2,
                "action": "BUY",
                "accountId": "ACC1",
                "securityId": "SEC1",
                "quantity": 50
              }
            ]
            """;

        mockMvc.perform(
                        post(PROCESS_TRADE_EVENTS_URI)
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].index").value(0))
                .andExpect(jsonPath("$.results[0].eventStatus").value("REJECTED"))
                .andExpect(jsonPath("$.results[1].index").value(1))
                .andExpect(jsonPath("$.results[1].eventId").value(2))
                .andExpect(jsonPath("$.results[1].eventStatus").value("PROCESSED"))
                .andExpect(jsonPath("$.results[1].errors").doesNotExist());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("invalidEventFields")
    void shouldRejectEventWithInvalidField(
            final String scenario,
            final String idJson,
            final String actionJson,
            final String accountIdJson,
            final String securityIdJson,
            final String quantityJson,
            final String expectedField,
            final String expectedCode,
            final String expectedMessage)
            throws Exception {
        final String body =
                """
            [
              {
                "id": %s,
                "action": %s,
                "accountId": %s,
                "securityId": %s,
                "quantity": %s
              }
            ]
            """
                        .formatted(idJson, actionJson, accountIdJson, securityIdJson, quantityJson);

        mockMvc.perform(
                        post(PROCESS_TRADE_EVENTS_URI)
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(0))
                .andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].index").value(0))
                .andExpect(jsonPath("$.results[0].eventStatus").value("REJECTED"))
                .andExpect(jsonPath("$.results[0].errors[0].field").value(expectedField))
                .andExpect(jsonPath("$.results[0].errors[0].code").value(expectedCode))
                .andExpect(jsonPath("$.results[0].errors[0].message").value(expectedMessage));
    }

    private static Stream<Arguments> invalidEventFields() {
        return Stream.of(
                Arguments.of(
                        "null event ID",
                        "null",
                        "\"BUY\"",
                        "\"ACC1\"",
                        "\"SEC1\"",
                        "100",
                        "eventId",
                        "INVALID_EVENT_ID",
                        "Event Id is required"),
                Arguments.of(
                        "zero event ID",
                        "0",
                        "\"BUY\"",
                        "\"ACC1\"",
                        "\"SEC1\"",
                        "100",
                        "eventId",
                        "INVALID_EVENT_ID",
                        "Event Id must be greater than zero"),
                Arguments.of(
                        "negative event ID",
                        "-1",
                        "\"BUY\"",
                        "\"ACC1\"",
                        "\"SEC1\"",
                        "100",
                        "eventId",
                        "INVALID_EVENT_ID",
                        "Event Id must be greater than zero"),
                Arguments.of(
                        "null account ID",
                        "1",
                        "\"BUY\"",
                        "null",
                        "\"SEC1\"",
                        "100",
                        "accountId",
                        "INVALID_ACCOUNT_ID",
                        "Account ID must contain 1–64 letters or numbers"),
                Arguments.of(
                        "blank account ID",
                        "1",
                        "\"BUY\"",
                        "\" \"",
                        "\"SEC1\"",
                        "100",
                        "accountId",
                        "INVALID_ACCOUNT_ID",
                        "Account ID must contain 1–64 letters or numbers"),
                Arguments.of(
                        "account ID with forbidden character",
                        "1",
                        "\"BUY\"",
                        "\"ACC@1\"",
                        "\"SEC1\"",
                        "100",
                        "accountId",
                        "INVALID_ACCOUNT_ID",
                        "Account ID must contain 1–64 letters or numbers"),
                Arguments.of(
                        "account ID exceeding 64 characters",
                        "1",
                        "\"BUY\"",
                        "\"" + "A".repeat(65) + "\"",
                        "\"SEC1\"",
                        "100",
                        "accountId",
                        "INVALID_ACCOUNT_ID",
                        "Account ID must contain 1–64 letters or numbers"),
                Arguments.of(
                        "null action",
                        "1",
                        "null",
                        "\"ACC1\"",
                        "\"SEC1\"",
                        "100",
                        "action",
                        "INVALID_ACTION",
                        "Action is required"),
                Arguments.of(
                        "blank action",
                        "1",
                        "\"\"",
                        "\"ACC1\"",
                        "\"SEC1\"",
                        "100",
                        "action",
                        "INVALID_ACTION",
                        "Action is required"),
                Arguments.of(
                        "unknown action",
                        "1",
                        "\"PURCHASE\"",
                        "\"ACC1\"",
                        "\"SEC1\"",
                        "100",
                        "action",
                        "INVALID_ACTION",
                        "Action must be BUY, SELL or CANCEL"),
                Arguments.of(
                        "lowercase action",
                        "1",
                        "\"buy\"",
                        "\"ACC1\"",
                        "\"SEC1\"",
                        "100",
                        "action",
                        "INVALID_ACTION",
                        "Action must be BUY, SELL or CANCEL"),
                Arguments.of(
                        "missing security ID",
                        "1",
                        "\"BUY\"",
                        "\"ACC1\"",
                        "null",
                        "100",
                        "securityId",
                        "INVALID_SECURITY_ID",
                        "Security Id is required for BUY and SELL"),
                Arguments.of(
                        "invalid security ID",
                        "1",
                        "\"BUY\"",
                        "\"ACC1\"",
                        "\"SEC@1\"",
                        "100",
                        "securityId",
                        "INVALID_SECURITY_ID",
                        "Security Id may contain only letters, numbers, hyphens, underscores and dots"),
                Arguments.of(
                        "overlong security ID",
                        "1",
                        "\"BUY\"",
                        "\"ACC1\"",
                        "\"" + "S".repeat(129) + "\"",
                        "100",
                        "securityId",
                        "INVALID_SECURITY_ID",
                        "Security Id must not exceed 128 characters"),
                Arguments.of(
                        "missing quantity",
                        "1",
                        "\"BUY\"",
                        "\"ACC1\"",
                        "\"SEC1\"",
                        "null",
                        "quantity",
                        "INVALID_QUANTITY",
                        "Quantity is required for BUY and SELL"),
                Arguments.of(
                        "non-positive quantity",
                        "1",
                        "\"BUY\"",
                        "\"ACC1\"",
                        "\"SEC1\"",
                        "0",
                        "quantity",
                        "INVALID_QUANTITY",
                        "Quantity must be greater than zero for BUY and SELL"));
    }
}
