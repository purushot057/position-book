package uk.co.jpmc.services.trading.positionbook.exception;

import java.io.Serial;
import lombok.Getter;

/**
 * Indicates that an event cannot be applied because it violates a business rule. The service
 * catches this exception per event, records a rejected result, and continues processing the
 * remaining events in the batch.
 */
@Getter
public class EventRejectedException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    private final String code;

    public EventRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public EventRejectedException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
