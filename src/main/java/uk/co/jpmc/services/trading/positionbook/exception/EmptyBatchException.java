package uk.co.jpmc.services.trading.positionbook.exception;

import java.io.Serial;

public class EmptyBatchException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    public EmptyBatchException() {
        super("At least one trade event is required");
    }
}
