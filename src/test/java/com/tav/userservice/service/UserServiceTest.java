package com.tav.userservice.service;

import com.tav.userservice.dto.UserCreateRequest;
import com.tav.userservice.dto.UserDto;
import com.tav.userservice.entity.Role;
import com.tav.userservice.entity.RoleName;
import com.tav.userservice.entity.User;
import com.tav.userservice.event.UserCreatedEvent;
import com.tav.userservice.repository.RoleRepository;
import com.tav.userservice.repository.UserRepository;
import com.tav.userservice.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks UserService userService;

    private UserCreateRequest request;
    private User savedUser;

    @BeforeEach
    void setUp() {
        request = TestDataFactory.buildCreateRequest();
        savedUser = TestDataFactory.buildUser();
    }

    // ------------------------------------------------------------------ create

    @Test
    @DisplayName("createUser: başarılı yolda kaydet, parolayı encode et, event yayınla")
    void createUser_happyPath_savesUserAndPublishesEvent() {
        // given
        when(userRepository.existsByUsername(TestDataFactory.USERNAME)).thenReturn(false);
        when(userRepository.existsByEmail(TestDataFactory.EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(TestDataFactory.PASSWORD)).thenReturn(TestDataFactory.ENCODED_PASSWORD);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        Role role = TestDataFactory.buildRole(RoleName.OPERATION_OFFICER);
        when(roleRepository.findByName(RoleName.OPERATION_OFFICER)).thenReturn(Optional.of(role));
        when(userRepository.findByIdWithRoles(TestDataFactory.USER_ID))
                .thenReturn(Optional.of(TestDataFactory.buildUserWithRoles(RoleName.OPERATION_OFFICER)));

        // when
        UserDto result = userService.createUser(request);

        // then
        verify(userRepository, atLeastOnce()).save(any(User.class));
        verify(eventPublisher).publishEvent(any(UserCreatedEvent.class));
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(TestDataFactory.USERNAME);
    }

    @Test
    @DisplayName("createUser: username mevcutsa IllegalArgumentException fırlatılır")
    void createUser_whenUsernameExists_throwsIllegalArgumentException() {
        // given
        when(userRepository.existsByUsername(TestDataFactory.USERNAME)).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already taken");
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(UserCreatedEvent.class));
    }

    @Test
    @DisplayName("createUser: email mevcutsa IllegalArgumentException fırlatılır")
    void createUser_whenEmailExists_throwsIllegalArgumentException() {
        // given
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(TestDataFactory.EMAIL)).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already in use");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("createUser: parola kaydedilmeden önce encode edilir (raw kaydedilmez)")
    void createUser_encodesPasswordBeforeSave() {
        // given
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(TestDataFactory.PASSWORD)).thenReturn(TestDataFactory.ENCODED_PASSWORD);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(TestDataFactory.USER_ID);
            return u;
        });
        Role role = TestDataFactory.buildRole(RoleName.OPERATION_OFFICER);
        when(roleRepository.findByName(any())).thenReturn(Optional.of(role));
        when(userRepository.findByIdWithRoles(any()))
                .thenReturn(Optional.of(TestDataFactory.buildUserWithRoles(RoleName.OPERATION_OFFICER)));

        // when
        userService.createUser(request);

        // then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());
        User savedArg = userCaptor.getAllValues().get(0);
        assertThat(savedArg.getPassword()).isEqualTo(TestDataFactory.ENCODED_PASSWORD);
        assertThat(savedArg.getPassword()).isNotEqualTo(TestDataFactory.PASSWORD);
    }

    @Test
    @DisplayName("createUser: roles listesinde rol DB'de yoksa IllegalArgumentException fırlatılır")
    void createUser_whenRoleNotFound_throwsIllegalArgumentException() {
        // given
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn(TestDataFactory.ENCODED_PASSWORD);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(roleRepository.findByName(RoleName.OPERATION_OFFICER)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role not found");
    }

    @Test
    @DisplayName("createUser: yayınlanan UserCreatedEvent payload'ı request'i taşır")
    void createUser_publishesUserCreatedEventWithCorrectPayload() {
        // given
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn(TestDataFactory.ENCODED_PASSWORD);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(roleRepository.findByName(any())).thenReturn(Optional.of(TestDataFactory.buildRole(RoleName.OPERATION_OFFICER)));
        when(userRepository.findByIdWithRoles(any()))
                .thenReturn(Optional.of(TestDataFactory.buildUserWithRoles(RoleName.OPERATION_OFFICER)));

        // when
        userService.createUser(request);

        // then
        ArgumentCaptor<UserCreatedEvent> eventCaptor = ArgumentCaptor.forClass(UserCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        UserCreatedEvent evt = eventCaptor.getValue();
        assertThat(evt.username()).isEqualTo(TestDataFactory.USERNAME);
        assertThat(evt.email()).isEqualTo(TestDataFactory.EMAIL);
        assertThat(evt.password()).isEqualTo(TestDataFactory.PASSWORD);
        assertThat(evt.roles()).containsExactly(RoleName.OPERATION_OFFICER);
    }

    @Test
    @DisplayName("createUser: roller boş olduğunda rol araması yapılmaz, kullanıcı yine kaydedilir")
    void createUser_whenRolesEmpty_skipsRoleLookup() {
        // given
        UserCreateRequest noRolesReq = TestDataFactory.buildCreateRequest(Set.of());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn(TestDataFactory.ENCODED_PASSWORD);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userRepository.findByIdWithRoles(any()))
                .thenReturn(Optional.of(TestDataFactory.buildUser()));

        // when
        userService.createUser(noRolesReq);

        // then
        verify(roleRepository, never()).findByName(any());
        verify(eventPublisher).publishEvent(any(UserCreatedEvent.class));
    }

    // ----------------------------------------------------------- getUserById

    @Test
    @DisplayName("getUserById: kayıt varsa UserDto döner")
    void getUserById_whenExists_returnsDto() {
        // given
        User u = TestDataFactory.buildUserWithRoles(RoleName.OPERATION_OFFICER);
        when(userRepository.findByIdWithRoles(TestDataFactory.USER_ID)).thenReturn(Optional.of(u));

        // when
        UserDto result = userService.getUserById(TestDataFactory.USER_ID);

        // then
        assertThat(result.getId()).isEqualTo(TestDataFactory.USER_ID);
        assertThat(result.getUsername()).isEqualTo(TestDataFactory.USERNAME);
        assertThat(result.getRoles()).contains(RoleName.OPERATION_OFFICER);
    }

    @Test
    @DisplayName("getUserById: kayıt yoksa IllegalArgumentException fırlatılır")
    void getUserById_whenNotFound_throwsIllegalArgumentException() {
        // given
        when(userRepository.findByIdWithRoles(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    // -------------------------------------------------------- getAllActiveUsers

    @Test
    @DisplayName("getAllActiveUsers: aktif kullanıcılar liste olarak döner")
    void getAllActiveUsers_returnsList() {
        // given
        User u1 = TestDataFactory.buildUserWithRoles(RoleName.OPERATION_OFFICER);
        User u2 = TestDataFactory.buildUserWithRoles(RoleName.BI_SPECIALIST);
        u2.setId(2L);
        when(userRepository.findAllActive()).thenReturn(List.of(u1, u2));

        // when
        List<UserDto> result = userService.getAllActiveUsers();

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getAllActiveUsers: repository boşsa boş liste döner")
    void getAllActiveUsers_whenEmpty_returnsEmptyList() {
        // given
        when(userRepository.findAllActive()).thenReturn(List.of());

        // when
        List<UserDto> result = userService.getAllActiveUsers();

        // then
        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------- getUserCount

    @Test
    @DisplayName("getUserCount: repository.count değerini döner")
    void getUserCount_returnsRepositoryCount() {
        // given
        when(userRepository.count()).thenReturn(42L);

        // when
        long result = userService.getUserCount();

        // then
        assertThat(result).isEqualTo(42L);
    }
}
