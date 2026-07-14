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

        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false, hasSpace = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (c == ' ') hasSpace = true;
            else hasSpecial = true;
        }

        if (!hasUpper) violations.add("en az 1 büyük harf");
        if (!hasLower) violations.add("en az 1 küçük harf");
        if (!hasDigit) violations.add("en az 1 rakam");
        if (!hasSpecial) violations.add("en az 1 özel karakter");
        if (hasSpace) violations.add("boşluk içermemeli");

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
