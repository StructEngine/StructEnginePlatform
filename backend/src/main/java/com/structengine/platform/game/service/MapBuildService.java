package com.structengine.platform.game.service;

import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.domain.ModelData;
import com.structengine.platform.game.model.BuildRequest;
import com.structengine.platform.game.model.BuildResult;
import com.structengine.platform.game.model.StructureRequest;
import com.structengine.platform.game.protocol.GameMessage;
import com.structengine.platform.game.ws.GameServerRegistry;
import com.structengine.platform.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 地图选点构建主流程：
 * <ol>
 *   <li>选择已连接的游戏服；</li>
 *   <li>生成 taskId，发送 QUERY_Y，并在虚拟线程上阻塞等待 RESPONSE_Y；</li>
 *   <li>拿到完整 (X,Y,Z) 后调用模型生成器；</li>
 *   <li>模型 JSON 落盘到绝对路径 data/models/{taskId}.json；</li>
 *   <li>通过 WebSocket 下发 BUILD（含 modelUrl），插件按绝对坐标构建。</li>
 * </ol>
 */
@Service
public class MapBuildService {

    private static final Logger log = LoggerFactory.getLogger(MapBuildService.class);
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern SERVER_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final int WORLD_LIMIT = 30_000_000;
    private static final int MAX_PROMPT_LENGTH = 1000;

    private final GameServerRegistry registry;
    private final HeightRpcService heightRpcService;
    private final StructureGenerator structureGenerator;
    private final FileStorageService fileStorage;

    public MapBuildService(GameServerRegistry registry,
                           HeightRpcService heightRpcService,
                           StructureGenerator structureGenerator,
                           FileStorageService fileStorage) {
        this.registry = registry;
        this.heightRpcService = heightRpcService;
        this.structureGenerator = structureGenerator;
        this.fileStorage = fileStorage;
    }

    public BuildResult build(BuildRequest request, String baseUrl) {
        String playerName = validateAndTrimPlayerName(request.playerName());
        String prompt = validatePrompt(request.prompt());
        int x = validateCoordinate(request.x(), "X");
        int z = validateCoordinate(request.z(), "Z");
        String serverId = resolveServerId(request.serverId());

        String taskId = UUID.randomUUID().toString();
        int y = heightRpcService.queryY(serverId, taskId, playerName, x, z);
        log.info("高度查询完成 taskId={} server={} x={} y={} z={}", taskId, serverId, x, y, z);

        ModelData model = structureGenerator.generate(new StructureRequest(playerName, x, y, z, prompt));
        try {
            model.validate();
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.UNPROCESSABLE, "模型数据非法: " + e.getMessage());
        }

        // 绝对路径落盘：{项目根目录}/data/models/{taskId}.json
        fileStorage.saveModel(taskId, model.serialize());

        String modelUrl = joinBaseUrl(baseUrl, "/models/" + taskId + ".json");
        GameMessage build = GameMessage.build(taskId, null, playerName, x, y, z, modelUrl, taskId, 0);
        if (!registry.send(serverId, build)) {
            throw new BusinessException(ErrorCode.GAME_SERVER_UNAVAILABLE,
                    "游戏服务器 " + serverId + " 在构建指令下发前断开");
        }

        log.info("BUILD 已下发 taskId={} server={} blocks={} modelUrl={}",
                taskId, serverId, model.blocks().size(), modelUrl);
        return new BuildResult(taskId, serverId, playerName, x, y, z, modelUrl);
    }

    private String resolveServerId(String requested) {
        if (requested != null && !requested.isBlank()) {
            String serverId = requested.trim();
            if (!SERVER_ID.matcher(serverId).matches()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "serverId 格式非法");
            }
            if (!registry.isConnected(serverId)) {
                throw new BusinessException(ErrorCode.GAME_SERVER_UNAVAILABLE,
                        "游戏服务器 " + serverId + " 未连接");
            }
            return serverId;
        }

        List<String> serverIds = registry.serverIds();
        if (serverIds.isEmpty()) {
            throw new BusinessException(ErrorCode.GAME_SERVER_UNAVAILABLE, "当前没有已连接的游戏服务器");
        }
        if (serverIds.size() > 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "存在多个已连接的游戏服务器，请显式指定 serverId");
        }
        return serverIds.get(0);
    }

    private static String validateAndTrimPlayerName(String playerName) {
        String value = playerName == null ? "" : playerName.trim();
        if (!PLAYER_NAME.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "玩家名需为 3-16 位字母、数字或下划线");
        }
        return value;
    }

    private static String validatePrompt(String prompt) {
        String value = prompt == null ? "" : prompt.trim();
        if (value.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "提示词不能为空");
        }
        if (value.length() > MAX_PROMPT_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "提示词不能超过 " + MAX_PROMPT_LENGTH + " 字符");
        }
        return value;
    }

    private static int validateCoordinate(Integer value, String name) {
        if (value == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, name + " 坐标不能为空");
        }
        if (value < -WORLD_LIMIT || value > WORLD_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, name + " 坐标超出 Minecraft 世界边界");
        }
        return value;
    }

    private static String joinBaseUrl(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法确定模型对外地址");
        }
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }
}
