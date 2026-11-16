package com.structengine.platform.game.service;

import com.structengine.platform.service.PlatformStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 构建任务状态读写。插件通过 BUILD_STATUS 上报进度。
 */
@Service
public class BuildTaskService {

    private static final Logger log = LoggerFactory.getLogger(BuildTaskService.class);

    private final PlatformStore store;

    public BuildTaskService(PlatformStore store) {
        this.store = store;
    }

    public void save(PlatformStore.TaskRecord task) {
        store.saveTask(task);
    }

    public void status(String taskId, String state, int placed, int total, String message) {
        log.info("任务 {} 状态 {} {}/{} {}", taskId, state, placed, total,
                message == null ? "" : message);
        store.findTask(taskId).ifPresent(task ->
                store.updateTask(taskId, state, placed, total, message));
    }

    public PlatformStore.TaskRecord find(String taskId) {
        return store.findTask(taskId).orElse(null);
    }
}
