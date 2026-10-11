package uk.co.jpmc.services.trading.positionbook.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TradeEventResult(
        int index, Long eventId, TradeEventStatus eventStatus, List<EventValidationError> errors) {}
