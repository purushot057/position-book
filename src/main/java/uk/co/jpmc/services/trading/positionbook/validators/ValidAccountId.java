package uk.co.jpmc.services.trading.positionbook.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target({
    ElementType.FIELD,
    ElementType.METHOD,
    ElementType.PARAMETER,
    ElementType.ANNOTATION_TYPE,
    ElementType.RECORD_COMPONENT
})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@NotBlank(message = "Account Id is required")
@Size(max = 64)
@Pattern(regexp = "[A-Za-z0-9]+")
@ReportAsSingleViolation
public @interface ValidAccountId {

    String message() default "Account ID must contain 1–64 letters or numbers";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
