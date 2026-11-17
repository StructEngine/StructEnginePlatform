package com.structengine.platform.auth;

import com.structengine.platform.account.AdminAccount;
import com.structengine.platform.account.AdminAccountRepository;
import com.structengine.platform.config.AppProperties;
import com.structengine.platform.id.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次安装时创建 admin 账号。
 *
 * <p>admin 已存在则不做任何事；不存在时必须提供
 * {@code STRUCTENGINE_ADMIN_INITIAL_PASSWORD}，避免默认密码。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);
    private static final String ADMIN_USERNAME = "admin";

    private final AdminAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SnowflakeIdGenerator idGenerator;
    private final AppProperties props;

    public AdminInitializer(AdminAccountRepository repository, PasswordEncoder passwordEncoder,
                            SnowflakeIdGenerator idGenerator, AppProperties props) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.idGenerator = idGenerator;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.existsAny()) {
            return;
        }
        String password = props.getAdminInitialPassword();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "首次安装必须设置 STRUCTENGINE_ADMIN_INITIAL_PASSWORD 以创建 admin 账号");
        }
        if (password.length() < 6 || password.length() > 16) {
            throw new IllegalStateException("admin 初始密码长度必须为 6~16 位");
        }
        long now = System.currentTimeMillis();
        repository.insert(new AdminAccount(
                idGenerator.nextId(), ADMIN_USERNAME, passwordEncoder.encode(password),
                0, true, now, now, null));
        log.warn("已创建初始 admin 账号，请首次登录后立即修改密码");
    }
}
