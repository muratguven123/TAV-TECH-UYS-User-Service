package com.tav.userservice.service;

import com.tav.userservice.entity.RoleName;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakUserSyncServiceTest {

    @Mock Keycloak keycloakAdmin;
    @Mock RealmResource realmResource;
    @Mock UsersResource usersResource;
    @Mock UserResource userResource;
    @Mock RolesResource rolesResource;
    @Mock RoleResource roleResource;
    @Mock RoleMappingResource roleMappingResource;
    @Mock RoleScopeResource roleScopeResource;

    KeycloakUserSyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new KeycloakUserSyncService(keycloakAdmin);
        ReflectionTestUtils.setField(syncService, "realm", "uys-realm");
        lenient().when(keycloakAdmin.realm("uys-realm")).thenReturn(realmResource);
        lenient().when(realmResource.users()).thenReturn(usersResource);
        lenient().when(realmResource.roles()).thenReturn(rolesResource);
    }

    // ----------------------------------------------------------- happy path

    @Test
    @DisplayName("createUser: başarılı yolda Keycloak'ta user oluşturur ve rolleri atar")
    void syncUser_happyPath_createsUserInKeycloakAndAssignsRole() {
        // given
        Response response = mockResponse(201);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);

        UserRepresentation existing = new UserRepresentation();
        existing.setId("kc-user-id-123");
        existing.setUsername("ahmet");
        when(usersResource.search("ahmet", true)).thenReturn(List.of(existing));

        when(usersResource.get("kc-user-id-123")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(new UserRepresentation());
        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);

        when(rolesResource.get(RoleName.OPERATION_OFFICER.name())).thenReturn(roleResource);
        RoleRepresentation kcRole = new RoleRepresentation();
        kcRole.setName(RoleName.OPERATION_OFFICER.name());
        when(roleResource.toRepresentation()).thenReturn(kcRole);

        // when
        syncService.createUser("ahmet", "ahmet@tav.aero", "Strong#Pass1",
                Set.of(RoleName.OPERATION_OFFICER));

        // then
        ArgumentCaptor<UserRepresentation> userCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource).create(userCaptor.capture());
        UserRepresentation sent = userCaptor.getValue();
        assertThat(sent.getUsername()).isEqualTo("ahmet");
        assertThat(sent.getEmail()).isEqualTo("ahmet@tav.aero");
        assertThat(sent.getFirstName()).isEqualTo("ahmet");
        assertThat(sent.getLastName()).isEqualTo("User");
        assertThat(sent.isEnabled()).isTrue();
        assertThat(sent.getCredentials()).isNullOrEmpty();

        ArgumentCaptor<CredentialRepresentation> credCaptor =
                ArgumentCaptor.forClass(CredentialRepresentation.class);
        verify(userResource).resetPassword(credCaptor.capture());
        CredentialRepresentation cred = credCaptor.getValue();
        assertThat(cred.getType()).isEqualTo(CredentialRepresentation.PASSWORD);
        assertThat(cred.getValue()).isEqualTo("Strong#Pass1");
        assertThat(cred.isTemporary()).isFalse();
        verify(userResource).update(any(UserRepresentation.class));

        verify(roleScopeResource).add(List.of(kcRole));
    }

    // -------------------------------------------------------- non-201 response

    @Test
    @DisplayName("createUser: Keycloak 409 dönerse IllegalStateException fırlatılır")
    void syncUser_when409Conflict_throwsIllegalStateException() {
        // given
        Response response = mockResponse(409);
        when(response.readEntity(String.class)).thenReturn("User exists with same username");
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);

        // when / then
        assertThatThrownBy(() -> syncService.createUser("ahmet", "x@y.z", "Strong#Pass1", Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 409");
        verify(usersResource, never()).search(anyString(), eq(true));
    }

    @Test
    @DisplayName("createUser: Keycloak 500 dönerse IllegalStateException fırlatılır")
    void syncUser_whenServerError_throwsIllegalStateException() {
        // given
        Response response = mockResponse(500);
        when(response.readEntity(String.class)).thenReturn("Server error");
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);

        // when / then
        assertThatThrownBy(() -> syncService.createUser("a", "b@c.d", "Strong#Pass1", Set.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    // --------------------------------------------------- user-not-found-after-create

    @Test
    @DisplayName("createUser: 201 sonrası kullanıcı bulunamazsa IllegalStateException fırlatılır")
    void syncUser_whenSearchEmpty_throwsIllegalStateException() {
        // given
        Response response = mockResponse(201);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(usersResource.search("ahmet", true)).thenReturn(List.of());

        // when / then
        assertThatThrownBy(() -> syncService.createUser("ahmet", "a@b.c", "Strong#Pass1", Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bulunamadı");
    }

    // ----------------------------------------------------- no roles -> skip role assign

    @Test
    @DisplayName("createUser: roller boşsa rol atama yapılmaz, şifre yine reset-password ile atanır")
    void syncUser_whenNoRoles_skipsRoleAssignment() {
        // given
        Response response = mockResponse(201);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        UserRepresentation existing = new UserRepresentation();
        existing.setId("kc-1");
        when(usersResource.search("ahmet", true)).thenReturn(List.of(existing));
        when(usersResource.get("kc-1")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(new UserRepresentation());

        // when
        syncService.createUser("ahmet", "a@b.c", "Strong#Pass1", Set.of());

        // then
        verify(userResource).resetPassword(any(CredentialRepresentation.class));
        verify(roleMappingResource, never()).realmLevel();
        verify(rolesResource, never()).get(anyString());
    }

    // ----------------------------------------------------------- credential check

    @Test
    @DisplayName("createUser: parola reset-password endpoint'i ile Keycloak'a iletilir")
    void syncUser_propagatesPasswordAsCredential() {
        // given
        Response response = mockResponse(201);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        UserRepresentation existing = new UserRepresentation();
        existing.setId("kc-1");
        when(usersResource.search("u", true)).thenReturn(List.of(existing));
        when(usersResource.get("kc-1")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(new UserRepresentation());

        // when
        syncService.createUser("u", "u@x.y", "MyPass#9", Set.of());

        // then
        ArgumentCaptor<CredentialRepresentation> cap = ArgumentCaptor.forClass(CredentialRepresentation.class);
        verify(userResource).resetPassword(cap.capture());
        assertThat(cap.getValue().getValue()).isEqualTo("MyPass#9");
    }

    // ------------------------------------------------------------------ helpers

    private Response mockResponse(int status) {
        Response r = org.mockito.Mockito.mock(Response.class);
        lenient().when(r.getStatus()).thenReturn(status);
        return r;
    }
}
