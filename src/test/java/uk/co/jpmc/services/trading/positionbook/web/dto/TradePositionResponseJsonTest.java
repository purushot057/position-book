package uk.co.jpmc.services.trading.positionbook.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.core.io.ClassPathResource;

@JsonTest
class TradePositionResponseJsonTest {

    @Autowired private JacksonTester<TradePositionResponse> json;

    @Test
    void shouldSerializePositionResponse() throws IOException {
        final TradePositionResponse response = new TradePositionResponse("ACC1", "SEC1", 60L);

        assertThat(json.write(response))
                .isStrictlyEqualToJson(new ClassPathResource("json/trade-position-response.json"));
    }

    @Test
    void shouldSerializeQuantityAsNumber() throws IOException {
        final TradePositionResponse response = new TradePositionResponse("ACC1", "SEC1", 60L);

        assertThat(json.write(response)).hasJsonPathNumberValue("$.quantity");
    }
}
