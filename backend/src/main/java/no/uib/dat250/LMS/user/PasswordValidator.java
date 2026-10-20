package no.uib.dat250.LMS.user;

import java.util.regex.Pattern;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {
    private static final Pattern SPECIAL = Pattern.compile("[\\p{P}\\p{S}]");
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && value.codePointCount(0, value.length()) >= 8 && SPECIAL.matcher(value).find();
    }
}
