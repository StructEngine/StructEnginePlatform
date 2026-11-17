package com.structengine.platform.task;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Platform 重启后终止所有非终态构建任务，避免前端无限轮询。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class TaskStartupInitializer implements ApplicationRunner {

    private final BuildTaskService taskService;

    public TaskStartupInitializer(BuildTaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public void run(ApplicationArguments args) {
        taskService.abortInterruptedTasks();
    }
}
