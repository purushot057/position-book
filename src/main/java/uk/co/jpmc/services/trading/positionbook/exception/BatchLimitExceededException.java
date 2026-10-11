package uk.co.jpmc.services.trading.positionbook.exception;

import java.io.Serial;

public class BatchLimitExceededException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    public BatchLimitExceededException(final int limit) {
        super(String.format("A request must contain no more than %d events.", limit));
    }
}
