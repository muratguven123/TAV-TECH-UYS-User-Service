package com.tav.userservice.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RoleName — JSON deserialize")
class RoleNameTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @CsvSource({
            "ADMIN, ADMIN",
            "ROLE_ADMIN, ADMIN",
            "BI_SPECIALIST, BI_SPECIALIST",
            "ROLE_BI_SPECIALIST, BI_SPECIALIST",
            "OPERATION_OFFICER, OPERATION_OFFICER",
            "ROLE_OPERATION_OFFICER, OPERATION_OFFICER"
    })
    @DisplayName("fromValue: ROLE_ ön eki ve düz isim aynı enum'a çözülür")
    void fromValue_acceptsWithOrWithoutRolePrefix(String input, RoleName expected) {
        assertThat(RoleName.fromValue(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("JSON array: ROLE_ ön ekli roller deserialize edilir")
    void jsonArray_withRolePrefix_deserializes() throws Exception {
        RoleName[] roles = objectMapper.readValue(
                "[\"ROLE_ADMIN\", \"ROLE_BI_SPECIALIST\"]", RoleName[].class);

        assertThat(roles).containsExactly(RoleName.ADMIN, RoleName.BI_SPECIALIST);
    }

    @Test
    @DisplayName("fromValue: geçersiz rol IllegalArgumentException fırlatır")
    void fromValue_invalidRole_throws() {
        assertThatThrownBy(() -> RoleName.fromValue("ROLE_UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
