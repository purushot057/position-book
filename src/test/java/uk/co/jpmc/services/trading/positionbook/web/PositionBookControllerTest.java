package uk.co.jpmc.services.trading.positionbook.web;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.ACCOUNT_ID;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.ACCOUNT_ID_FIELD;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.ACTION_FIELD;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.EVENT_ID_FIELD;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.INVALID_ACCOUNT_ID_CODE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.INVALID_ACTION_CODE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.INVALID_EVENT_ID_CODE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.INVALID_QUANTITY_CODE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.INVALID_SECURITY_ID_CODE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.PROCESSED_STATUS;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.QUANTITY_FIELD;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.QUANTITY_NOT_POSITIVE_MESSAGE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.QUANTITY_REQUIRED_MESSAGE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.REJECTED_STATUS;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.SECURITY_ID;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.SECURITY_ID_FIELD;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.SECURITY_ID_INVALID_CHARACTERS_MESSAGE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.SECURITY_ID_REQUIRED_MESSAGE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.SECURITY_ID_TOO_LONG_MESSAGE;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import uk.co.jpmc.services.trading.positionbook.filter.RequestIdFilter;
import uk.co.jpmc.services.trading.positionbook.persistence.InMemoryPositionBook;
import uk.co.jpmc.services.trading.positionbook.service.TradeEventService;

@WebMvcTest(
        controllers = PositionBookController.class,
        properties = {
            "server.servlet.context-path=",
            "position-book.max-events-per-request=2",
            "position-book.allow-negative-positions=false"
        })
@Import({TradeEventService.class, InMemoryPositionBook.class})
@SuppressWarnings({"PMD.UnitTestShouldIncludeAssert", "PMD.AvoidDuplicateLiterals"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PositionBookControllerTest {

    private static final String PROCESS_TRADE_EVENTS_URI = "/trade-events";
    private static final String RETRIEVE_POSITION_URI = "/positions";
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
                .andExpect(jsonPath("$.results[0].eventStatus").value(PROCESSED_STATUS))
                .andExpect(jsonPath("$.results[0].errors").doesNotExist())
                .andExpect(jsonPath("$.results[1].eventId").value(2))
                .andExpect(jsonPath("$.results[1].eventStatus").value(PROCESSED_STATUS))
                .andExpect(jsonPath("$.results[1].errors").doesNotExist())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID));
    }

    @Test
    void shouldGetPositionReturnPositionForAccountAndSecurity() throws Exception {
        submitEvents(tradeEventsValidJson())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(2))
                .andExpect(jsonPath("$.rejected").value(0));

        retrievePosition()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.securityId").value(SECURITY_ID))
                .andExpect(jsonPath("$.quantity").value(150))
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

        // To prove that an oversized batch processes nothing
        retrievePosition()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(CODE_JSON_PATH).value("POSITION_NOT_FOUND"));
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
                .andExpect(jsonPath("$.results[0].eventStatus").value(REJECTED_STATUS))
                .andExpect(
                        jsonPath("$.results[0].errors[*].code")
                                .value(
                                        hasItems(
                                                INVALID_EVENT_ID_CODE,
                                                INVALID_ACCOUNT_ID_CODE,
                                                INVALID_SECURITY_ID_CODE,
                                                INVALID_QUANTITY_CODE)));
    }

    @Test
    void shouldPostTradeEventsAcceptCancelWithoutSecurityIdOrQuantity() throws Exception {

        submitEvents(
                        """
            [
              {"id": 1, "action": "BUY", "accountId": "ACC1",
               "securityId": "SEC1", "quantity": 100}
            ]
            """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1));

        submitEvents(
                        """
            [
              {"id": 1, "action": "CANCEL", "accountId": "ACC1"}
            ]
            """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.rejected").value(0))
                .andExpect(jsonPath("$.results[0].eventStatus").value(PROCESSED_STATUS))
                .andExpect(jsonPath("$.results[0].errors").doesNotExist());

        retrievePosition().andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(0));
    }

    @Test
    void shouldPostTradeEventsContinueProcessingAfterDuplicateEvent() throws Exception {
        submitEvents(
                        """
            [
              {"id": 1, "action": "BUY", "accountId": "ACC1",
               "securityId": "SEC1", "quantity": 100}
            ]
            """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1));

        submitEvents(
                        """
            [
              {"id": 1, "action": "BUY", "accountId": "ACC1",
               "securityId": "SEC1", "quantity": 100},
              {"id": 2, "action": "BUY", "accountId": "ACC1",
               "securityId": "SEC1", "quantity": 50}
            ]
            """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].index").value(0))
                .andExpect(jsonPath("$.results[0].eventStatus").value(REJECTED_STATUS))
                .andExpect(jsonPath("$.results[0].errors[0].code").value("DUPLICATE_EVENT_ID"))
                .andExpect(jsonPath("$.results[1].index").value(1))
                .andExpect(jsonPath("$.results[1].eventStatus").value(PROCESSED_STATUS))
                .andExpect(jsonPath("$.results[1].errors").doesNotExist());

        retrievePosition().andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(150));
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
                .andExpect(jsonPath("$.results[0].eventStatus").value(REJECTED_STATUS))
                .andExpect(jsonPath("$.results[1].index").value(1))
                .andExpect(jsonPath("$.results[1].eventId").value(2))
                .andExpect(jsonPath("$.results[1].eventStatus").value(PROCESSED_STATUS))
                .andExpect(jsonPath("$.results[1].errors").doesNotExist());

        // To prove the invalid event did not affect the quantity
        retrievePosition().andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(50));
    }

    @ParameterizedTest(name = "{index}: {1}")
    @MethodSource("businessRejections")
    void shouldPostTradeEventsReturnBusinessRejectionAndContinueProcessing(
            final String rejectedEvent, final String expectedCode) throws Exception {

        submitEvents(
                        """
            [
              {"id": 1, "action": "BUY", "accountId": "ACC1",
               "securityId": "SEC1", "quantity": 100}
            ]
            """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1));

        final String body =
                """
            [
              %s,
              {"id": 3, "action": "BUY", "accountId": "ACC1",
               "securityId": "SEC1", "quantity": 50}
            ]
            """
                        .formatted(rejectedEvent);

        submitEvents(body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.results[0].eventStatus").value(REJECTED_STATUS))
                .andExpect(jsonPath("$.results[0].errors[0].field").value("event"))
                .andExpect(jsonPath("$.results[0].errors[0].code").value(expectedCode))
                .andExpect(
                        jsonPath("$.results[0].errors[0].message").value(not(emptyOrNullString())))
                .andExpect(jsonPath("$.results[1].eventStatus").value(PROCESSED_STATUS))
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID));

        retrievePosition().andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(150));
    }

    @Test
    void shouldPostTradeEventsRejectRepeatedCancellation() throws Exception {
        submitEvents(
                        """
            [
              {"id": 1, "action": "BUY", "accountId": "ACC1",
               "securityId": "SEC1", "quantity": 100},
              {"id": 1, "action": "CANCEL", "accountId": "ACC1"}
            ]
            """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(2));

        submitEvents(
                        """
            [
              {"id": 1, "action": "CANCEL", "accountId": "ACC1"}
            ]
            """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(0))
                .andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.results[0].eventStatus").value(REJECTED_STATUS))
                .andExpect(
                        jsonPath("$.results[0].errors[0].code").value("TRADE_ALREADY_CANCELLED"));

        retrievePosition().andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(0));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("invalidEventFields")
    void shouldPostTradeEventsRejectEventWithInvalidField(
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
                .andExpect(jsonPath("$.results[0].eventStatus").value(REJECTED_STATUS))
                .andExpect(
                        jsonPath("$.results[0].errors")
                                .value(
                                        hasItem(
                                                allOf(
                                                        hasEntry("field", expectedField),
                                                        hasEntry("code", expectedCode),
                                                        hasEntry("message", expectedMessage)))));
    }

    @Test
    void shouldPostTradeEventsRejectNullEventAndProcessRemainingEvent() throws Exception {
        submitEvents(
                        """
            [
              null,
              {"id": 1, "action": "BUY", "accountId": "ACC1",
               "securityId": "SEC1", "quantity": 50}
            ]
            """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.results[0].index").value(0))
                .andExpect(jsonPath("$.results[0].eventStatus").value(REJECTED_STATUS))
                .andExpect(jsonPath("$.results[0].errors[0].code").value("INVALID_EVENT"))
                .andExpect(jsonPath("$.results[1].eventStatus").value(PROCESSED_STATUS));

        retrievePosition().andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(50));
    }

    @ParameterizedTest
    @ValueSource(strings = {ACCOUNT_ID_FIELD, SECURITY_ID_FIELD})
    void shouldGetPositionRejectMissingQueryParameter(final String missingParameter)
            throws Exception {

        final var request =
                get(RETRIEVE_POSITION_URI)
                        .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                        .accept(MediaType.APPLICATION_JSON);

        if (!ACCOUNT_ID_FIELD.equals(missingParameter)) {
            request.param(ACCOUNT_ID_FIELD, ACCOUNT_ID);
        }
        if (!SECURITY_ID_FIELD.equals(missingParameter)) {
            request.param(SECURITY_ID_FIELD, SECURITY_ID);
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
                                                + "' is missing"))
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID));
    }

    @Test
    void shouldGetPositionReturnNotFoundWhenPositionDoesNotExist() throws Exception {
        retrievePosition()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(CODE_JSON_PATH).value("POSITION_NOT_FOUND"))
                .andExpect(jsonPath("$.id").value(REQUEST_ID))
                .andExpect(jsonPath("$.message").value(not(emptyOrNullString())))
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID));
    }

    @Test
    void shouldGetPositionRejectUnsupportedHttpMethod() throws Exception {
        mockMvc.perform(
                        put(RETRIEVE_POSITION_URI)
                                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath(CODE_JSON_PATH).value(METHOD_NOT_ALLOWED_CODE))
                .andExpect(jsonPath("$.id").value(REQUEST_ID))
                .andExpect(jsonPath("$.message").value(not(emptyOrNullString())))
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID));
    }

    @Test
    void shouldGetPositionRejectUnsupportedResponseFormat() throws Exception {
        submitEvents(tradeEventsValidJson())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(2))
                .andExpect(jsonPath("$.rejected").value(0));

        mockMvc.perform(
                        get(RETRIEVE_POSITION_URI)
                                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                                .param(ACCOUNT_ID_FIELD, ACCOUNT_ID)
                                .param(SECURITY_ID_FIELD, SECURITY_ID)
                                .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID));
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
                        EVENT_ID_FIELD,
                        INVALID_EVENT_ID_CODE,
                        "Event Id is required"),
                Arguments.of(
                        "zero event ID",
                        "0",
                        "\"BUY\"",
                        "\"ACC1\"",
                        "\"SEC1\"",
                        "100",
                        EVENT_ID_FIELD,
                        INVALID_EVENT_ID_CODE,
                        "Event Id must be greater than zero"),
                Arguments.of(
                        "negative event ID",
                        "-1",
                        "\"BUY\"",
                        "\"ACC1\"",
                        "\"SEC1\"",
                        "100",
                        EVENT_ID_FIELD,
                        INVALID_EVENT_ID_CODE,
                        "Event Id must be greater than zero"),
                Arguments.of(
                        "null account ID",
                        "1",
                        "\"BUY\"",
                        "null",
                        "\"SEC1\"",
                        "100",
                        ACCOUNT_ID_FIELD,
                        INVALID_ACCOUNT_ID_CODE,
                        "Account ID must contain 1–64 letters or numbers"),
                Arguments.of(
                        "blank account ID",
                        "1",
                        "\"BUY\"",
                        "\" \"",
                        "\"SEC1\"",
                        "100",
                        ACCOUNT_ID_FIELD,
                        INVALID_ACCOUNT_ID_CODE,
                        "Account ID must contain 1–64 letters or numbers"),
                Arguments.of(
                        "account ID with forbidden character",
                        "1",
                        "\"BUY\"",
                        "\"ACC@1\"",
                        "\"SEC1\"",
                        "100",
                        ACCOUNT_ID_FIELD,
                        INVALID_ACCOUNT_ID_CODE,
                        "Account ID must contain 1–64 letters or numbers"),
                Arguments.of(
                        "account ID exceeding 64 characters",
                        "1",
                        "\"BUY\"",
                        "\"" + "A".repeat(65) + "\"",
                        "\"SEC1\"",
                        "100",
                        ACCOUNT_ID_FIELD,
                        INVALID_ACCOUNT_ID_CODE,
                        "Account ID must contain 1–64 letters or numbers"),
                Arguments.of(
                        "null action",
                        "1",
                        "null",
                        "\"ACC1\"",
                        "\"SEC1\"",
                        "100",
                        ACTION_FIELD,
                        INVALID_ACTION_CODE,
                        "Action is required"),
                Arguments.of(
                        "blank action",
                        "1",
                        "\"\"",
                        "\"ACC1\"",
                        "\"SEC1\"",
                        "100",
                        ACTION_FIELD,
                        INVALID_ACTION_CODE,
                        "Action is required"),
                Arguments.of(
                        "unknown action",
                        "1",
                        "\"PURCHASE\"",
                        "\"ACC1\"",
                        "\"SEC1\"",
                        "100",
                        ACTION_FIELD,
                        INVALID_ACTION_CODE,
                        "Action must be BUY, SELL or CANCEL"),
                Arguments.of(
                        "lowercase action",
                        "1",
                        "\"buy\"",
                        "\"ACC1\"",
                        "\"SEC1\"",
                        "100",
                        ACTION_FIELD,
                        INVALID_ACTION_CODE,
                        "Action must be BUY, SELL or CANCEL"),
                Arguments.of(
                        "missing security ID",
                        "1",
                        "\"BUY\"",
                        "\"ACC1\"",
                        "null",
                        "100",
                        SECURITY_ID_FIELD,
                        INVALID_SECURITY_ID_CODE,
                        SECURITY_ID_REQUIRED_MESSAGE),
                Arguments.of(
                        "invalid security ID",
                        "1",
                        "\"BUY\"",
                        "\"ACC1\"",
                        "\"SEC@1\"",
                        "100",
                        SECURITY_ID_FIELD,
                        INVALID_SECURITY_ID_CODE,
                        SECURITY_ID_INVALID_CHARACTERS_MESSAGE),
                Arguments.of(
                        "overlong security ID",
                        "1",
                        "\"BUY\"",
                        "\"ACC1\"",
                        "\"" + "S".repeat(129) + "\"",
                        "100",
                        SECURITY_ID_FIELD,
                        INVALID_SECURITY_ID_CODE,
                        SECURITY_ID_TOO_LONG_MESSAGE),
                Arguments.of(
                        "missing quantity",
                        "1",
                        "\"BUY\"",
                        "\"ACC1\"",
                        "\"SEC1\"",
                        "null",
                        QUANTITY_FIELD,
                        INVALID_QUANTITY_CODE,
                        QUANTITY_REQUIRED_MESSAGE),
                Arguments.of(
                        "non-positive quantity",
                        "1",
                        "\"BUY\"",
                        "\"ACC1\"",
                        "\"SEC1\"",
                        "0",
                        QUANTITY_FIELD,
                        INVALID_QUANTITY_CODE,
                        QUANTITY_NOT_POSITIVE_MESSAGE));
    }

    private static Stream<Arguments> businessRejections() {
        return Stream.of(
                Arguments.of(
                        """
                        {"id": 2, "action": "CANCEL", "accountId": "ACC1"}
                        """,
                        "UNKNOWN_EVENT_ID"),
                Arguments.of(
                        """
                        {"id": 1, "action": "CANCEL", "accountId": "ACC2"}
                        """,
                        "ACCOUNT_MISMATCH"),
                Arguments.of(
                        """
                        {"id": 2, "action": "SELL", "accountId": "ACC1",
                         "securityId": "SEC1", "quantity": 101}
                        """,
                        "NEGATIVE_POSITION"));
    }

    private ResultActions submitEvents(final String body) throws Exception {
        return mockMvc.perform(
                post(PROCESS_TRADE_EVENTS_URI)
                        .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private ResultActions retrievePosition() throws Exception {
        return mockMvc.perform(
                get(RETRIEVE_POSITION_URI)
                        .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                        .param(ACCOUNT_ID_FIELD, ACCOUNT_ID)
                        .param(SECURITY_ID_FIELD, SECURITY_ID)
                        .accept(MediaType.APPLICATION_JSON));
    }
}
