package uk.co.jpmc.services.trading.positionbook.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.ACCOUNT_ID;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.ACCOUNT_MISMATCH_CODE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.ALTERNATE_SECURITY_ID;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.BUY_ACTION;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.CANCEL_ACTION;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.DUPLICATE_EVENT_ID_CODE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.NEGATIVE_POSITION_CODE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.POSITION_OVERFLOW_CODE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.SECONDARY_ACCOUNT_ID;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.SECONDARY_SECURITY_ID;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.SECURITY_ID;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.SELL_ACTION;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.TRADE_ALREADY_CANCELLED_CODE;
import static uk.co.jpmc.services.trading.positionbook.utilities.PositionBookFixtures.UNKNOWN_EVENT_ID_CODE;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import uk.co.jpmc.services.trading.positionbook.exception.EventRejectedException;
import uk.co.jpmc.services.trading.positionbook.model.Position;
import uk.co.jpmc.services.trading.positionbook.model.TradeEvent;

@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
class InMemoryPositionBookTest {

    private static final int CONCURRENT_EVENT_COUNT = 100;
    private static final int WORKER_COUNT = 8;
    private static final int TIMEOUT_SECONDS = 10;

    private InMemoryPositionBook positionBook;

    @BeforeEach
    void setUp() {
        positionBook = createPositionBook(false);
    }

    // Arithmetic
    @Test
    void shouldAggregateBuyEvents() {
        positionBook.apply(event(1L, BUY_ACTION, 100L));
        positionBook.apply(event(2L, BUY_ACTION, 50L));

        assertPositionQuantity(150L);
    }

    @Test
    void shouldSubtractSellQuantity() {
        positionBook.apply(event(10L, BUY_ACTION, 100L));
        positionBook.apply(event(11L, SELL_ACTION, 50L));

        assertPositionQuantity(50L);
    }

    @Test
    void shouldKeepPositionsSeparateByAccountAndSecurity() {
        positionBook.apply(new TradeEvent(3L, BUY_ACTION, ACCOUNT_ID, SECURITY_ID, 12L));
        positionBook.apply(new TradeEvent(4L, BUY_ACTION, ACCOUNT_ID, ALTERNATE_SECURITY_ID, 50L));
        positionBook.apply(
                new TradeEvent(5L, BUY_ACTION, SECONDARY_ACCOUNT_ID, ALTERNATE_SECURITY_ID, 33L));

        assertThat(positionBook.findPosition(ACCOUNT_ID, SECURITY_ID))
                .contains(new Position(ACCOUNT_ID, SECURITY_ID, 12L));
        assertThat(positionBook.findPosition(ACCOUNT_ID, ALTERNATE_SECURITY_ID))
                .contains(new Position(ACCOUNT_ID, ALTERNATE_SECURITY_ID, 50L));
        assertThat(positionBook.findPosition(SECONDARY_ACCOUNT_ID, ALTERNATE_SECURITY_ID))
                .contains(new Position(SECONDARY_ACCOUNT_ID, ALTERNATE_SECURITY_ID, 33L));
        assertThat(positionBook.findPosition(SECONDARY_ACCOUNT_ID, SECURITY_ID)).isEmpty();
    }

    @Test
    void shouldRetainZeroPositionAfterSellingEntireHolding() {
        positionBook.apply(event(1L, BUY_ACTION, 100L));
        positionBook.apply(event(2L, SELL_ACTION, 100L));

        assertPositionQuantity(0L);
    }

    // Cancellation
    @Test
    void shouldCancelBuyAndProcessSubsequentTrade() {
        positionBook.apply(event(21L, BUY_ACTION, 100L));
        positionBook.apply(cancellation(21L));
        positionBook.apply(event(22L, BUY_ACTION, 5L));

        assertPositionQuantity(5L);
    }

    @Test
    void shouldReverseSellOnCancellation() {
        positionBook.apply(event(1L, BUY_ACTION, 100L));
        positionBook.apply(event(2L, SELL_ACTION, 40L));

        assertPositionQuantity(60L);

        positionBook.apply(cancellation(2L));

        assertPositionQuantity(100L);
    }

    @Test
    void shouldIgnoreCancellationSecurityAndQuantity() {
        positionBook.apply(event(1L, BUY_ACTION, 100L));

        positionBook.apply(new TradeEvent(1L, CANCEL_ACTION, ACCOUNT_ID, "OTHER", -999L));

        assertPositionQuantity(0L);
        assertThat(positionBook.findPosition(ACCOUNT_ID, "OTHER")).isEmpty();
    }

    @Test
    void shouldRetainZeroPositionAfterCancellation() {
        positionBook.apply(event(1L, BUY_ACTION, 100L));
        positionBook.apply(cancellation(1L));

        assertPositionQuantity(0L);
    }

    // Duplicate IDs and other rejections
    @ParameterizedTest
    @ValueSource(strings = {BUY_ACTION, SELL_ACTION})
    void shouldRejectDuplicateTradeIdWithoutChangingPosition(final String action) {

        positionBook.apply(event(1L, BUY_ACTION, 100L));

        assertRejected(event(1L, action, 25L), DUPLICATE_EVENT_ID_CODE);

        assertPositionQuantity(100L);
    }

    @Test
    void shouldRejectDuplicateTradeIdAcrossDifferentPositions() {
        positionBook.apply(event(1L, BUY_ACTION, 100L));

        assertRejected(
                new TradeEvent(1L, BUY_ACTION, SECONDARY_ACCOUNT_ID, SECONDARY_SECURITY_ID, 50L),
                DUPLICATE_EVENT_ID_CODE);

        assertPositionQuantity(100L);
        assertThat(positionBook.findPosition(SECONDARY_ACCOUNT_ID, SECONDARY_SECURITY_ID))
                .isEmpty();
    }

    @Test
    void shouldRejectReuseOfCancelledTradeId() {
        positionBook.apply(event(1L, BUY_ACTION, 100L));
        positionBook.apply(cancellation(1L));

        assertRejected(event(1L, BUY_ACTION, 50L), DUPLICATE_EVENT_ID_CODE);

        assertPositionQuantity(0L);
    }

    @Test
    void shouldRejectUnknownCancellationWithoutReservingId() {
        assertRejected(cancellation(1L), UNKNOWN_EVENT_ID_CODE);

        assertThat(positionBook.findPosition(ACCOUNT_ID, SECURITY_ID)).isEmpty();

        positionBook.apply(event(1L, BUY_ACTION, 100L));
        positionBook.apply(cancellation(1L));

        assertPositionQuantity(0L);
    }

    @Test
    void shouldRejectRepeatedCancellationWithoutChangingPosition() {
        positionBook.apply(event(1L, BUY_ACTION, 100L));
        positionBook.apply(cancellation(1L));

        assertRejected(cancellation(1L), TRADE_ALREADY_CANCELLED_CODE);

        assertPositionQuantity(0L);
    }

    @Test
    void shouldRejectCancellationForDifferentAccountWithoutCancellingTrade() {
        positionBook.apply(event(1L, BUY_ACTION, 100L));

        assertRejected(
                new TradeEvent(1L, CANCEL_ACTION, SECONDARY_ACCOUNT_ID, null, null),
                ACCOUNT_MISMATCH_CODE);

        assertPositionQuantity(100L);
        assertThat(positionBook.findPosition(SECONDARY_ACCOUNT_ID, SECURITY_ID)).isEmpty();

        positionBook.apply(cancellation(1L));

        assertPositionQuantity(0L);
    }

    @Test
    void shouldRejectUnsupportedActionWithoutReservingId() {
        assertRejected(event(1L, "PURCHASE", 100L), "INVALID_ACTION");

        assertThat(positionBook.findPosition(ACCOUNT_ID, SECURITY_ID)).isEmpty();

        positionBook.apply(event(1L, BUY_ACTION, 100L));

        assertPositionQuantity(100L);
    }

    // Negative-position policy
    @Test
    void shouldRejectSellThatWouldCreateNegativePosition() {
        positionBook.apply(event(1L, BUY_ACTION, 50L));

        assertRejected(event(2L, SELL_ACTION, 80L), NEGATIVE_POSITION_CODE);

        assertPositionQuantity(50L);

        // A rejected trade must not reserve its ID.
        positionBook.apply(event(3L, BUY_ACTION, 30L));
        positionBook.apply(event(2L, SELL_ACTION, 80L));

        assertPositionQuantity(0L);
    }

    @Test
    void shouldRejectSellForNewPositionWithoutCreatingPosition() {
        assertRejected(event(1L, SELL_ACTION, 10L), NEGATIVE_POSITION_CODE);

        assertThat(positionBook.findPosition(ACCOUNT_ID, SECURITY_ID)).isEmpty();

        positionBook.apply(event(1L, BUY_ACTION, 10L));

        assertPositionQuantity(10L);
    }

    @Test
    void shouldRejectCancellationThatWouldCreateNegativePosition() {
        positionBook.apply(event(1L, BUY_ACTION, 100L));
        positionBook.apply(event(2L, SELL_ACTION, 80L));

        assertRejected(cancellation(1L), NEGATIVE_POSITION_CODE);

        assertPositionQuantity(20L);

        // A rejected cancellation must leave the original trade active.
        positionBook.apply(event(3L, BUY_ACTION, 80L));
        positionBook.apply(cancellation(1L));

        assertPositionQuantity(0L);
    }

    @Test
    void shouldAllowNegativePositionsWhenConfigured() {
        positionBook = createPositionBook(true);

        positionBook.apply(event(1L, SELL_ACTION, 80L));

        assertPositionQuantity(-80L);

        positionBook.apply(cancellation(1L));

        assertPositionQuantity(0L);
    }

    @Test
    void shouldAllowCancellationToCreateNegativePositionWhenConfigured() {
        positionBook = createPositionBook(true);

        positionBook.apply(event(1L, BUY_ACTION, 100L));
        positionBook.apply(event(2L, SELL_ACTION, 80L));
        positionBook.apply(cancellation(1L));

        assertPositionQuantity(-80L);
    }

    // Overflow
    @Test
    void shouldRejectBuyOverflowWithoutChangingPositionOrReservingId() {
        positionBook.apply(event(1L, BUY_ACTION, Long.MAX_VALUE));

        assertThatThrownBy(() -> positionBook.apply(event(2L, BUY_ACTION, 1L)))
                .isInstanceOf(EventRejectedException.class)
                .hasCauseInstanceOf(ArithmeticException.class)
                .satisfies(
                        exception ->
                                assertThat(((EventRejectedException) exception).getCode())
                                        .isEqualTo(POSITION_OVERFLOW_CODE));

        assertPositionQuantity(Long.MAX_VALUE);

        positionBook.apply(event(3L, SELL_ACTION, 1L));
        positionBook.apply(event(2L, BUY_ACTION, 1L));

        assertPositionQuantity(Long.MAX_VALUE);
    }

    @Test
    void shouldRejectSellUnderflowEvenWhenNegativePositionsAreAllowed() {
        positionBook = createPositionBook(true);

        positionBook.apply(event(1L, SELL_ACTION, Long.MAX_VALUE));
        positionBook.apply(event(2L, SELL_ACTION, 1L));

        assertPositionQuantity(Long.MIN_VALUE);

        assertRejected(event(3L, SELL_ACTION, 1L), POSITION_OVERFLOW_CODE);

        assertPositionQuantity(Long.MIN_VALUE);

        positionBook.apply(event(4L, BUY_ACTION, 1L));
        positionBook.apply(event(3L, SELL_ACTION, 1L));

        assertPositionQuantity(Long.MIN_VALUE);
    }

    @Test
    void shouldRejectCancellationOverflowWithoutCancellingTrade() {
        positionBook.apply(event(1L, BUY_ACTION, Long.MAX_VALUE));
        positionBook.apply(event(2L, SELL_ACTION, 1L));
        positionBook.apply(event(3L, BUY_ACTION, 1L));

        assertRejected(cancellation(2L), POSITION_OVERFLOW_CODE);

        assertPositionQuantity(Long.MAX_VALUE);

        positionBook.apply(event(4L, SELL_ACTION, 1L));
        positionBook.apply(cancellation(2L));

        assertPositionQuantity(Long.MAX_VALUE);
    }

    @Test
    void shouldRejectCancellationUnderflowWithoutCancellingTrade() {
        positionBook = createPositionBook(true);

        positionBook.apply(event(1L, BUY_ACTION, 1L));
        positionBook.apply(event(2L, SELL_ACTION, Long.MAX_VALUE));
        positionBook.apply(event(3L, SELL_ACTION, 2L));

        assertPositionQuantity(Long.MIN_VALUE);

        assertRejected(cancellation(1L), POSITION_OVERFLOW_CODE);

        assertPositionQuantity(Long.MIN_VALUE);

        positionBook.apply(event(4L, BUY_ACTION, 1L));
        positionBook.apply(cancellation(1L));

        assertPositionQuantity(Long.MIN_VALUE);
    }

    // Position retrieval
    @Test
    void shouldReturnEmptyWhenPositionDoesNotExist() {
        assertThat(positionBook.findPosition(ACCOUNT_ID, SECURITY_ID)).isEmpty();
    }

    @Test
    void shouldReturnPositionSnapshotUnaffectedByLaterUpdates() {
        positionBook.apply(event(1L, BUY_ACTION, 100L));

        final Position snapshot = positionBook.findPosition(ACCOUNT_ID, SECURITY_ID).orElseThrow();

        positionBook.apply(event(2L, BUY_ACTION, 50L));

        assertThat(snapshot).isEqualTo(new Position(ACCOUNT_ID, SECURITY_ID, 100L));
        assertPositionQuantity(150L);
    }

    // Concurrency
    @Test
    void shouldApplyConcurrentTradesWithoutLosingUpdates() throws Exception {
        final List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int index = 0; index < CONCURRENT_EVENT_COUNT; index++) {
            final long eventId = index + 1L;

            tasks.add(
                    () -> {
                        positionBook.apply(event(eventId, BUY_ACTION, 1L));
                        return true;
                    });
        }

        assertThat(runConcurrently(tasks)).hasSize(CONCURRENT_EVENT_COUNT).containsOnly(true);

        assertPositionQuantity(CONCURRENT_EVENT_COUNT);
    }

    @Test
    void shouldAcceptOnlyOneConcurrentTradeWithSameIdAcrossPositions() throws Exception {

        final TradeEvent first = new TradeEvent(1L, BUY_ACTION, ACCOUNT_ID, SECURITY_ID, 10L);
        final TradeEvent second =
                new TradeEvent(1L, BUY_ACTION, SECONDARY_ACCOUNT_ID, SECONDARY_SECURITY_ID, 20L);

        final List<Boolean> outcomes =
                runConcurrently(
                        List.of(
                                () -> applyOrReject(first, DUPLICATE_EVENT_ID_CODE),
                                () -> applyOrReject(second, DUPLICATE_EVENT_ID_CODE)));

        assertThat(outcomes).containsExactlyInAnyOrder(true, false);

        final var firstPosition = positionBook.findPosition(ACCOUNT_ID, SECURITY_ID);
        final var secondPosition =
                positionBook.findPosition(SECONDARY_ACCOUNT_ID, SECONDARY_SECURITY_ID);

        assertThat(firstPosition.isPresent() ^ secondPosition.isPresent()).isTrue();

        firstPosition.ifPresent(position -> assertThat(position.quantity()).isEqualTo(10L));
        secondPosition.ifPresent(position -> assertThat(position.quantity()).isEqualTo(20L));
    }

    @Test
    void shouldApplyConcurrentCancellationOnlyOnce() throws Exception {
        positionBook.apply(event(1L, BUY_ACTION, 100L));

        final List<Boolean> outcomes =
                runConcurrently(
                        List.of(
                                () -> applyOrReject(cancellation(1L), TRADE_ALREADY_CANCELLED_CODE),
                                () ->
                                        applyOrReject(
                                                cancellation(1L), TRADE_ALREADY_CANCELLED_CODE)));

        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertPositionQuantity(0L);
    }

    private void assertPositionQuantity(final long expectedQuantity) {
        assertThat(positionBook.findPosition(ACCOUNT_ID, SECURITY_ID))
                .contains(new Position(ACCOUNT_ID, SECURITY_ID, expectedQuantity));
    }

    private void assertRejected(final TradeEvent event, final String expectedCode) {

        assertThatThrownBy(() -> positionBook.apply(event))
                .isInstanceOf(EventRejectedException.class)
                .satisfies(
                        exception ->
                                assertThat(((EventRejectedException) exception).getCode())
                                        .isEqualTo(expectedCode));
    }

    private boolean applyOrReject(final TradeEvent event, final String expectedCode) {

        try {
            positionBook.apply(event);
            return true;
        } catch (EventRejectedException exception) {
            assertThat(exception.getCode()).isEqualTo(expectedCode);
            return false;
        }
    }

    private List<Boolean> runConcurrently(final List<Callable<Boolean>> tasks) throws Exception {

        final CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(WORKER_COUNT)) {
            final List<Future<Boolean>> futures = new ArrayList<>();

            for (final Callable<Boolean> task : tasks) {
                futures.add(
                        executor.submit(
                                () -> {
                                    if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException(
                                                "Timed out waiting for concurrent test start");
                                    }
                                    return task.call();
                                }));
            }

            start.countDown();

            final List<Boolean> outcomes = new ArrayList<>();
            for (final var future : futures) {
                outcomes.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }

            return outcomes;
        }
    }

    private static InMemoryPositionBook createPositionBook(final boolean allowNegativePositions) {

        final InMemoryPositionBook book = new InMemoryPositionBook();
        ReflectionTestUtils.setField(book, "allowNegativePositions", allowNegativePositions);

        return book;
    }

    private static TradeEvent event(final long eventId, final String action, final long quantity) {

        return new TradeEvent(eventId, action, ACCOUNT_ID, SECURITY_ID, quantity);
    }

    private static TradeEvent cancellation(final long eventId) {
        return new TradeEvent(eventId, CANCEL_ACTION, ACCOUNT_ID, null, null);
    }
}
