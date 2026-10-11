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
class TradeBatchResponseJsonTest {

    @Autowired private JacksonTester<TradeBatchResponse> json;

    @Test
    void shouldSerializeProcessedBatchWithoutEmptyErrors() throws IOException {
        final TradeBatchResponse response =
                new TradeBatchResponse(
                        2,
                        0,
                        List.of(
                                new TradeEventResult(0, 1L, TradeEventStatus.PROCESSED, List.of()),
                                new TradeEventResult(
                                        1, 2L, TradeEventStatus.PROCESSED, List.of())));

        assertThat(json.write(response))
                .isStrictlyEqualToJson(
                        new ClassPathResource("json/trade-batch-response-processed.json"));
    }

    @Test
    void shouldSerializeMixedBatchWithRejectionDetails() throws IOException {
        final EventValidationError error =
                new EventValidationError(
                        "accountId",
                        "INVALID_ACCOUNT_ID",
                        "Account ID must contain 1–64 letters or numbers");

        final TradeBatchResponse response =
                new TradeBatchResponse(
                        1,
                        1,
                        List.of(
                                new TradeEventResult(
                                        0, 1L, TradeEventStatus.REJECTED, List.of(error)),
                                new TradeEventResult(
                                        1, 2L, TradeEventStatus.PROCESSED, List.of())));

        assertThat(json.write(response))
                .isStrictlyEqualToJson(
                        new ClassPathResource("json/trade-batch-response-mixed.json"));
    }
}
