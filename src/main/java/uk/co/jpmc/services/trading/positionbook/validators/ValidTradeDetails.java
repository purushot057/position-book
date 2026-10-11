package uk.co.jpmc.services.trading.positionbook.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TradeDetailsValidator.class)
public @interface ValidTradeDetails {

    String message() default "Invalid trade details";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
