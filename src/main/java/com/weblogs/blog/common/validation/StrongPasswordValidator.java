package com.weblogs.blog.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    // Min 8 chars, at least: 1 uppercase, 1 lowercase, 1 digit, 1 special char
    private static final Pattern PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d]).{8,}$"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return PATTERN.matcher(value).matches();
    }
}
