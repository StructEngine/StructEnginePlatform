package com.structengine.platform.web;

import com.structengine.platform.ai.AiChatService;
import com.structengine.platform.ai.ModelGenerationService;
import com.structengine.platform.common.ApiResponse;
import com.structengine.platform.domain.VoxelBlock;
import com.structengine.platform.game.service.BaseUrlResolver;
import com.structengine.platform.service.PlatformStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 对话与模型生成接口。
 */
@RestController
@RequestMapping("/api")
public class AiModelController {

    private final AiChatService aiChatService;
    private final ModelGenerationService modelGenerationService;
    private final PlatformStore store;
    private final BaseUrlResolver baseUrlResolver;

    public AiModelController(AiChatService aiChatService,
                             ModelGenerationService modelGenerationService,
                             PlatformStore store,
                             BaseUrlResolver baseUrlResolver) {
        this.aiChatService = aiChatService;
        this.modelGenerationService = modelGenerationService;
        this.store = store;
        this.baseUrlResolver = baseUrlResolver;
    }

    @PostMapping("/ai/chat")
    public ApiResponse<ChatReply> chat(@RequestBody ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return new ApiResponse<>(400, "消息不能为空", null);
        }
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? "default" : request.sessionId();
        store.appendChat(sessionId, "user", request.message());
        String reply = aiChatService.complete("""
                你是 StructEngine 的建筑设计助手。
                与用户讨论建筑风格、尺寸、材料和结构。
                不要输出 voxel JSON，最终模型由独立的生成接口完成。
                回答使用中文，简洁、具体、可执行。
                """, request.message());
        store.appendChat(sessionId, "assistant", reply);
        return ApiResponse.ok(new ChatReply(reply));
    }

    @GetMapping("/ai/chat/{sessionId}")
    public ApiResponse<List<PlatformStore.ChatRecord>> history(@PathVariable String sessionId) {
        return ApiResponse.ok(store.chatHistory(sessionId));
    }

    @PostMapping("/models/generate")
    public ApiResponse<GenerateReply> generate(@RequestBody GenerateRequest request,
                                               HttpServletRequest httpRequest) {
        String baseUrl = baseUrlResolver.resolve(httpRequest);
        ModelGenerationService.Draft draft = modelGenerationService.generate(
                request.serverId(), request.playerUuid(), request.playerName(),
                request.prompt(), baseUrl);
        PlatformStore.ModelRecord record = draft.record();
        List<VoxelBlock> blocks = draft.blocks();
        return ApiResponse.ok(new GenerateReply(record.id(), record.modelUrl(),
                record.blockCount(), record.minX(), record.maxX(), record.minY(), record.maxY(),
                record.minZ(), record.maxZ(), record.prompt(), blocks));
    }

    public record ChatRequest(String sessionId, String message) {
    }

    public record ChatReply(String reply) {
    }

    public record GenerateRequest(String serverId, String playerUuid, String playerName, String prompt) {
    }

    public record GenerateReply(String modelId, String modelUrl, int blockCount,
                                int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                                String prompt, List<VoxelBlock> blocks) {
    }
}
