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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import uk.co.jpmc.services.trading.positionbook.filter.RequestIdFilter;
import uk.co.jpmc.services.trading.positionbook.web.dto.ApiErrorResponse;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String INTERNAL_SERVER_ERROR_CODE = "INTERNAL_SERVER_ERROR";
    private static final String INTERNAL_SERVER_ERROR_MESSAGE = "An unexpected error occurred.";
    private static final String INVALID_REQUEST_BODY_CODE = "INVALID_REQUEST_BODY";
    private static final String INVALID_REQUEST_BODY_MESSAGE =
            "Request body must contain valid JSON with the expected field types.";
    private static final String MISSING_REQUEST_PARAMETER_CODE = "MISSING_REQUEST_PARAMETER";
    private static final String MISSING_REQUEST_PARAMETER_MESSAGE =
            "Required query parameter '%s' is missing.";
    private static final String INVALID_REQUEST_PARAMETER_CODE = "INVALID_REQUEST_PARAMETER";
    private static final String INVALID_REQUEST_PARAMETER_MESSAGE =
            "Query parameter '%s' has an invalid value.";
    private static final String REQUEST_FAILED_CODE = "REQUEST_FAILED";
    private static final String REQUEST_FAILED_MESSAGE = "The request could not be processed.";

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
        } else {
            final HttpStatus httpStatus = HttpStatus.resolve(status.value());
            code = httpStatus != null ? httpStatus.name() : REQUEST_FAILED_CODE;
            message = httpStatus != null ? httpStatus.getReasonPhrase() : REQUEST_FAILED_MESSAGE;
        }

        final ApiErrorResponse error = createError(code, message, exception, status);

        return super.handleExceptionInternal(exception, error, headers, status, request);
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
            log.debug("Request rejected: id={}, code={}", id, code);
        }

        return new ApiErrorResponse(code, id, message);
    }
}
