package uk.co.jpmc.services.trading.positionbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.ACCOUNT_ID;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.ACCOUNT_ID_FIELD;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.ACTION_FIELD;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.BUY_ACTION;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.EVENT_ID_FIELD;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.INVALID_ACCOUNT_ID_CODE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.INVALID_ACTION_CODE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.INVALID_EVENT_ID_CODE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.INVALID_QUANTITY_CODE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.INVALID_SECURITY_ID_CODE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.QUANTITY_FIELD;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.QUANTITY_NOT_POSITIVE_MESSAGE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.QUANTITY_REQUIRED_MESSAGE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.SECURITY_ID;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.SECURITY_ID_FIELD;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.SECURITY_ID_INVALID_CHARACTERS_MESSAGE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.SECURITY_ID_REQUIRED_MESSAGE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.SECURITY_ID_TOO_LONG_MESSAGE;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
import uk.co.jpmc.services.trading.positionbook.exception.EventRejectedException;
import uk.co.jpmc.services.trading.positionbook.exception.PositionNotFoundException;
import uk.co.jpmc.services.trading.positionbook.model.Position;
import uk.co.jpmc.services.trading.positionbook.model.TradeEvent;
import uk.co.jpmc.services.trading.positionbook.persistence.InMemoryPositionBook;
import uk.co.jpmc.services.trading.positionbook.web.dto.EventValidationError;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeBatchResponse;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeEventRequest;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeEventResult;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeEventStatus;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradePositionResponse;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
class TradeEventServiceTest {

    private static final int BATCH_LIMIT = 3;
    private static final String ACCOUNT_ID_ERROR_MESSAGE =
            "Account ID must contain 1–64 letters or numbers";

    @Mock private Validator validator;
    @Mock private InMemoryPositionBook positionBook;

    private TradeEventService service;

    @BeforeEach
    void setUp() {
        service = new TradeEventService(validator, BATCH_LIMIT, positionBook);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void shouldRejectNonPositiveBatchLimit(final int limit) {
        assertThatThrownBy(() -> new TradeEventService(validator, limit, positionBook))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum events per request must be positive");

        verifyNoInteractions(validator, positionBook);
    }

    @ParameterizedTest
    @MethodSource("nullAndEmptyBatches")
    void shouldRejectNullOrEmptyBatchBeforeValidation(final List<TradeEventRequest> events) {
        assertThatThrownBy(() -> service.processTradeEvents(events))
                .isInstanceOf(EmptyBatchException.class);

        verifyNoInteractions(validator, positionBook);
    }

    @Test
    void shouldRejectOversizedBatchBeforeValidation() {
        final TradeEventRequest event = mock(TradeEventRequest.class);
        final List<TradeEventRequest> events = Collections.nCopies(BATCH_LIMIT + 1, event);

        assertThatThrownBy(() -> service.processTradeEvents(events))
                .isInstanceOf(BatchLimitExceededException.class);

        verifyNoInteractions(validator, positionBook);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, BATCH_LIMIT})
    void shouldProcessValidBatchUpToAndIncludingLimit(final int size) {
        final List<TradeEventRequest> events = new ArrayList<>();

        for (int index = 0; index < size; index++) {
            final TradeEventRequest event = validRequest(index + 1L, BUY_ACTION);
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

        final var ordered = inOrder(positionBook);

        for (int index = 0; index < size; index++) {
            ordered.verify(positionBook)
                    .apply(new TradeEvent(index + 1L, BUY_ACTION, ACCOUNT_ID, SECURITY_ID, 100L));
        }

        verifyNoMoreInteractions(validator, positionBook);
    }

    @ParameterizedTest
    @ValueSource(strings = {BUY_ACTION, "SELL", "CANCEL"})
    void shouldMapValidatedRequestAndApplyEvent(final String action) {
        final TradeEventRequest event = validRequest(1L, action);
        when(validator.validate(event)).thenReturn(Set.of());

        final TradeBatchResponse response = service.processTradeEvents(List.of(event));

        assertThat(response.processed()).isEqualTo(1);
        assertThat(response.rejected()).isZero();
        assertThat(response.results())
                .containsExactly(
                        new TradeEventResult(0, 1L, TradeEventStatus.PROCESSED, List.of()));

        final var ordered = inOrder(validator, positionBook);
        ordered.verify(validator).validate(event);
        ordered.verify(positionBook)
                .apply(new TradeEvent(1L, action, ACCOUNT_ID, SECURITY_ID, 100L));

        verifyNoMoreInteractions(validator, positionBook);
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
                        ACCOUNT_ID_FIELD, INVALID_ACCOUNT_ID_CODE, ACCOUNT_ID_ERROR_MESSAGE);

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

            final TradeEventRequest event =
                    state == EventState.VALID
                            ? validRequest(eventId, BUY_ACTION)
                            : requestWithOnlyEventId(eventId);

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

        for (int index = 0; index < states.size(); index++) {
            if (states.get(index) == EventState.VALID) {
                verify(positionBook)
                        .apply(
                                new TradeEvent(
                                        index + 1L, BUY_ACTION, ACCOUNT_ID, SECURITY_ID, 100L));
            }
        }

        verifyNoMoreInteractions(validator, positionBook);
    }

    @ParameterizedTest
    @MethodSource("validationErrorCodes")
    void shouldMapViolationToFieldCodeAndMessage(
            final String field, final String expectedCode, final String message) {
        final TradeEventRequest event = requestWithOnlyEventId(1L);

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

        verifyNoInteractions(positionBook);
    }

    @Test
    void shouldReturnAllViolationsSortedByFieldThenMessage() {
        final TradeEventRequest event = requestWithOnlyEventId(1L);

        final Set<ConstraintViolation<TradeEventRequest>> violations =
                Set.of(
                        violation(QUANTITY_FIELD, QUANTITY_NOT_POSITIVE_MESSAGE),
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
                                ACCOUNT_ID_FIELD,
                                INVALID_ACCOUNT_ID_CODE,
                                ACCOUNT_ID_ERROR_MESSAGE),
                        new EventValidationError(
                                ACTION_FIELD, INVALID_ACTION_CODE, "Action is required"),
                        new EventValidationError(
                                ACTION_FIELD,
                                INVALID_ACTION_CODE,
                                "Action must be BUY, SELL or CANCEL"),
                        new EventValidationError(
                                QUANTITY_FIELD,
                                INVALID_QUANTITY_CODE,
                                QUANTITY_NOT_POSITIVE_MESSAGE));

        verifyNoInteractions(positionBook);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "DUPLICATE_EVENT_ID",
                "UNKNOWN_EVENT_ID",
                "TRADE_ALREADY_CANCELLED",
                "ACCOUNT_MISMATCH",
                "NEGATIVE_POSITION",
                "POSITION_OVERFLOW"
            })
    void shouldMapPositionBookRejectionAndContinueProcessing(final String code) {
        final TradeEventRequest first = validRequest(1L, BUY_ACTION);
        final TradeEventRequest second = validRequest(2L, BUY_ACTION);
        final TradeEventRequest third = validRequest(3L, BUY_ACTION);

        when(validator.validate(first)).thenReturn(Set.of());
        when(validator.validate(second)).thenReturn(Set.of());
        when(validator.validate(third)).thenReturn(Set.of());

        final TradeEvent firstTrade = new TradeEvent(1L, BUY_ACTION, ACCOUNT_ID, SECURITY_ID, 100L);
        final TradeEvent secondTrade =
                new TradeEvent(2L, BUY_ACTION, ACCOUNT_ID, SECURITY_ID, 100L);
        final TradeEvent thirdTrade = new TradeEvent(3L, BUY_ACTION, ACCOUNT_ID, SECURITY_ID, 100L);

        final String message = "The event cannot be applied";

        doAnswer(
                        invocation -> {
                            final TradeEvent tradeEvent = invocation.getArgument(0);
                            if (secondTrade.equals(tradeEvent)) {
                                throw new EventRejectedException(code, message);
                            }
                            return null;
                        })
                .when(positionBook)
                .apply(any(TradeEvent.class));

        final TradeBatchResponse response =
                service.processTradeEvents(List.of(first, second, third));

        assertThat(response.processed()).isEqualTo(2);
        assertThat(response.rejected()).isEqualTo(1);
        assertThat(response.results())
                .containsExactly(
                        new TradeEventResult(0, 1L, TradeEventStatus.PROCESSED, List.of()),
                        new TradeEventResult(
                                1,
                                2L,
                                TradeEventStatus.REJECTED,
                                List.of(new EventValidationError("event", code, message))),
                        new TradeEventResult(2, 3L, TradeEventStatus.PROCESSED, List.of()));

        final var ordered = inOrder(positionBook);
        ordered.verify(positionBook).apply(firstTrade);
        ordered.verify(positionBook).apply(secondTrade);
        ordered.verify(positionBook).apply(thirdTrade);

        verifyNoMoreInteractions(positionBook);
    }

    @Test
    void shouldPropagateUnexpectedProcessingFailure() {
        final TradeEventRequest event = validRequest(1L, BUY_ACTION);
        when(validator.validate(event)).thenReturn(Set.of());

        final IllegalStateException failure =
                new IllegalStateException("Unexpected processing failure");

        doAnswer(
                        invocation -> {
                            throw failure;
                        })
                .when(positionBook)
                .apply(any(TradeEvent.class));

        assertThatThrownBy(() -> service.processTradeEvents(List.of(event))).isSameAs(failure);

        verify(validator).validate(event);
        verify(positionBook).apply(new TradeEvent(1L, BUY_ACTION, ACCOUNT_ID, SECURITY_ID, 100L));
        verifyNoMoreInteractions(validator, positionBook);
    }

    @Test
    void shouldStopBatchWhenUnexpectedFailureOccursAfterSuccessfulEvent() {
        final TradeEventRequest first = validRequest(1L, BUY_ACTION);
        final TradeEventRequest second = validRequest(2L, BUY_ACTION);
        final TradeEventRequest third = validRequest(3L, BUY_ACTION);

        when(validator.validate(first)).thenReturn(Set.of());
        when(validator.validate(second)).thenReturn(Set.of());

        final TradeEvent firstTrade = new TradeEvent(1L, BUY_ACTION, "ACC1", "SEC1", 100L);
        final TradeEvent secondTrade = new TradeEvent(2L, BUY_ACTION, "ACC1", "SEC1", 100L);

        final IllegalStateException failure =
                new IllegalStateException("Unexpected processing failure");

        doAnswer(
                        invocation -> {
                            final TradeEvent trade = invocation.getArgument(0);
                            if (secondTrade.equals(trade)) {
                                throw failure;
                            }
                            return null;
                        })
                .when(positionBook)
                .apply(any(TradeEvent.class));

        assertThatThrownBy(() -> service.processTradeEvents(List.of(first, second, third)))
                .isSameAs(failure);

        final var ordered = inOrder(validator, positionBook);
        ordered.verify(validator).validate(first);
        ordered.verify(positionBook).apply(firstTrade);
        ordered.verify(validator).validate(second);
        ordered.verify(positionBook).apply(secondTrade);

        verifyNoMoreInteractions(validator, positionBook);
    }

    @Test
    void shouldReturnUnmodifiableResults() {
        final TradeEventRequest event = validRequest(1L, BUY_ACTION);
        when(validator.validate(event)).thenReturn(Set.of());

        final TradeBatchResponse response = service.processTradeEvents(List.of(event));

        assertThatThrownBy(() -> response.results().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 150L})
    void shouldMapStoredPositionToResponse(final long quantity) {
        when(positionBook.findPosition(ACCOUNT_ID, SECURITY_ID))
                .thenReturn(Optional.of(new Position(ACCOUNT_ID, SECURITY_ID, quantity)));

        final TradePositionResponse response = service.retrievePosition(ACCOUNT_ID, SECURITY_ID);

        assertThat(response)
                .isEqualTo(new TradePositionResponse(ACCOUNT_ID, SECURITY_ID, quantity));

        verify(positionBook).findPosition(ACCOUNT_ID, SECURITY_ID);
        verifyNoMoreInteractions(positionBook);
        verifyNoInteractions(validator);
    }

    @Test
    void shouldThrowWhenPositionDoesNotExist() {
        when(positionBook.findPosition(ACCOUNT_ID, SECURITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retrievePosition(ACCOUNT_ID, SECURITY_ID))
                .isInstanceOf(PositionNotFoundException.class)
                .hasMessage("No position exists for the specified account and security");

        verify(positionBook).findPosition(ACCOUNT_ID, SECURITY_ID);
        verifyNoMoreInteractions(positionBook);
        verifyNoInteractions(validator);
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
                Arguments.of(ACCOUNT_ID_FIELD, INVALID_ACCOUNT_ID_CODE, ACCOUNT_ID_ERROR_MESSAGE),
                Arguments.of(EVENT_ID_FIELD, INVALID_EVENT_ID_CODE, "Event Id is required"),
                Arguments.of(
                        EVENT_ID_FIELD,
                        INVALID_EVENT_ID_CODE,
                        "Event Id must be greater than zero"),
                Arguments.of(ACTION_FIELD, INVALID_ACTION_CODE, "Action is required"),
                Arguments.of(
                        ACTION_FIELD, INVALID_ACTION_CODE, "Action must be BUY, SELL or CANCEL"),
                Arguments.of(
                        SECURITY_ID_FIELD, INVALID_SECURITY_ID_CODE, SECURITY_ID_REQUIRED_MESSAGE),
                Arguments.of(
                        SECURITY_ID_FIELD, INVALID_SECURITY_ID_CODE, SECURITY_ID_TOO_LONG_MESSAGE),
                Arguments.of(
                        SECURITY_ID_FIELD,
                        INVALID_SECURITY_ID_CODE,
                        SECURITY_ID_INVALID_CHARACTERS_MESSAGE),
                Arguments.of(QUANTITY_FIELD, INVALID_QUANTITY_CODE, QUANTITY_REQUIRED_MESSAGE),
                Arguments.of(QUANTITY_FIELD, INVALID_QUANTITY_CODE, QUANTITY_NOT_POSITIVE_MESSAGE),
                Arguments.of("otherField", "INVALID_EVENT", "Validation failed"));
    }

    private static TradeEventRequest validRequest(final Long eventId, final String action) {

        return new TradeEventRequest(eventId, action, ACCOUNT_ID, SECURITY_ID, 100L);
    }

    private static TradeEventRequest requestWithOnlyEventId(final Long eventId) {
        return new TradeEventRequest(eventId, null, null, null, null);
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
