package com.structengine.platform.game.model;

/**
 * 构建任务结果：插件查询到的 Y 轴、AI 模型地址与任务标识。
 */
public record BuildResult(
        String taskId,
        String serverId,
        String playerName,
        int x,
        int y,
        int z,
        String modelUrl
) {
}
