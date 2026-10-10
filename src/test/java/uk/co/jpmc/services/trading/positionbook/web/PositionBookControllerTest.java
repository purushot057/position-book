package uk.co.jpmc.services.trading.positionbook.web;

import static org.hamcrest.Matchers.emptyOrNullString;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.co.jpmc.services.trading.positionbook.filter.RequestIdFilter;

@WebMvcTest(controllers = PositionBookController.class, properties = "server.servlet.context-path=")
@SuppressWarnings("PMD.UnitTestShouldIncludeAssert")
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
    void shouldProcessTradeEventBatchOk() throws Exception {
        mockMvc.perform(
                        post(PROCESS_TRADE_EVENTS_URI)
                                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(tradeEventsValidJson()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.received").value(2))
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID));
    }

    @Test
    void shouldReturnPositionOk() throws Exception {
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

        var request =
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
    void shouldProcessTradeEventBatchRejectUnreadableRequestBody(String body) throws Exception {
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
    void shouldProcessTradeEventBatchRejectUnsupportedContentType() throws Exception {
        mockMvc.perform(
                        post(PROCESS_TRADE_EVENTS_URI)
                                .contentType(MediaType.APPLICATION_XML)
                                .accept(MediaType.APPLICATION_JSON)
                                .content("<events/>"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath(CODE_JSON_PATH).value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void shouldProcessTradeEventBatchRejectUnsupportedHttpMethod() throws Exception {
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
    void shouldProcessTradeEventBatchRejectUnsupportedResponseFormat() throws Exception {
        mockMvc.perform(
                        post(PROCESS_TRADE_EVENTS_URI)
                                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_XML)
                                .content(tradeEventsValidJson()))
                .andExpect(status().isNotAcceptable());
    }
}
