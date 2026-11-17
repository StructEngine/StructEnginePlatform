package com.structengine.platform.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.structengine.platform.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final AppProperties props;

    public BuildTaskService(BuildTaskRepository repository, AppProperties props) {
        this.repository = repository;
        this.props = props;
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

    /** 插件长时间没有 BUILD_STATUS 时，避免任务永久卡在非终态。 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void failStaleTasks() {
        long now = System.currentTimeMillis();
        long cutoff = now - props.getTaskTimeoutSeconds() * 1000L;
        try {
            int updated = repository.markStaleFailed(cutoff, now,
                    "任务超时：超过 " + props.getTaskTimeoutSeconds() + " 秒没有进度");
            if (updated > 0) {
                log.warn("已将 {} 个超时任务标记为 FAILED", updated);
            }
        } catch (RuntimeException e) {
            log.warn("扫描超时任务失败（数据库忙或已关闭）：{}", e.getMessage());
        }
    }

    /** Platform 重启后，插件侧构建状态不可恢复，统一终止非终态任务。 */
    public void abortInterruptedTasks() {
        try {
            int updated = repository.markInterrupted(System.currentTimeMillis(),
                    "Platform 重启，任务状态中断");
            if (updated > 0) {
                log.warn("已将 {} 个中断任务标记为 ABORTED", updated);
            }
        } catch (RuntimeException e) {
            log.warn("终止中断任务失败（数据库忙或已关闭）：{}", e.getMessage());
        }
    }
}
