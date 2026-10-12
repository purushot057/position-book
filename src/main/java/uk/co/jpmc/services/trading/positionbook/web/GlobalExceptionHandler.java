package uk.co.jpmc.services.trading.positionbook.web;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import uk.co.jpmc.services.trading.positionbook.exception.BatchLimitExceededException;
import uk.co.jpmc.services.trading.positionbook.exception.EmptyBatchException;
import uk.co.jpmc.services.trading.positionbook.exception.EventRejectedException;
import uk.co.jpmc.services.trading.positionbook.exception.PositionNotFoundException;
import uk.co.jpmc.services.trading.positionbook.filter.RequestIdFilter;
import uk.co.jpmc.services.trading.positionbook.web.dto.ApiErrorResponse;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String INTERNAL_SERVER_ERROR_CODE = "INTERNAL_SERVER_ERROR";
    private static final String INTERNAL_SERVER_ERROR_MESSAGE = "An unexpected error occurred";
    private static final String INVALID_REQUEST_BODY_CODE = "INVALID_REQUEST_BODY";
    private static final String INVALID_REQUEST_BODY_MESSAGE =
            "Request body must contain valid JSON with the expected field types";
    private static final String MISSING_REQUEST_PARAMETER_CODE = "MISSING_REQUEST_PARAMETER";
    private static final String MISSING_REQUEST_PARAMETER_MESSAGE =
            "Required query parameter '%s' is missing";
    private static final String INVALID_REQUEST_PARAMETER_CODE = "INVALID_REQUEST_PARAMETER";
    private static final String INVALID_REQUEST_PARAMETER_MESSAGE =
            "Query parameter '%s' has an invalid value";
    private static final String REQUEST_FAILED_CODE = "REQUEST_FAILED";
    private static final String REQUEST_FAILED_MESSAGE = "The request could not be processed";
    private static final String POSITION_NOT_FOUND_CODE = "POSITION_NOT_FOUND";
    private static final String REQUEST_REJECTED_LOG_MESSAGE_TEMPLATE =
            "Request rejected: id={}, code={}";

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            final Exception exception,
            final Object body,
            final HttpHeaders headers,
            final HttpStatusCode status,
            final WebRequest request) {
        final String code;
        final String message;

        if (status.is5xxServerError()) {
            code = INTERNAL_SERVER_ERROR_CODE;
            message = INTERNAL_SERVER_ERROR_MESSAGE;
        } else if (exception instanceof HttpMessageNotReadableException) {
            code = INVALID_REQUEST_BODY_CODE;
            message = INVALID_REQUEST_BODY_MESSAGE;
        } else if (exception instanceof MissingServletRequestParameterException missing) {
            code = MISSING_REQUEST_PARAMETER_CODE;
            message = MISSING_REQUEST_PARAMETER_MESSAGE.formatted(missing.getParameterName());
        } else if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            code = INVALID_REQUEST_PARAMETER_CODE;
            message = INVALID_REQUEST_PARAMETER_MESSAGE.formatted(mismatch.getName());
        } else if (exception instanceof HandlerMethodValidationException) {
            code = INVALID_REQUEST_PARAMETER_CODE;
            message = "Query parameters contain invalid values";
        } else {
            final HttpStatus httpStatus = HttpStatus.resolve(status.value());
            code = httpStatus != null ? httpStatus.name() : REQUEST_FAILED_CODE;
            message = httpStatus != null ? httpStatus.getReasonPhrase() : REQUEST_FAILED_MESSAGE;
        }

        final ApiErrorResponse error = createError(code, message, exception, status);

        return super.handleExceptionInternal(exception, error, headers, status, request);
    }

    @ExceptionHandler(BatchLimitExceededException.class)
    public ResponseEntity<Object> handleBatchLimitExceeded(
            final BatchLimitExceededException exception) {
        final ApiErrorResponse error =
                createError(
                        "BATCH_LIMIT_EXCEEDED",
                        exception.getMessage(),
                        exception,
                        HttpStatus.BAD_REQUEST);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(EmptyBatchException.class)
    public ResponseEntity<Object> handleEmptyBatch(final EmptyBatchException exception) {
        final ApiErrorResponse error =
                createError(
                        "EMPTY_BATCH", exception.getMessage(), exception, HttpStatus.BAD_REQUEST);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(PositionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handlePositionNotFound(
            final PositionNotFoundException exception) {
        final ApiErrorResponse error =
                createError(
                        POSITION_NOT_FOUND_CODE,
                        exception.getMessage(),
                        exception,
                        HttpStatus.NOT_FOUND);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // Fallback: the service normally catches this exception per event
    // and includes the rejection in the batch response.
    @ExceptionHandler(EventRejectedException.class)
    public ResponseEntity<ApiErrorResponse> handleEventRejected(
            final EventRejectedException exception) {
        final String code = exception.getCode();
        final ApiErrorResponse error =
                createError(code, exception.getMessage(), exception, HttpStatus.CONFLICT);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(final Exception exception) {
        final ApiErrorResponse error =
                createError(
                        INTERNAL_SERVER_ERROR_CODE,
                        INTERNAL_SERVER_ERROR_MESSAGE,
                        exception,
                        HttpStatus.INTERNAL_SERVER_ERROR);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private ApiErrorResponse createError(
            final String code,
            final String message,
            final Exception exception,
            final HttpStatusCode status) {
        String id = MDC.get(RequestIdFilter.MDC_KEY);

        if (id == null) {
            id = UUID.randomUUID().toString();
        }

        if (status.is5xxServerError()) {
            log.error("Request failed: id={}, code={}", id, code, exception);
        } else {
            log.debug(REQUEST_REJECTED_LOG_MESSAGE_TEMPLATE, id, code);
        }

        return new ApiErrorResponse(code, id, message);
    }
}
