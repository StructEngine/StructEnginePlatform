package com.structengine.platform.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 构建任务状态读写。插件通过 BUILD_STATUS 上报进度。
 */
@Service
public class BuildTaskService {

    private static final Logger log = LoggerFactory.getLogger(BuildTaskService.class);

    private final BuildTaskRepository repository;

    public BuildTaskService(BuildTaskRepository repository) {
        this.repository = repository;
    }

    public void save(BuildTask task) {
        repository.insert(task);
    }

    public void status(String taskId, String state, int placed, int total, String message) {
        log.info("任务 {} 状态 {} {}/{} {}", taskId, state, placed, total,
                message == null ? "" : message);
        try {
            long id = Long.parseLong(taskId);
            repository.findById(id).ifPresent(task ->
                    repository.updateStatus(id, state, placed, total, message,
                            System.currentTimeMillis()));
        } catch (NumberFormatException e) {
            log.warn("忽略非法 taskId: {}", taskId);
        }
    }

    public Optional<BuildTask> find(long taskId) {
        return repository.findById(taskId);
    }

    public List<BuildTask> list(long serverId, String playerUuid) {
        return repository.listByServer(serverId, playerUuid, 100);
    }
}
