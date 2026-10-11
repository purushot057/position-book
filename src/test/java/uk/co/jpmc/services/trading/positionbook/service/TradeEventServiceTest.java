package uk.co.jpmc.services.trading.positionbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.jpmc.services.trading.positionbook.exception.BatchLimitExceededException;
import uk.co.jpmc.services.trading.positionbook.exception.EmptyBatchException;
import uk.co.jpmc.services.trading.positionbook.web.dto.EventValidationError;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeBatchResponse;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeEventRequest;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeEventResult;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeEventStatus;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
class TradeEventServiceTest {

    private static final int BATCH_LIMIT = 3;
    private static final String ACCOUNT_ID_FIELD = "accountId";
    private static final String ACCOUNT_ID_ERROR_MESSAGE =
            "Account ID must contain 1–64 letters or numbers";
    private static final String ACTION_FIELD = "action";
    private static final String INVALID_ACTION_CODE = "INVALID_ACTION";
    private static final String QUANTITY_FIELD = "quantity";

    @Mock private Validator validator;

    private TradeEventService service;

    @BeforeEach
    void setUp() {
        service = new TradeEventService(validator, BATCH_LIMIT);
    }

    @ParameterizedTest
    @MethodSource("nullAndEmptyBatches")
    void shouldRejectNullOrEmptyBatchBeforeValidation(List<TradeEventRequest> events) {
        assertThatThrownBy(() -> service.processTradeEvents(events))
                .isInstanceOf(EmptyBatchException.class);

        verifyNoInteractions(validator);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void shouldRejectNonPositiveBatchLimit(final int limit) {
        assertThatThrownBy(() -> new TradeEventService(validator, limit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum events per request must be positive");

        verifyNoInteractions(validator);
    }

    @Test
    void shouldRejectOversizedBatchBeforeValidation() {
        final TradeEventRequest event = mock(TradeEventRequest.class);
        final List<TradeEventRequest> events = Collections.nCopies(BATCH_LIMIT + 1, event);

        assertThatThrownBy(() -> service.processTradeEvents(events))
                .isInstanceOf(BatchLimitExceededException.class);

        verifyNoInteractions(validator);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, BATCH_LIMIT})
    void shouldProcessValidBatchUpToAndIncludingLimit(final int size) {
        final List<TradeEventRequest> events = new ArrayList<>();

        for (int index = 0; index < size; index++) {
            final TradeEventRequest event = request(index + 1L);
            when(validator.validate(event)).thenReturn(Set.of());
            events.add(event);
        }

        final TradeBatchResponse response = service.processTradeEvents(events);

        assertThat(response.processed()).isEqualTo(size);
        assertThat(response.rejected()).isZero();
        assertThat(response.results()).hasSize(size);

        for (int index = 0; index < size; index++) {
            assertThat(response.results().get(index))
                    .isEqualTo(
                            new TradeEventResult(
                                    index, index + 1L, TradeEventStatus.PROCESSED, List.of()));

            verify(validator).validate(events.get(index));
        }

        verifyNoMoreInteractions(validator);
    }

    @ParameterizedTest(name = "{index}: {0}, {1}, {2}")
    @MethodSource("batchCombinations")
    void shouldHandleEveryCombinationOfValidInvalidAndNullEvents(
            final EventState first, final EventState second, final EventState third) {
        final List<EventState> states = List.of(first, second, third);
        final List<TradeEventRequest> events = new ArrayList<>();
        final List<TradeEventResult> expectedResults = new ArrayList<>();

        final EventValidationError invalidAccountError =
                new EventValidationError(
                        ACCOUNT_ID_FIELD, "INVALID_ACCOUNT_ID", ACCOUNT_ID_ERROR_MESSAGE);

        final EventValidationError nullEventError =
                new EventValidationError("event", "INVALID_EVENT", "Trade event must not be null");

        int expectedProcessed = 0;

        for (int index = 0; index < states.size(); index++) {
            final EventState state = states.get(index);
            final Long eventId = index + 1L;

            if (state == EventState.NULL) {
                events.add(null);
                expectedResults.add(
                        new TradeEventResult(
                                index, null, TradeEventStatus.REJECTED, List.of(nullEventError)));
                continue;
            }

            final TradeEventRequest event = request(eventId);
            events.add(event);

            if (state == EventState.VALID) {
                when(validator.validate(event)).thenReturn(Set.of());
                expectedProcessed++;

                expectedResults.add(
                        new TradeEventResult(
                                index, eventId, TradeEventStatus.PROCESSED, List.of()));
            } else {
                final Set<ConstraintViolation<TradeEventRequest>> violations =
                        Set.of(violation(ACCOUNT_ID_FIELD, ACCOUNT_ID_ERROR_MESSAGE));
                when(validator.validate(event)).thenReturn(violations);

                expectedResults.add(
                        new TradeEventResult(
                                index,
                                eventId,
                                TradeEventStatus.REJECTED,
                                List.of(invalidAccountError)));
            }
        }

        final TradeBatchResponse response = service.processTradeEvents(events);

        assertThat(response.processed()).isEqualTo(expectedProcessed);
        assertThat(response.rejected()).isEqualTo(states.size() - expectedProcessed);
        assertThat(response.results()).containsExactlyElementsOf(expectedResults);

        for (final TradeEventRequest event : events) {
            if (event != null) {
                verify(validator).validate(event);
            }
        }

        verifyNoMoreInteractions(validator);
    }

    @ParameterizedTest
    @MethodSource("validationErrorCodes")
    void shouldMapViolationToFieldCodeAndMessage(
            final String field, final String expectedCode, final String message) {
        final TradeEventRequest event = request(1L);

        final Set<ConstraintViolation<TradeEventRequest>> violations =
                Set.of(violation(field, message));

        when(validator.validate(event)).thenReturn(violations);

        final TradeBatchResponse response = service.processTradeEvents(List.of(event));

        assertThat(response.processed()).isZero();
        assertThat(response.rejected()).isEqualTo(1);
        assertThat(response.results())
                .containsExactly(
                        new TradeEventResult(
                                0,
                                1L,
                                TradeEventStatus.REJECTED,
                                List.of(new EventValidationError(field, expectedCode, message))));
    }

    @Test
    void shouldReturnAllViolationsSortedByFieldThenMessage() {
        final TradeEventRequest event = request(1L);

        final Set<ConstraintViolation<TradeEventRequest>> violations =
                Set.of(
                        violation(
                                QUANTITY_FIELD,
                                "Quantity must be greater than zero for BUY and SELL"),
                        violation(ACTION_FIELD, "Action is required"),
                        violation(ACTION_FIELD, "Action must be BUY, SELL or CANCEL"),
                        violation(ACCOUNT_ID_FIELD, ACCOUNT_ID_ERROR_MESSAGE));

        when(validator.validate(event)).thenReturn(violations);

        final TradeBatchResponse response = service.processTradeEvents(List.of(event));

        assertThat(response.processed()).isZero();
        assertThat(response.rejected()).isEqualTo(1);

        assertThat(response.results().getFirst().errors())
                .containsExactly(
                        new EventValidationError(
                                ACCOUNT_ID_FIELD, "INVALID_ACCOUNT_ID", ACCOUNT_ID_ERROR_MESSAGE),
                        new EventValidationError(
                                ACTION_FIELD, INVALID_ACTION_CODE, "Action is required"),
                        new EventValidationError(
                                ACTION_FIELD,
                                INVALID_ACTION_CODE,
                                "Action must be BUY, SELL or CANCEL"),
                        new EventValidationError(
                                QUANTITY_FIELD,
                                "INVALID_QUANTITY",
                                "Quantity must be greater than zero for BUY and SELL"));
    }

    @Test
    void shouldReturnUnmodifiableResults() {
        final TradeEventRequest event = request(1L);
        when(validator.validate(event)).thenReturn(Set.of());

        final TradeBatchResponse response = service.processTradeEvents(List.of(event));

        assertThatThrownBy(() -> response.results().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static Stream<Arguments> batchCombinations() {
        return Stream.of(EventState.values())
                .flatMap(
                        first ->
                                Stream.of(EventState.values())
                                        .flatMap(
                                                second ->
                                                        Stream.of(EventState.values())
                                                                .map(
                                                                        third ->
                                                                                Arguments.of(
                                                                                        first,
                                                                                        second,
                                                                                        third))));
    }

    private static Stream<Arguments> validationErrorCodes() {
        return Stream.of(
                Arguments.of(ACCOUNT_ID_FIELD, "INVALID_ACCOUNT_ID", ACCOUNT_ID_ERROR_MESSAGE),
                Arguments.of("eventId", "INVALID_EVENT_ID", "Event Id is required"),
                Arguments.of("eventId", "INVALID_EVENT_ID", "Event Id must be greater than zero"),
                Arguments.of(ACTION_FIELD, INVALID_ACTION_CODE, "Action is required"),
                Arguments.of(
                        ACTION_FIELD, INVALID_ACTION_CODE, "Action must be BUY, SELL or CANCEL"),
                Arguments.of(
                        "securityId",
                        "INVALID_SECURITY_ID",
                        "Security Id is required for BUY and SELL"),
                Arguments.of(
                        "securityId",
                        "INVALID_SECURITY_ID",
                        "Security Id must not exceed 128 characters"),
                Arguments.of(
                        "securityId",
                        "INVALID_SECURITY_ID",
                        "Security Id may contain only letters, numbers, hyphens, underscores and dots"),
                Arguments.of(
                        QUANTITY_FIELD,
                        "INVALID_QUANTITY",
                        "Quantity is required for BUY and SELL"),
                Arguments.of(
                        QUANTITY_FIELD,
                        "INVALID_QUANTITY",
                        "Quantity must be greater than zero for BUY and SELL"),
                Arguments.of("otherField", "INVALID_EVENT", "Validation failed"));
    }

    private static TradeEventRequest request(final Long eventId) {
        final TradeEventRequest event = mock(TradeEventRequest.class);
        when(event.eventId()).thenReturn(eventId);
        return event;
    }

    @SuppressWarnings("unchecked")
    private ConstraintViolation<TradeEventRequest> violation(
            final String field, final String message) {
        final ConstraintViolation<TradeEventRequest> violation = mock(ConstraintViolation.class);
        final Path path = mock(Path.class);

        when(path.toString()).thenReturn(field);
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn(message);

        return violation;
    }

    private static Stream<Arguments> nullAndEmptyBatches() {
        return Stream.of(Arguments.of((Object) null), Arguments.of(List.<TradeEventRequest>of()));
    }

    private enum EventState {
        VALID,
        INVALID,
        NULL
    }
}
