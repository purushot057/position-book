package uk.co.jpmc.services.trading.positionbook.validators;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeEventRequest;

class TradeDetailsValidatorTest {

    private static final String SECURITY_REQUIRED = "Security Id is required for BUY and SELL";
    private static final String SECURITY_TOO_LONG = "Security Id must not exceed 128 characters";
    private static final String SECURITY_INVALID_CHARACTERS =
            "Security Id may contain only letters, numbers, hyphens, underscores and dots";
    private static final String QUANTITY_REQUIRED = "Quantity is required for BUY and SELL";
    private static final String QUANTITY_NOT_POSITIVE =
            "Quantity must be greater than zero for BUY and SELL";

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("buyAndSellCases")
    void shouldValidateSecurityIdAndQuantityForBuyAndSell(final ValidationCase testCase) {
        final TradeEventRequest event =
                event(testCase.action(), testCase.securityId(), testCase.quantity());

        assertThat(tradeDetailsErrors(event))
                .containsExactlyInAnyOrderElementsOf(testCase.expectedErrors());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("cancelCases")
    void shouldIgnoreSecurityIdAndQuantityForCancel(final ValidationCase testCase) {
        final TradeEventRequest event = event("CANCEL", testCase.securityId(), testCase.quantity());

        assertThat(tradeDetailsErrors(event)).isEmpty();
    }

    @Test
    void shouldAllowNullEventForServiceToHandle() {
        final TradeDetailsValidator constraintValidator = new TradeDetailsValidator();

        // The null-event branch must return before accessing the context.
        assertThat(constraintValidator.isValid(null, null)).isTrue();
    }

    @Test
    void shouldLeaveMissingActionToActionValidation() {
        final TradeEventRequest event = event(null, null, null);

        assertThat(tradeDetailsErrors(event)).isEmpty();
    }

    @Test
    void shouldLeaveUnknownActionToActionValidation() {
        final TradeEventRequest event = event("UNKNOWN", null, null);

        assertThat(tradeDetailsErrors(event)).isEmpty();
    }

    private static Stream<ValidationCase> buyAndSellCases() {
        return Stream.of("BUY", "SELL")
                .flatMap(
                        action ->
                                securityIdCases().stream()
                                        .flatMap(
                                                security ->
                                                        quantityCases().stream()
                                                                .map(
                                                                        quantity ->
                                                                                validationCase(
                                                                                        action,
                                                                                        security,
                                                                                        quantity))));
    }

    private static Stream<ValidationCase> cancelCases() {
        return securityIdCases().stream()
                .flatMap(
                        security ->
                                quantityCases().stream()
                                        .map(
                                                quantity ->
                                                        validationCase(
                                                                "CANCEL", security, quantity)));
    }

    private static List<SecurityCase> securityIdCases() {
        return List.of(
                new SecurityCase("missing", null, SECURITY_REQUIRED),
                new SecurityCase("empty", "", SECURITY_REQUIRED),
                new SecurityCase("spaces", "   ", SECURITY_REQUIRED),
                new SecurityCase("tabs/newlines", "\t\n", SECURITY_REQUIRED),
                new SecurityCase("letters", "SECXYZ", null),
                new SecurityCase("lowercase", "secxyz", null),
                new SecurityCase("numbers", "123", null),
                new SecurityCase("letters/numbers", "SEC1", null),
                new SecurityCase("hyphen", "SEC-1", null),
                new SecurityCase("underscore", "SEC_1", null),
                new SecurityCase("dot", "SEC.1", null),
                new SecurityCase("mixed punctuation", "SEC-1_X.Y", null),
                new SecurityCase("one character", "S", null),
                new SecurityCase("128 characters", "S".repeat(128), null),
                new SecurityCase("129 characters", "S".repeat(129), SECURITY_TOO_LONG),
                new SecurityCase("embedded space", "SEC 1", SECURITY_INVALID_CHARACTERS),
                new SecurityCase("leading space", " SEC1", SECURITY_INVALID_CHARACTERS),
                new SecurityCase("trailing space", "SEC1 ", SECURITY_INVALID_CHARACTERS),
                new SecurityCase("at sign", "SEC@1", SECURITY_INVALID_CHARACTERS),
                new SecurityCase("non-ASCII letter", "SÉC1", SECURITY_INVALID_CHARACTERS),
                new SecurityCase(
                        "length takes precedence over format", "@".repeat(129), SECURITY_TOO_LONG));
    }

    private static List<QuantityCase> quantityCases() {
        return List.of(
                new QuantityCase("missing", null, QUANTITY_REQUIRED),
                new QuantityCase("minimum long", Long.MIN_VALUE, QUANTITY_NOT_POSITIVE),
                new QuantityCase("negative", -1L, QUANTITY_NOT_POSITIVE),
                new QuantityCase("zero", 0L, QUANTITY_NOT_POSITIVE),
                new QuantityCase("one", 1L, null),
                new QuantityCase("positive", 100L, null),
                new QuantityCase("maximum long", Long.MAX_VALUE, null));
    }

    private static ValidationCase validationCase(
            final String action, final SecurityCase security, final QuantityCase quantity) {
        final List<FieldError> errors = new ArrayList<>();

        if (security.message() != null) {
            errors.add(new FieldError("securityId", security.message()));
        }
        if (quantity.message() != null) {
            errors.add(new FieldError("quantity", quantity.message()));
        }

        return new ValidationCase(
                action + ": security=" + security.name() + ", quantity=" + quantity.name(),
                action,
                security.value(),
                quantity.value(),
                List.copyOf(errors));
    }

    private static List<FieldError> tradeDetailsErrors(final TradeEventRequest event) {
        return validator.validate(event).stream()
                // Isolate this constraint from account, event Id and action rules.
                .filter(
                        violation ->
                                violation.getConstraintDescriptor().getAnnotation()
                                        instanceof ValidTradeDetails)
                .map(
                        violation ->
                                new FieldError(
                                        violation.getPropertyPath().toString(),
                                        violation.getMessage()))
                .toList();
    }

    private static TradeEventRequest event(
            final String action, final String securityId, final Long quantity) {
        return new TradeEventRequest(1L, action, "ACC1", securityId, quantity);
    }

    private record FieldError(String field, String message) {}

    private record SecurityCase(String name, String value, String message) {}

    private record QuantityCase(String name, Long value, String message) {}

    private record ValidationCase(
            String name,
            String action,
            String securityId,
            Long quantity,
            List<FieldError> expectedErrors) {
        @Override
        public String toString() {
            return name;
        }
    }
}
