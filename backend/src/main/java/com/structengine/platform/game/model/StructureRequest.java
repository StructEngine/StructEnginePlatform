package com.structengine.platform.game.model;

/**
 * 交给 AI/模型生成器的完整建造上下文。
 */
public record StructureRequest(String playerName, int x, int y, int z, String prompt) {
}
