package uk.co.jpmc.services.trading.positionbook.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import uk.co.jpmc.services.trading.positionbook.validators.ValidAccountId;
import uk.co.jpmc.services.trading.positionbook.validators.ValidTradeDetails;

@ValidTradeDetails
public record TradeEventRequest(
        @NotNull(message = "Event Id is required")
                @Positive(message = "Event Id must be greater than zero")
                @JsonProperty("id")
                Long eventId,
        @NotBlank(message = "Action is required")
                @Pattern(regexp = "BUY|SELL|CANCEL", message = "Action must be BUY, SELL or CANCEL")
                @JsonProperty("action")
                String action,
        @JsonProperty("accountId") @ValidAccountId String accountId,
        @JsonProperty("securityId") String securityId,
        @JsonProperty("quantity") Long quantity) {}
