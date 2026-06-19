package com.tav.userservice.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.ArrayList;
import java.util.List;

public class PasswordConstraintValidator implements ConstraintValidator<ValidPassword, String> {

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) return false;

        List<String> violations = new ArrayList<>();
        if (password.length() < 8 || password.length() > 64) violations.add("8-64 karakter olmalı");
        if (!password.matches(".*[A-Z].*")) violations.add("en az 1 büyük harf");
        if (!password.matches(".*[a-z].*")) violations.add("en az 1 küçük harf");
        if (!password.matches(".*[0-9].*")) violations.add("en az 1 rakam");
        if (!password.matches(".*[^a-zA-Z0-9].*")) violations.add("en az 1 özel karakter");
        if (password.contains(" ")) violations.add("boşluk içermemeli");

        if (!violations.isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Parola kuralları ihlal edildi: " + String.join(", ", violations)
            ).addConstraintViolation();
            return false;
        }
        return true;
    }
}
