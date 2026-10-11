package uk.co.jpmc.services.trading.positionbook.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.core.io.ClassPathResource;

@JsonTest
class TradeEventRequestJsonTest {

    @Autowired private JacksonTester<List<TradeEventRequest>> json;

    @Test
    void shouldDeserializeTradeEventBatch() throws IOException {
        final List<TradeEventRequest> events =
                json.readObject(new ClassPathResource("json/trade-event-batch-request.json"));

        assertThat(events)
                .containsExactly(
                        new TradeEventRequest(1L, "BUY", "ACC1", "SEC1", 100L),
                        new TradeEventRequest(2L, "SELL", "ACC1", "SEC1", 40L));
    }

    @Test
    void shouldDeserializeCancellationWithoutSecurityOrQuantity() throws IOException {

        final List<TradeEventRequest> events =
                json.readObject(new ClassPathResource("json/cancel-event-batch-request.json"));

        assertThat(events).containsExactly(new TradeEventRequest(1L, "CANCEL", "ACC1", null, null));
    }
}
