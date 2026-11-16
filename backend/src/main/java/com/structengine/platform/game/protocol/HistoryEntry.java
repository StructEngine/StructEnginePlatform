package com.structengine.platform.game.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 插件内存中的一条构建历史，供 Web 回滚/恢复列表使用。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HistoryEntry(
        String historyId,
        String taskId,
        String modelName,
        long createdAt,
        int blockCount,
        boolean canRollback,
        boolean canRedo
) {
}
