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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 管理员/用户注册、登录、改密与管理员用户管理。
 */
@Service
public class AuthService {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z]{4,16}$");
    private static final String RESERVED_ADMIN = "admin";
    private static final int MIN_PASSWORD = 6;
    private static final int MAX_PASSWORD = 16;

    private final AdminAccountRepository adminRepository;
    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CaptchaService captchaService;
    private final SnowflakeIdGenerator idGenerator;

    public AuthService(AdminAccountRepository adminRepository,
                       UserAccountRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       CaptchaService captchaService,
                       SnowflakeIdGenerator idGenerator) {
        this.adminRepository = adminRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.captchaService = captchaService;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        validateUsername(username);
        validatePassword(request.password());
        requireCaptcha(request.captchaId(), request.captchaCode());
        if (isReserved(username)) {
            throw new BusinessException(ErrorCode.CONFLICT, "该用户名不可用");
        }
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }

        long now = System.currentTimeMillis();
        AppUser user = new AppUser(idGenerator.nextId(), username,
                passwordEncoder.encode(request.password()), "ACTIVE", 0, now, now, null);
        try {
            userRepository.insert(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }
        return tokenFor(user);
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        requireCaptcha(request.captchaId(), request.captchaCode());
        String username = request.username() == null ? "" : request.username().trim();

        AdminAccount admin = adminRepository.findByUsername(username).orElse(null);
        if (admin != null) {
            if (!passwordEncoder.matches(request.password(), admin.passwordHash())) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
            }
            adminRepository.updateLastLogin(admin.id(), System.currentTimeMillis());
            return tokenFor(admin, admin.mustChangePassword());
        }

        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));
        if (!user.active()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        userRepository.updateLastLogin(user.id(), System.currentTimeMillis());
        return tokenFor(user);
    }

    public AccountView me(AuthPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (principal.admin()) {
            AdminAccount admin = adminRepository.findById(principal.id())
                    .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
            return new AccountView(Long.toString(admin.id()), admin.username(), "ADMIN",
                    "ACTIVE", admin.mustChangePassword());
        }
        AppUser user = userRepository.findById(principal.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return new AccountView(Long.toString(user.id()), user.username(), "USER",
                user.status(), false);
    }

    @Transactional
    public AuthTokenResponse changePassword(AuthPrincipal principal, ChangePasswordRequest request) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        validatePassword(request.newPassword());
        if (principal.admin()) {
            AdminAccount admin = adminRepository.findById(principal.id())
                    .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
            if (!passwordEncoder.matches(request.oldPassword(), admin.passwordHash())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "旧密码错误");
            }
            int nextVersion = admin.tokenVersion() + 1;
            adminRepository.updatePassword(admin.id(), passwordEncoder.encode(request.newPassword()),
                    false, nextVersion, System.currentTimeMillis());
            return tokenFor(admin.id(), admin.username(), "ADMIN", nextVersion, false);
        }

        AppUser user = userRepository.findById(principal.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (!passwordEncoder.matches(request.oldPassword(), user.passwordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "旧密码错误");
        }
        int nextVersion = user.tokenVersion() + 1;
        userRepository.updatePassword(user.id(), passwordEncoder.encode(request.newPassword()),
                nextVersion, System.currentTimeMillis());
        return tokenFor(user.id(), user.username(), "USER", nextVersion, false);
    }

    public PageResponse<AdminUserView> listUsers(int page, int size, String keyword) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        long total = userRepository.count(keyword);
        List<AdminUserView> items = userRepository.list((safePage - 1) * safeSize, safeSize, keyword)
                .stream()
                .map(user -> new AdminUserView(Long.toString(user.id()), user.username(),
                        user.status(), user.createdAt(), user.lastLoginAt()))
                .toList();
        return new PageResponse<>(items, safePage, safeSize, total);
    }

    @Transactional
    public AdminUserView createUser(AdminCreateUserRequest request) {
        String username = normalizeUsername(request.username());
        validateUsername(username);
        validatePassword(request.password());
        if (isReserved(username)) {
            throw new BusinessException(ErrorCode.CONFLICT, "该用户名不可用");
        }
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }
        long now = System.currentTimeMillis();
        AppUser user = new AppUser(idGenerator.nextId(), username,
                passwordEncoder.encode(request.password()), "ACTIVE", 0, now, now, null);
        try {
            userRepository.insert(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }
        return new AdminUserView(Long.toString(user.id()), user.username(), user.status(),
                user.createdAt(), user.lastLoginAt());
    }

    @Transactional
    public AdminUserView updateUser(long userId, AdminUpdateUserRequest request) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        String username = request.username() == null ? null : normalizeUsername(request.username());
        if (username != null && !username.equals(user.username())) {
            validateUsername(username);
            if (isReserved(username)) {
                throw new BusinessException(ErrorCode.CONFLICT, "该用户名不可用");
            }
            if (userRepository.existsByUsername(username)) {
                throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
            }
            userRepository.updateUsername(user.id(), username, System.currentTimeMillis());
            user = userRepository.findById(user.id()).orElseThrow();
        }

        String status = request.status();
        if (status != null && !status.equals(user.status())) {
            if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "状态只能是 ACTIVE 或 DISABLED");
            }
            int nextVersion = "DISABLED".equals(status)
                    ? user.tokenVersion() + 1 : user.tokenVersion();
            userRepository.updateStatus(user.id(), status, nextVersion, System.currentTimeMillis());
            user = userRepository.findById(user.id()).orElseThrow();
        }

        return new AdminUserView(Long.toString(user.id()), user.username(), user.status(),
                user.createdAt(), user.lastLoginAt());
    }

    @Transactional
    public void resetPassword(long userId, AdminResetPasswordRequest request) {
        validatePassword(request.password());
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        userRepository.updatePassword(user.id(), passwordEncoder.encode(request.password()),
                user.tokenVersion() + 1, System.currentTimeMillis());
    }

    @Transactional
    public void deleteUser(long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        userRepository.delete(userId);
    }

    @Transactional
    public AuthTokenResponse changeAdminPassword(AuthPrincipal principal, ChangePasswordRequest request) {
        if (principal == null || !principal.admin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        validatePassword(request.newPassword());
        AdminAccount admin = adminRepository.findById(principal.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (!passwordEncoder.matches(request.oldPassword(), admin.passwordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "旧密码错误");
        }
        int nextVersion = admin.tokenVersion() + 1;
        adminRepository.updatePassword(admin.id(), passwordEncoder.encode(request.newPassword()),
                false, nextVersion, System.currentTimeMillis());
        return tokenFor(admin.id(), admin.username(), "ADMIN", nextVersion, false);
    }

    private AuthTokenResponse tokenFor(AppUser user) {
        return tokenFor(user.id(), user.username(), "USER", user.tokenVersion(), false);
    }

    private AuthTokenResponse tokenFor(AdminAccount admin, boolean mustChangePassword) {
        return tokenFor(admin.id(), admin.username(), "ADMIN", admin.tokenVersion(),
                admin.mustChangePassword());
    }

    private AuthTokenResponse tokenFor(long id, String username, String role,
                                       int tokenVersion, boolean mustChangePassword) {
        String token = jwtService.issue(
                "ADMIN".equals(role) ? AccountType.ADMIN : AccountType.USER,
                id, username, role, tokenVersion);
        AccountView view = new AccountView(Long.toString(id), username, role,
                "ACTIVE", mustChangePassword);
        return new AuthTokenResponse(token, "Bearer", jwtService.ttlSeconds(), view);
    }

    private void requireCaptcha(String captchaId, String captchaCode) {
        if (!captchaService.verify(captchaId, captchaCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码错误或已过期");
        }
    }

    private static String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private static void validateUsername(String username) {
        if (!USERNAME.matcher(username).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名必须为 4~16 位英文字母");
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD
                || password.length() > MAX_PASSWORD) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码长度必须为 6~16 位");
        }
    }

    private static boolean isReserved(String username) {
        return RESERVED_ADMIN.equalsIgnoreCase(username);
    }
}
