package com.smartrecruit.identity.web;

import com.smartrecruit.identity.domain.CommonPasswords;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NotCommonPasswordValidator implements ConstraintValidator<NotCommonPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null est laissé à @NotNull/@NotBlank, pour un seul message par champ
        return value == null || !CommonPasswords.contains(value);
    }
}
