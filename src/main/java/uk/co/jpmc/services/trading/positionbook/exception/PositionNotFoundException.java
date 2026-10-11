package uk.co.jpmc.services.trading.positionbook.exception;

import java.io.Serial;

public class PositionNotFoundException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    public PositionNotFoundException(final String message) {
        super(message);
    }
}
