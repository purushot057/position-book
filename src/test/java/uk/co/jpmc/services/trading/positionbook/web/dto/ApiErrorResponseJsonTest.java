package uk.co.jpmc.services.trading.positionbook.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.core.io.ClassPathResource;

@JsonTest
class ApiErrorResponseJsonTest {

    @Autowired private JacksonTester<ApiErrorResponse> json;

    @Test
    void shouldSerializeApiErrorResponse() throws IOException {
        final ApiErrorResponse response =
                new ApiErrorResponse(
                        "POSITION_NOT_FOUND",
                        "test-request-123",
                        "No position exists for the specified account and security.");

        assertThat(json.write(response))
                .isStrictlyEqualToJson(new ClassPathResource("json/api-error-response.json"));
    }
}
