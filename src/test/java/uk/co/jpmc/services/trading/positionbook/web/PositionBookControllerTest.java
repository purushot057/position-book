package uk.co.jpmc.services.trading.positionbook.web;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.co.jpmc.services.trading.positionbook.utilities.TestHelper.createTradeEventsValidJson;

@WebMvcTest(
        controllers = PositionBookController.class,
        properties = "server.servlet.context-path="
)
class PositionBookControllerTest {

    private static final String REQUEST_ID = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldProcessTradeEventBatchOk() throws Exception {
        mockMvc.perform(post("/trade-events")
                        .header("x-jpmc-request-id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(createTradeEventsValidJson()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.received").value(2))
                .andExpect(header().string("x-jpmc-request-id", REQUEST_ID));
    }

    @Test
    void shouldReturnPositionOk() throws Exception {
        mockMvc.perform(get("/positions")
                        .param("accountId", "ACC1")
                        .param("securityId", "SEC1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("ACC1"))
                .andExpect(jsonPath("$.securityId").value("SEC1"))
                .andExpect(jsonPath("$.quantity").value(150))
                .andExpect(header().string(
                        "x-jpmc-request-id", not(emptyOrNullString())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"accountId", "securityId"})
    void shouldGetPositionRejectMissingQueryParameter(final String missingParameter)
            throws Exception {

        var request = get("/positions")
                .header("x-jpmc-request-id", REQUEST_ID)
                .accept(MediaType.APPLICATION_JSON);

        if (!missingParameter.equals("accountId")) {
            request.param("accountId", "ACC1");
        }
        if (!missingParameter.equals("securityId")) {
            request.param("securityId", "SEC1");
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MISSING_REQUEST_PARAMETER"))
                .andExpect(jsonPath("$.id").value(REQUEST_ID))
                .andExpect(jsonPath("$.message").value(
                        "Required query parameter '" + missingParameter
                                + "' is missing."))
                .andExpect(header().string("x-jpmc-request-id", REQUEST_ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "[{\"id\": 1",
            "{}",
            "[{\"quantity\": \"abc\"}]"
    })
    void shouldProcessTradeEventBatchRejectUnreadableRequestBody(String body) throws Exception {
        mockMvc.perform(post("/trade-events")
                        .header("x-jpmc-request-id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.id").value(REQUEST_ID))
                .andExpect(jsonPath("$.message")
                        .value(not(emptyOrNullString())));
    }

    @Test
    void shouldProcessTradeEventBatchRejectUnsupportedContentType() throws Exception {
        mockMvc.perform(post("/trade-events")
                        .contentType(MediaType.APPLICATION_XML)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("<events/>"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code")
                        .value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void shouldProcessTradeEventBatchRejectUnsupportedHttpMethod() throws Exception {
        mockMvc.perform(put("/trade-events")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code")
                        .value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void shouldGetPositionRejectUnsupportedHttpMethod() throws Exception {
        mockMvc.perform(put("/positions")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code")
                        .value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void shouldGetPositionRejectUnsupportedResponseFormat() throws Exception {
        mockMvc.perform(get("/positions")
                        .param("accountId", "ACC1")
                        .param("securityId", "SEC1")
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }
}
