package com.structengine.platform.account;

import com.structengine.platform.security.AuthPrincipal;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 按账号 ID 加载当前有效主体，供 JWT 过滤器校验 token_version/状态。
 */
@Service
public class AccountService {

    private final AdminAccountRepository adminRepository;
    private final UserAccountRepository userRepository;

    public AccountService(AdminAccountRepository adminRepository,
                          UserAccountRepository userRepository) {
        this.adminRepository = adminRepository;
        this.userRepository = userRepository;
    }

    public Optional<AuthPrincipal> load(AccountType type, long id) {
        if (type == AccountType.ADMIN) {
            return adminRepository.findById(id)
                    .map(account -> new AuthPrincipal(AccountType.ADMIN, account.id(),
                            account.username(), "ADMIN", account.tokenVersion()));
        }
        return userRepository.findById(id)
                .filter(AppUser::active)
                .map(account -> new AuthPrincipal(AccountType.USER, account.id(),
                        account.username(), "USER", account.tokenVersion()));
    }
}
