package com.structengine.platform.ai;

import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.config.AppProperties;
import com.structengine.platform.domain.ModelData;
import com.structengine.platform.domain.VoxelBlock;
import com.structengine.platform.id.SnowflakeIdGenerator;
import com.structengine.platform.llm.JsonSanitizer;
import com.structengine.platform.model.ModelArtifact;
import com.structengine.platform.model.ModelArtifactService;
import com.structengine.platform.model.ModelDraft;
import com.structengine.platform.service.FileStorageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AI 对话描述生成严格 voxel JSON，归一化、落盘并登记模型产物。
 */
@Service
public class ModelGenerationService {

    private static final String SYSTEM_PROMPT = """
            你是 Minecraft 体素建筑生成器。
            只输出一个 JSON 数组，不要 Markdown、不要解释。
            每个元素必须是 ["namespace:block_id", x, y, z]。
            坐标为相对坐标，必须是非负整数；y=0 表示地面以上的第一层。
            方块 ID 必须使用 minecraft: 命名空间。
            建筑中心由平台根据包围盒自动计算，你不需要输出中心。
            尽量控制在 %d 个方块以内，结构必须完整、可站立、可回滚。
            """;

    private final AiChatService aiChatService;
    private final JsonSanitizer sanitizer;
    private final FileStorageService fileStorage;
    private final ModelArtifactService modelService;
    private final SnowflakeIdGenerator idGenerator;
    private final AppProperties props;

    public ModelGenerationService(AiChatService aiChatService, JsonSanitizer sanitizer,
                                  FileStorageService fileStorage,
                                  ModelArtifactService modelService,
                                  SnowflakeIdGenerator idGenerator,
                                  AppProperties props) {
        this.aiChatService = aiChatService;
        this.sanitizer = sanitizer;
        this.fileStorage = fileStorage;
        this.modelService = modelService;
        this.idGenerator = idGenerator;
        this.props = props;
    }

    public ModelDraft generate(long ownerUserId, long serverId, String prompt, String baseUrl) {
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "提示词不能为空");
        }
        String raw = aiChatService.complete(String.format(SYSTEM_PROMPT, props.getMaxModelBlocks()),
                prompt.trim());
        List<VoxelBlock> blocks = sanitizer.sanitize(raw);
        if (blocks.size() > props.getMaxModelBlocks()) {
            throw new BusinessException(ErrorCode.UNPROCESSABLE,
                    "模型方块数超过限制 " + props.getMaxModelBlocks());
        }

        int minX = blocks.stream().mapToInt(VoxelBlock::x).min().orElse(0);
        int minY = blocks.stream().mapToInt(VoxelBlock::y).min().orElse(0);
        int minZ = blocks.stream().mapToInt(VoxelBlock::z).min().orElse(0);

        List<VoxelBlock> normalized = new ArrayList<>(blocks.size());
        for (VoxelBlock block : blocks) {
            normalized.add(new VoxelBlock(block.id(), block.x() - minX,
                    block.y() - minY, block.z() - minZ));
        }
        normalized.sort(Comparator.comparingInt(VoxelBlock::y)
                .thenComparingInt(VoxelBlock::x).thenComparingInt(VoxelBlock::z));
        ModelData model = new ModelData(normalized);
        model.validate();

        long modelId = idGenerator.nextId();
        String modelIdText = Long.toString(modelId);
        fileStorage.saveModel(modelIdText, model.serialize());
        String modelUrl = join(baseUrl, "/models/" + modelIdText + ".json");
        ModelArtifact artifact = modelService.create(modelId, ownerUserId, serverId,
                "MAP_AI", prompt.trim(), modelUrl, normalized);
        return new ModelDraft(artifact, normalized);
    }

    private static String join(String base, String path) {
        String value = base == null ? "" : base.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value + path;
    }
}
