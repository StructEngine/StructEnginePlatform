package com.structengine.platform.game.service;

import com.structengine.platform.domain.ModelData;
import com.structengine.platform.game.model.StructureRequest;

/**
 * 建筑模型生成抽象。
 *
 * <p>当前由 {@link MockStructureGenerator} 提供确定性的占位结构；
 * 后续接入 Spring AI 时，只需新增一个实现并替换 Bean，无需改动 WebSocket/RPC 链路。</p>
 */
public interface StructureGenerator {

    ModelData generate(StructureRequest request);
}
