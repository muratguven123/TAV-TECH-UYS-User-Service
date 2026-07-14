package com.tav.userservice.bootstrap;

import com.tav.userservice.entity.Role;
import com.tav.userservice.entity.RoleName;
import com.tav.userservice.repository.RoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RoleSeedRunner} birim testleri — idempotency ve savunmacı davranış.
 */
@ExtendWith(MockitoExtension.class)
class RoleSeedRunnerTest {

    @Mock RoleRepository roleRepository;
    @InjectMocks RoleSeedRunner runner;

    @Test
    @DisplayName("Eksik rol (ADMIN yok) seed edilir; var olanlara dokunulmaz")
    void run_insertsMissingRole() {
        // given — OPERATION_OFFICER ve BI_SPECIALIST mevcut, ADMIN eksik
        when(roleRepository.findByName(RoleName.OPERATION_OFFICER)).thenReturn(Optional.of(new Role()));
        when(roleRepository.findByName(RoleName.BI_SPECIALIST)).thenReturn(Optional.of(new Role()));
        when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.empty());

        // when
        runner.run(null);

        // then — yalnızca ADMIN kaydedilir
        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo(RoleName.ADMIN);
    }

    @Test
    @DisplayName("Tüm roller mevcutsa hiçbir kayıt yapılmaz (idempotent)")
    void run_allPresent_savesNothing() {
        when(roleRepository.findByName(any(RoleName.class))).thenReturn(Optional.of(new Role()));

        runner.run(null);

        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("save başarısız olsa bile başlangıç kesilmez (hata yutulur)")
    void run_saveFails_doesNotPropagate() {
        when(roleRepository.findByName(any(RoleName.class))).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class)))
                .thenThrow(new RuntimeException("CHECK constraint ihlali (eski şema)"));

        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
    }
}
