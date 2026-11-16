package com.structengine.platform.game.service;

import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.config.AppProperties;
import com.structengine.platform.domain.ModelData;
import com.structengine.platform.domain.VoxelBlock;
import com.structengine.platform.game.protocol.GameMessage;
import com.structengine.platform.game.ws.GameServerRegistry;
import com.structengine.platform.llm.JsonSanitizer;
import com.structengine.platform.service.FileStorageService;
import com.structengine.platform.service.PlatformStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 生成模型 -> 计算中心/原点 -> 旋转 -> 下发 BUILD。
 */
@Service
public class DeployService {

    private final PlatformStore store;
    private final GameServerRegistry registry;
    private final HeightRpcService heightRpcService;
    private final FileStorageService fileStorage;
    private final JsonSanitizer sanitizer;
    private final BuildTaskService taskService;
    private final AppProperties props;

    public DeployService(PlatformStore store, GameServerRegistry registry,
                         HeightRpcService heightRpcService, FileStorageService fileStorage,
                         JsonSanitizer sanitizer, BuildTaskService taskService, AppProperties props) {
        this.store = store;
        this.registry = registry;
        this.heightRpcService = heightRpcService;
        this.fileStorage = fileStorage;
        this.sanitizer = sanitizer;
        this.taskService = taskService;
        this.props = props;
    }

    public PlatformStore.TaskRecord deploy(String serverId, String playerUuid, String playerName,
                                           String modelId, int centerX, int centerZ, int rotation,
                                           String baseUrl) {
        PlatformStore.ModelRecord model = store.findModel(modelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "模型不存在"));
        if (!registry.isConnected(serverId)) {
            throw new BusinessException(ErrorCode.GAME_SERVER_UNAVAILABLE, "游戏服未连接");
        }

        String taskId = UUID.randomUUID().toString();
        int groundY = heightRpcService.queryY(serverId, taskId, playerName, centerX, centerZ);

        List<VoxelBlock> blocks = loadModel(modelId);
        blocks = rotate(blocks, rotation);
        int maxX = blocks.stream().mapToInt(VoxelBlock::x).max().orElse(0);
        int maxY = blocks.stream().mapToInt(VoxelBlock::y).max().orElse(0);
        int maxZ = blocks.stream().mapToInt(VoxelBlock::z).max().orElse(0);
        int width = maxX + 1;
        int depth = maxZ + 1;
        int originX = centerX - Math.floorDiv(width - 1, 2);
        int originZ = centerZ - Math.floorDiv(depth - 1, 2);
        int originY = groundY + 1;

        fileStorage.saveModel(taskId, new ModelData(blocks).serialize());
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String modelUrl = base + "/models/" + taskId + ".json";

        long now = System.currentTimeMillis();
        PlatformStore.TaskRecord task = new PlatformStore.TaskRecord(
                taskId, serverId, playerUuid, playerName, modelId,
                centerX, groundY, centerZ, originX, originY, originZ, rotation,
                "QUEUED", 0, blocks.size(), null, now, now);
        taskService.save(task);

        GameMessage build = GameMessage.build(taskId, playerUuid, playerName,
                originX, originY, originZ, modelUrl, model.prompt(), rotation);
        if (!registry.send(serverId, build)) {
            taskService.status(taskId, "FAILED", 0, blocks.size(), "BUILD 发送失败");
            throw new BusinessException(ErrorCode.GAME_SERVER_UNAVAILABLE, "BUILD 发送失败");
        }
        return task;
    }

    private List<VoxelBlock> loadModel(String modelId) {
        try {
            String json = Files.readString(props.dataPath().resolve("models").resolve(modelId + ".json"));
            return sanitizer.sanitize(json);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取模型失败");
        }
    }

    static List<VoxelBlock> rotate(List<VoxelBlock> blocks, int rotation) {
        int normalized = ((rotation % 360) + 360) % 360;
        if (normalized == 0) {
            return blocks;
        }
        int maxX = blocks.stream().mapToInt(VoxelBlock::x).max().orElse(0);
        int maxZ = blocks.stream().mapToInt(VoxelBlock::z).max().orElse(0);
        List<VoxelBlock> result = new ArrayList<>(blocks.size());
        for (VoxelBlock block : blocks) {
            int x = block.x();
            int z = block.z();
            int nx;
            int nz;
            switch (normalized) {
                case 90 -> {
                    nx = z;
                    nz = maxX - x;
                }
                case 180 -> {
                    nx = maxX - x;
                    nz = maxZ - z;
                }
                case 270 -> {
                    nx = maxZ - z;
                    nz = x;
                }
                default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "旋转角度必须是 0/90/180/270");
            }
            result.add(new VoxelBlock(block.id(), nx, block.y(), nz));
        }
        int minX = result.stream().mapToInt(VoxelBlock::x).min().orElse(0);
        int minZ = result.stream().mapToInt(VoxelBlock::z).min().orElse(0);
        List<VoxelBlock> normalizedList = new ArrayList<>(result.size());
        for (VoxelBlock block : result) {
            normalizedList.add(new VoxelBlock(block.id(), block.x() - minX, block.y(), block.z() - minZ));
        }
        normalizedList.sort(Comparator.comparingInt(VoxelBlock::y)
                .thenComparingInt(VoxelBlock::x).thenComparingInt(VoxelBlock::z));
        return normalizedList;
    }
}
