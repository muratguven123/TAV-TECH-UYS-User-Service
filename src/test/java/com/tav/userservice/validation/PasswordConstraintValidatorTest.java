package com.tav.userservice.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PasswordConstraintValidatorTest {

    private PasswordConstraintValidator validator;
    private ConstraintValidatorContext ctx;
    private ConstraintValidatorContext.ConstraintViolationBuilder builder;

    @BeforeEach
    void setUp() {
        validator = new PasswordConstraintValidator();
        ctx = mock(ConstraintValidatorContext.class);
        builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        lenient().when(ctx.buildConstraintViolationWithTemplate(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(builder);
    }

    @ParameterizedTest(name = "geçerli parola: {0}")
    @DisplayName("Geçerli parolalar kabul edilir")
    @ValueSource(strings = {
            "Strong#Pass1",
            "Abcd1234!",
            "A!a1aaaa",
            "MyP@ssword9",
            "Z9z!z9z!"
    })
    void isValid_validPasswords_returnsTrue(String password) {
        // when / then
        assertThat(validator.isValid(password, ctx)).isTrue();
    }

    @ParameterizedTest(name = "geçersiz parola: '{0}'")
    @DisplayName("Geçersiz parolalar reddedilir")
    @ValueSource(strings = {
            "short1!",        // < 8 karakter
            "nouppercase1!",  // büyük harf yok
            "NOLOWERCASE1!",  // küçük harf yok
            "NoDigitHere!",   // rakam yok
            "NoSpecial123",   // özel karakter yok
            "Has Space1!",    // boşluk içeriyor
            ""                // boş
    })
    void isValid_invalidPasswords_returnsFalse(String password) {
        // when / then
        assertThat(validator.isValid(password, ctx)).isFalse();
    }

    @Test
    @DisplayName("Null parola reddedilir")
    void isValid_null_returnsFalse() {
        // when / then
        assertThat(validator.isValid(null, ctx)).isFalse();
    }

    @Test
    @DisplayName("Geçersiz parolada custom violation mesajı set edilir")
    void isValid_invalid_buildsCustomViolationMessage() {
        // when
        validator.isValid("short", ctx);

        // then
        org.mockito.Mockito.verify(ctx).disableDefaultConstraintViolation();
        org.mockito.Mockito.verify(ctx).buildConstraintViolationWithTemplate(
                org.mockito.ArgumentMatchers.contains("Parola kuralları ihlal edildi"));
    }

    @Test
    @DisplayName("64 karakter sınır parola kabul edilir (sınır testi)")
    void isValid_exactly64Chars_returnsTrue() {
        // given - 64 karakter, kurallara uygun
        String pwd = "Abcdefg1!" + "x".repeat(55); // büyük+küçük+rakam+özel, toplam 64
        assertThat(pwd.length()).isEqualTo(64);

        // when / then
        assertThat(validator.isValid(pwd, ctx)).isTrue();
    }

    @Test
    @DisplayName("65 karakter parola reddedilir")
    void isValid_65Chars_returnsFalse() {
        // given
        String pwd = "Abcdefg1!" + "x".repeat(56); // 65 karakter
        assertThat(pwd.length()).isEqualTo(65);

        // when / then
        assertThat(validator.isValid(pwd, ctx)).isFalse();
    }
}
