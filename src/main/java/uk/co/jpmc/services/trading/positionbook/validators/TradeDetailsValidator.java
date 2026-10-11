package uk.co.jpmc.services.trading.positionbook.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;
import uk.co.jpmc.services.trading.positionbook.util.PositionBookConstants;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeEventRequest;

/**
 * Validates security Id and quantity for BUY and SELL events.
 *
 * <p>These rules depend on the action: CANCEL events ignore both fields. A class-level validator
 * allows the action to be checked before applying the constraints, while reporting errors against
 * the individual fields.
 */
public class TradeDetailsValidator
        implements ConstraintValidator<ValidTradeDetails, TradeEventRequest> {

    private static final int MAX_SECURITY_ID_LENGTH = 128;
    private static final Pattern SECURITY_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");

    @Override
    public boolean isValid(
            final TradeEventRequest event, final ConstraintValidatorContext context) {
        if (event == null) {
            return true; // Null events are handled by the service.
        }

        // Works whether action is currently a String or an enum.
        final String action = String.valueOf(event.action());

        if (!PositionBookConstants.BUY_ACTION.equals(action)
                && !PositionBookConstants.SELL_ACTION.equals(action)) {
            return true; // Action validation handles missing, unknown and CANCEL actions.
        }

        context.disableDefaultConstraintViolation();

        boolean valid = true;
        final String securityId = event.securityId();

        if (securityId == null || securityId.isBlank()) {
            addViolation(
                    context,
                    PositionBookConstants.SECURITY_ID_FIELD,
                    "Security Id is required for BUY and SELL");
            valid = false;
        } else if (securityId.length() > MAX_SECURITY_ID_LENGTH) {
            addViolation(
                    context,
                    PositionBookConstants.SECURITY_ID_FIELD,
                    "Security Id must not exceed 128 characters");
            valid = false;
        } else if (!SECURITY_ID_PATTERN.matcher(securityId).matches()) {
            addViolation(
                    context,
                    PositionBookConstants.SECURITY_ID_FIELD,
                    "Security Id may contain only letters, numbers, hyphens, underscores and dots");
            valid = false;
        }

        final Long quantity = event.quantity();

        if (quantity == null) {
            addViolation(context, "quantity", "Quantity is required for BUY and SELL");
            valid = false;
        } else if (quantity <= 0) {
            addViolation(
                    context, "quantity", "Quantity must be greater than zero for BUY and SELL");
            valid = false;
        }

        return valid;
    }

    private void addViolation(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
