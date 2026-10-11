package uk.co.jpmc.services.trading.positionbook.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.co.jpmc.services.trading.positionbook.exception.BatchLimitExceededException;
import uk.co.jpmc.services.trading.positionbook.exception.EmptyBatchException;
import uk.co.jpmc.services.trading.positionbook.web.dto.EventValidationError;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeBatchResponse;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeEventRequest;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeEventResult;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeEventStatus;

@Service
@Slf4j
public class TradeEventService {

    private static final int MIN_EVENTS_PER_REQUEST = 1;

    private final Validator validator;
    private final int maxEventsPerRequest;

    public TradeEventService(
            Validator validator,
            @Value("${position-book.max-events-per-request:100}") int maxEventsPerRequest) {

        if (maxEventsPerRequest < MIN_EVENTS_PER_REQUEST) {
            throw new IllegalArgumentException("Maximum events per request must be positive");
        }

        this.validator = validator;
        this.maxEventsPerRequest = maxEventsPerRequest;
    }

    public TradeBatchResponse processTradeEvents(List<TradeEventRequest> events) {
        // Check for empty batch before proceeding
        if (Objects.isNull(events) || events.isEmpty()) {
            throw new EmptyBatchException();
        }

        // Check before processing anything
        if (events.size() > maxEventsPerRequest) {
            throw new BatchLimitExceededException(maxEventsPerRequest);
        }

        List<TradeEventResult> results = new ArrayList<>(events.size());
        int processed = 0;
        int rejected = 0;

        for (int index = 0; index < events.size(); index++) {

            final TradeEventRequest event = events.get(index);
            final List<EventValidationError> errors = validate(event);

            if (!errors.isEmpty()) {
                rejected++;

                results.add(
                        new TradeEventResult(
                                index,
                                event == null ? null : event.eventId(),
                                TradeEventStatus.REJECTED,
                                errors));

                log.atWarn()
                        .addKeyValue("eventIndex", index)
                        .addKeyValue("eventId", event == null ? null : event.eventId())
                        .addKeyValue("errorCount", errors.size())
                        .log("Trade event rejected");

                continue;
            }

            // Temporary: validation passed
            // Apply the position update here before marking it processed.
            processed++;

            results.add(
                    new TradeEventResult(
                            index, event.eventId(), TradeEventStatus.PROCESSED, List.of()));
        }

        log.atInfo()
                .addKeyValue("processed", processed)
                .addKeyValue("rejected", rejected)
                .log("Trade event batch completed");

        return new TradeBatchResponse(processed, rejected, List.copyOf(results));
    }

    private List<EventValidationError> validate(final TradeEventRequest event) {
        if (event == null) {
            return List.of(
                    new EventValidationError(
                            "event", "INVALID_EVENT", "Trade event must not be null"));
        }

        return validator.validate(event).stream()
                .map(this::toValidationError)
                .sorted(
                        Comparator.comparing(EventValidationError::field)
                                .thenComparing(EventValidationError::code)
                                .thenComparing(EventValidationError::message))
                .toList();
    }

    private EventValidationError toValidationError(
            ConstraintViolation<TradeEventRequest> violation) {
        final String field = violation.getPropertyPath().toString();

        final String code =
                switch (field) {
                    case "accountId" -> "INVALID_ACCOUNT_ID";
                    case "securityId" -> "INVALID_SECURITY_ID";
                    case "eventId" -> "INVALID_EVENT_ID";
                    case "action" -> "INVALID_ACTION";
                    case "quantity" -> "INVALID_QUANTITY";
                    default -> "INVALID_EVENT";
                };

        return new EventValidationError(field, code, violation.getMessage());
    }
}
