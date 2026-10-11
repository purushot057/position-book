package uk.co.jpmc.services.trading.positionbook.persistence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.co.jpmc.services.trading.positionbook.exception.EventRejectedException;
import uk.co.jpmc.services.trading.positionbook.model.Position;
import uk.co.jpmc.services.trading.positionbook.model.PositionKey;
import uk.co.jpmc.services.trading.positionbook.model.TradeEvent;
import uk.co.jpmc.services.trading.positionbook.util.PositionBookConstants;

/**
 * Stores trades, positions and event history in memory. Reads and updates share the same lock to
 * prevent callers from seeing a partially updated position book.
 */
@Component
@Slf4j
public class InMemoryPositionBook {

    private static final int CANCELLED_TRADE_HISTORY_SIZE = 2;
    private static final String POSITION_LOOKUP_LOG_TEMPLATE =
            "Position lookup completed: found={}, quantity={}";
    private static final String TRADE_APPLIED_LOG_TEMPLATE =
            "Trade applied: eventId={}, action={}, quantity={} -> {}";
    private static final String TRADE_CANCELLED_LOG_TEMPLATE =
            "Trade cancelled: eventId={}, originalAction={}, quantity={} -> {}";

    private final Map<Long, List<TradeEvent>> eventsById = new HashMap<>();
    private final Map<PositionKey, Long> positionsByKey = new HashMap<>();

    @Value("${position-book.allow-negative-positions:false}")
    private boolean allowNegativePositions;

    // Input validation is performed by the service before calling this method.
    // The lock covers one event, allowing requests to interleave between events.
    public synchronized void apply(final TradeEvent event) {
        switch (event.action()) {
            case PositionBookConstants.BUY_ACTION -> applyTrade(event, true);
            case PositionBookConstants.SELL_ACTION -> applyTrade(event, false);
            case PositionBookConstants.CANCEL_ACTION -> cancelTrade(event);
            default ->
                    throw new EventRejectedException(
                            PositionBookConstants.INVALID_ACTION_CODE,
                            PositionBookConstants.INVALID_ACTION_MESSAGE);
        }
    }

    public synchronized Optional<Position> findPosition(
            final String accountId, final String securityId) {

        final PositionKey key = new PositionKey(accountId, securityId);
        final Long quantity = positionsByKey.get(key);

        log.atInfo()
                .addKeyValue(PositionBookConstants.ACCOUNT_ID_FIELD, accountId)
                .addKeyValue(PositionBookConstants.SECURITY_ID_FIELD, securityId)
                .log(POSITION_LOOKUP_LOG_TEMPLATE, quantity != null, quantity);

        if (quantity == null) {
            return Optional.empty();
        }

        return Optional.of(new Position(accountId, securityId, quantity));
    }

    private void applyTrade(final TradeEvent event, final boolean buy) {
        // Keep IDs after cancellation so they cannot be reused for a new trade.
        if (eventsById.containsKey(event.eventId())) {
            throw new EventRejectedException(
                    "DUPLICATE_EVENT_ID", "A trade with this event ID already exists");
        }

        final PositionKey key = new PositionKey(event.accountId(), event.securityId());

        final long currentQuantity = positionsByKey.getOrDefault(key, 0L);
        final long updatedQuantity = calculateQuantity(currentQuantity, event.quantity(), buy);

        // validation to prevent position becoming negative, if not allowed
        checkPosition(updatedQuantity);

        final List<TradeEvent> history = List.of(event);

        // Complete all business checks before changing either map
        eventsById.put(event.eventId(), history);
        positionsByKey.put(key, updatedQuantity);

        log.atInfo()
                .addKeyValue(PositionBookConstants.ACCOUNT_ID_FIELD, event.accountId())
                .addKeyValue(PositionBookConstants.SECURITY_ID_FIELD, event.securityId())
                .log(
                        TRADE_APPLIED_LOG_TEMPLATE,
                        event.eventId(),
                        event.action(),
                        currentQuantity,
                        updatedQuantity);
    }

    private void cancelTrade(final TradeEvent cancellation) {
        final List<TradeEvent> history = eventsById.get(cancellation.eventId());

        if (history == null) {
            throw new EventRejectedException(
                    "UNKNOWN_EVENT_ID", "No trade exists with this event Id");
        }

        // Each event Id holds the original BUY/SELL and at most, one CANCEL
        // A second event therefore means the trade has already been cancelled
        if (history.size() == CANCELLED_TRADE_HISTORY_SIZE) {
            throw new EventRejectedException(
                    "TRADE_ALREADY_CANCELLED", "The trade has already been cancelled");
        }

        final TradeEvent original = history.getFirst();

        if (!original.accountId().equals(cancellation.accountId())) {
            throw new EventRejectedException(
                    "ACCOUNT_MISMATCH", "Cancellation account must match the original trade");
        }

        // The cancellation's security ID and quantity are ignored
        // Cancellation uses the original trade's security and quantity
        final PositionKey key = new PositionKey(original.accountId(), original.securityId());

        final long currentQuantity = positionsByKey.get(key);

        // Reverse the original trade: subtract a BUY quantity or add a SELL quantity
        final boolean reverseSell = PositionBookConstants.SELL_ACTION.equals(original.action());
        final long updatedQuantity =
                calculateQuantity(currentQuantity, original.quantity(), reverseSell);

        checkPosition(updatedQuantity);

        final List<TradeEvent> updatedHistory = List.of(original, cancellation);

        // Complete all business checks before changing either map
        eventsById.put(cancellation.eventId(), updatedHistory);
        positionsByKey.put(key, updatedQuantity);

        log.atInfo()
                .addKeyValue(PositionBookConstants.ACCOUNT_ID_FIELD, original.accountId())
                .addKeyValue(PositionBookConstants.SECURITY_ID_FIELD, original.securityId())
                .log(
                        TRADE_CANCELLED_LOG_TEMPLATE,
                        cancellation.eventId(),
                        original.action(),
                        currentQuantity,
                        updatedQuantity);
    }

    private long calculateQuantity(
            final long currentQuantity, final long tradedQuantity, final boolean add) {

        try {
            return add
                    ? Math.addExact(currentQuantity, tradedQuantity)
                    : Math.subtractExact(currentQuantity, tradedQuantity);
        } catch (final ArithmeticException exception) {
            throw new EventRejectedException(
                    "POSITION_OVERFLOW",
                    "The resulting position exceeds the supported quantity range",
                    exception);
        }
    }

    private void checkPosition(final long quantity) {
        if (!allowNegativePositions && quantity < 0) {
            throw new EventRejectedException(
                    "NEGATIVE_POSITION", "The event would result in a negative position");
        }
    }
}
