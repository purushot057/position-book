package uk.co.jpmc.services.trading.positionbook.web;

import static uk.co.jpmc.services.trading.positionbook.util.PositionBookConstants.ACCOUNT_ID_FIELD;
import static uk.co.jpmc.services.trading.positionbook.util.PositionBookConstants.SECURITY_ID_FIELD;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.co.jpmc.services.trading.positionbook.service.TradeEventService;
import uk.co.jpmc.services.trading.positionbook.validators.ValidAccountId;
import uk.co.jpmc.services.trading.positionbook.web.dto.ApiErrorResponse;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeBatchResponse;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeEventRequest;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradePositionResponse;

@RestController
@AllArgsConstructor
@Tag(name = "Position Book")
public class PositionBookController {

    private static final String TRADE_EVENTS_PATH = "/trade-events";
    private static final String POSITIONS_PATH = "/positions";
    private static final String APPLICATION_JSON = "application/json";
    private final TradeEventService tradeEventService;

    @Operation(
            summary = "Process a batch of trade events",
            description =
                    "Processes events in input order and returns an outcome for each event. "
                            + "Rejected events do not stop the batch. Unexpected failures stop "
                            + "processing without rolling back earlier events.",
            parameters =
                    @Parameter(
                            name = "x-jpmc-request-id",
                            in = ParameterIn.HEADER,
                            description = "Optional request ID. Generated when absent or invalid",
                            schema = @Schema(type = "string")))
    @ApiResponse(
            responseCode = "200",
            description = "Batch summary and individual event outcomes",
            content =
                    @Content(
                            mediaType = APPLICATION_JSON,
                            schema = @Schema(implementation = TradeBatchResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Empty batch, batch limit exceeded or unreadable request body",
            content =
                    @Content(
                            mediaType = APPLICATION_JSON,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "500",
            description = "Unexpected processing failure, the batch is not rolled back",
            content =
                    @Content(
                            mediaType = APPLICATION_JSON,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping(TRADE_EVENTS_PATH)
    public TradeBatchResponse processTradeEvents(@RequestBody List<TradeEventRequest> events) {

        return tradeEventService.processTradeEvents(events);
    }

    @Operation(
            summary = "Retrieve a position",
            description =
                    "Returns the current quantity for an account and security. "
                            + "Zero positions are retained. Event history is not returned.",
            parameters =
                    @Parameter(
                            name = "x-jpmc-request-id",
                            in = ParameterIn.HEADER,
                            description = "Optional request ID. Generated when absent or invalid.",
                            schema = @Schema(type = "string")))
    @ApiResponse(
            responseCode = "200",
            description = "Current position",
            content =
                    @Content(
                            mediaType = APPLICATION_JSON,
                            schema = @Schema(implementation = TradePositionResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Missing or invalid query parameters",
            content =
                    @Content(
                            mediaType = APPLICATION_JSON,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "No position exists for the account and security",
            content =
                    @Content(
                            mediaType = APPLICATION_JSON,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "500",
            description = "Unexpected server error",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping(POSITIONS_PATH)
    public TradePositionResponse getPosition(
            @RequestParam(ACCOUNT_ID_FIELD) @ValidAccountId String accountId,
            @RequestParam(SECURITY_ID_FIELD)
                    @NotBlank(message = "Security Id is required")
                    @Size(max = 128, message = "Security Id must not exceed 128 characters")
                    @Pattern(
                            regexp = "[A-Za-z0-9._-]+",
                            message =
                                    "Security Id may contain only letters, numbers, hyphens, underscores and dots")
                    String securityId) {

        return tradeEventService.retrievePosition(accountId, securityId);
    }
}
