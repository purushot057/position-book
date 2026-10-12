package uk.co.jpmc.services.trading.positionbook.web.dto;

import static uk.co.jpmc.services.trading.positionbook.util.PositionBookConstants.ACCOUNT_ID_FIELD;
import static uk.co.jpmc.services.trading.positionbook.util.PositionBookConstants.INVALID_ACTION_MESSAGE;
import static uk.co.jpmc.services.trading.positionbook.util.PositionBookConstants.SECURITY_ID_FIELD;
import static uk.co.jpmc.services.trading.positionbook.util.PositionBookConstants.TRADE_ACTION_PATTERN;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
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
                @Schema(description = "Trade ID; CANCEL uses the original trade ID", example = "1")
                Long eventId,
        @NotBlank(message = "Action is required")
                @Pattern(regexp = TRADE_ACTION_PATTERN, message = INVALID_ACTION_MESSAGE)
                @JsonProperty("action")
                @Schema(
                        allowableValues = {"BUY", "SELL", "CANCEL"},
                        example = "BUY")
                String action,
        @JsonProperty(ACCOUNT_ID_FIELD) @ValidAccountId @Schema(example = "ACC1") String accountId,
        @JsonProperty(SECURITY_ID_FIELD)
                @Schema(description = "Required for BUY/SELL; ignored for CANCEL", example = "SEC1")
                String securityId,
        @JsonProperty("quantity")
                @Schema(
                        description = "Positive quantity for BUY/SELL; ignored for CANCEL",
                        example = "100")
                Long quantity) {}
