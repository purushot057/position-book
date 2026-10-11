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
import uk.co.jpmc.services.trading.positionbook.exception.EventRejectedException;
import uk.co.jpmc.services.trading.positionbook.exception.PositionNotFoundException;
import uk.co.jpmc.services.trading.positionbook.model.Position;
import uk.co.jpmc.services.trading.positionbook.model.TradeEvent;
import uk.co.jpmc.services.trading.positionbook.persistence.InMemoryPositionBook;
import uk.co.jpmc.services.trading.positionbook.util.PositionBookConstants;
import uk.co.jpmc.services.trading.positionbook.web.dto.EventValidationError;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeBatchResponse;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeEventRequest;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeEventResult;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeEventStatus;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradePositionResponse;

@Service
@Slf4j
public class TradeEventService {

    private static final int MIN_EVENTS_PER_REQUEST = 1;
    private static final String EMPTY_BATCH_MESSAGE = "At least one trade event is required";
    private static final String POSITION_NOT_FOUND_MESSAGE =
            "No position exists for the specified account and security";
    private static final String BATCH_LIMIT_EXCEEDED_MESSAGE_FORMAT =
            "A request must contain no more than %d events";

    private final Validator validator;
    private final int maxEventsPerRequest;
    private final InMemoryPositionBook positionBook;

    public TradeEventService(
            Validator validator,
            @Value("${position-book.max-events-per-request:100}") int maxEventsPerRequest,
            InMemoryPositionBook positionBook) {

        if (maxEventsPerRequest < MIN_EVENTS_PER_REQUEST) {
            throw new IllegalArgumentException("Maximum events per request must be positive");
        }

        this.validator = validator;
        this.maxEventsPerRequest = maxEventsPerRequest;
        this.positionBook = positionBook;
    }

    public TradeBatchResponse processTradeEvents(List<TradeEventRequest> events) {
        // Check for empty batch before proceeding
        if (Objects.isNull(events) || events.isEmpty()) {
            throw new EmptyBatchException(EMPTY_BATCH_MESSAGE);
        }

        // Check before processing anything
        if (events.size() > maxEventsPerRequest) {
            throw new BatchLimitExceededException(
                    String.format(BATCH_LIMIT_EXCEEDED_MESSAGE_FORMAT, maxEventsPerRequest));
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
                        .addKeyValue(
                                PositionBookConstants.ACCOUNT_ID_FIELD,
                                event == null ? null : event.accountId())
                        .addKeyValue(
                                PositionBookConstants.SECURITY_ID_FIELD,
                                event == null ? null : event.securityId())
                        .log(
                                "Trade event rejected: index={}, eventId={}, errorCount={}",
                                index,
                                event == null ? null : event.eventId(),
                                errors.size());

                continue;
            }

            try {
                // Apply the position update here before marking it processed.
                final TradeEvent tradeEvent =
                        new TradeEvent(
                                event.eventId(),
                                String.valueOf(event.action()),
                                event.accountId(),
                                event.securityId(),
                                event.quantity());

                positionBook.apply(tradeEvent);

                processed++;
                results.add(
                        new TradeEventResult(
                                index, event.eventId(), TradeEventStatus.PROCESSED, List.of()));
            } catch (final EventRejectedException exception) {
                rejected++;
                results.add(
                        new TradeEventResult(
                                index,
                                event.eventId(),
                                TradeEventStatus.REJECTED,
                                List.of(
                                        new EventValidationError(
                                                "event",
                                                exception.getCode(),
                                                exception.getMessage()))));

                log.atWarn()
                        .addKeyValue(PositionBookConstants.ACCOUNT_ID_FIELD, event.accountId())
                        .addKeyValue(PositionBookConstants.SECURITY_ID_FIELD, event.securityId())
                        .log(
                                "Trade event rejected: index={}, eventId={}, code={}, reason={}",
                                index,
                                event.eventId(),
                                exception.getCode(),
                                exception.getMessage());
            }
        }

        log.atInfo().log(
                "Trade event batch completed: processed={}, rejected={}", processed, rejected);

        return new TradeBatchResponse(processed, rejected, List.copyOf(results));
    }

    public TradePositionResponse retrievePosition(final String accountId, final String securityId) {

        final Position position =
                positionBook
                        .findPosition(accountId, securityId)
                        .orElseThrow(
                                () -> new PositionNotFoundException(POSITION_NOT_FOUND_MESSAGE));

        return new TradePositionResponse(
                position.accountId(), position.securityId(), position.quantity());
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
                    case PositionBookConstants.ACCOUNT_ID_FIELD -> "INVALID_ACCOUNT_ID";
                    case PositionBookConstants.SECURITY_ID_FIELD -> "INVALID_SECURITY_ID";
                    case "eventId" -> "INVALID_EVENT_ID";
                    case "action" -> PositionBookConstants.INVALID_ACTION_CODE;
                    case "quantity" -> "INVALID_QUANTITY";
                    default -> "INVALID_EVENT";
                };

        return new EventValidationError(field, code, violation.getMessage());
    }
}
