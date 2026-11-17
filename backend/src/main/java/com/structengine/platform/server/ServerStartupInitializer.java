package com.structengine.platform.server;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 平台重启后，数据库里残留的 ONLINE 状态统一改为 OFFLINE，等待插件重连。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class ServerStartupInitializer implements ApplicationRunner {

    private final ServerService serverService;

    public ServerStartupInitializer(ServerService serverService) {
        this.serverService = serverService;
    }

    @Override
    public void run(ApplicationArguments args) {
        serverService.markAllOnlineOffline();
    }
}
