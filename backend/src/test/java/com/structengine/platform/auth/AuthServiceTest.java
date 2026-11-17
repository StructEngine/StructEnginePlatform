package com.structengine.platform.auth;

import com.structengine.platform.account.AccountType;
import com.structengine.platform.account.AdminAccount;
import com.structengine.platform.account.AdminAccountRepository;
import com.structengine.platform.account.AppUser;
import com.structengine.platform.account.UserAccountRepository;
import com.structengine.platform.captcha.CaptchaService;
import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.id.SnowflakeIdGenerator;
import com.structengine.platform.security.AuthPrincipal;
import com.structengine.platform.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AdminAccountRepository adminRepository;

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private CaptchaService captchaService;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(adminRepository, userRepository, passwordEncoder,
                jwtService, captchaService, idGenerator);
        lenient().when(jwtService.ttlSeconds()).thenReturn(3600L);
    }

    @Test
    void registersUserAndIssuesToken() {
        when(captchaService.verify("cid", "code")).thenReturn(true);
        when(idGenerator.nextId()).thenReturn(100L);
        when(passwordEncoder.encode("123456")).thenReturn("hash");
        when(jwtService.issue(AccountType.USER, 100L, "Steve", "USER", 0)).thenReturn("token");

        AuthTokenResponse response = service.register(
                new RegisterRequest("Steve", "123456", "cid", "code"));

        assertEquals("token", response.token());
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600L, response.expiresIn());
        assertEquals("100", response.user().id());
        assertEquals("Steve", response.user().username());
        assertEquals("USER", response.user().role());

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).insert(captor.capture());
        assertEquals("ACTIVE", captor.getValue().status());
        assertEquals("hash", captor.getValue().passwordHash());
    }

    @Test
    void rejectsReservedAdminUsername() {
        when(captchaService.verify(anyString(), anyString())).thenReturn(true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.register(new RegisterRequest("Admin", "123456", "cid", "code")));
        assertEquals(ErrorCode.CONFLICT, error.errorCode());
        verify(userRepository, never()).insert(any());
    }

    @Test
    void rejectsInvalidCaptcha() {
        when(captchaService.verify("cid", "bad")).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.register(new RegisterRequest("Steve", "123456", "cid", "bad")));
        assertEquals(ErrorCode.BAD_REQUEST, error.errorCode());
    }

    @Test
    void rejectsDuplicateUsername() {
        when(captchaService.verify(anyString(), anyString())).thenReturn(true);
        when(userRepository.existsByUsername("Steve")).thenReturn(true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.register(new RegisterRequest("Steve", "123456", "cid", "code")));
        assertEquals(ErrorCode.CONFLICT, error.errorCode());
    }

    @Test
    void logsInAdminWithMustChangeFlag() {
        AdminAccount admin = new AdminAccount(10L, "admin", "admin-hash", 0, true,
                1L, 1L, null);
        when(captchaService.verify(anyString(), anyString())).thenReturn(true);
        when(adminRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("admin-pass", "admin-hash")).thenReturn(true);
        when(jwtService.issue(AccountType.ADMIN, 10L, "admin", "ADMIN", 0))
                .thenReturn("admin-token");

        AuthTokenResponse response = service.login(
                new LoginRequest("admin", "admin-pass", "cid", "code"));

        assertEquals("admin-token", response.token());
        assertEquals("ADMIN", response.user().role());
        assertTrue(response.user().mustChangePassword());
        verify(adminRepository).updateLastLogin(eq(10L), anyLong());
    }

    @Test
    void rejectsWrongAdminPasswordWithoutFallingThrough() {
        AdminAccount admin = new AdminAccount(10L, "admin", "admin-hash", 0, false,
                1L, 1L, null);
        when(captchaService.verify(anyString(), anyString())).thenReturn(true);
        when(adminRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong", "admin-hash")).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("admin", "wrong", "cid", "code")));
        assertEquals(ErrorCode.UNAUTHORIZED, error.errorCode());
        verify(userRepository, never()).findByUsername(anyString());
    }

    @Test
    void rejectsDisabledUser() {
        AppUser user = new AppUser(5L, "Steve", "hash", "DISABLED", 2, 1L, 1L, null);
        when(captchaService.verify(anyString(), anyString())).thenReturn(true);
        when(adminRepository.findByUsername("Steve")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("Steve")).thenReturn(Optional.of(user));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("Steve", "123456", "cid", "code")));
        assertEquals(ErrorCode.FORBIDDEN, error.errorCode());
    }

    @Test
    void changesUserPasswordAndInvalidatesOldToken() {
        AppUser user = new AppUser(5L, "Steve", "old-hash", "ACTIVE", 2, 1L, 1L, null);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-pass", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-pass")).thenReturn("new-hash");
        when(jwtService.issue(AccountType.USER, 5L, "Steve", "USER", 3)).thenReturn("new-token");

        AuthTokenResponse response = service.changePassword(
                new AuthPrincipal(AccountType.USER, 5L, "Steve", "USER", 2),
                new ChangePasswordRequest("old-pass", "new-pass"));

        assertEquals("new-token", response.token());
        assertFalse(response.user().mustChangePassword());
        verify(userRepository).updatePassword(eq(5L), eq("new-hash"), eq(3), anyLong());
    }

    @Test
    void disablesUserAndBumpsTokenVersion() {
        AppUser user = new AppUser(5L, "Steve", "hash", "ACTIVE", 2, 1L, 1L, null);
        AppUser disabled = new AppUser(5L, "Steve", "hash", "DISABLED", 3, 1L, 1L, null);
        when(userRepository.findById(5L))
                .thenReturn(Optional.of(user))
                .thenReturn(Optional.of(disabled));

        AdminUserView view = service.updateUser(5L, new AdminUpdateUserRequest(null, "DISABLED"));

        assertEquals("DISABLED", view.status());
        verify(userRepository).updateStatus(eq(5L), eq("DISABLED"), eq(3), anyLong());
    }

    @Test
    void listsUsersWithPagination() {
        AppUser user = new AppUser(5L, "Steve", "hash", "ACTIVE", 0, 1L, 1L, null);
        when(userRepository.count("ste")).thenReturn(1L);
        when(userRepository.list(0, 20, "ste")).thenReturn(List.of(user));

        PageResponse<AdminUserView> page = service.listUsers(1, 20, "ste");

        assertEquals(1, page.items().size());
        assertEquals(1, page.page());
        assertEquals(20, page.size());
        assertEquals(1L, page.total());
        assertEquals("Steve", page.items().get(0).username());
    }

    @Test
    void rejectsDeletingMissingUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.deleteUser(99L));
        assertEquals(ErrorCode.NOT_FOUND, error.errorCode());
    }
}
