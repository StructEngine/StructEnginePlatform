package com.structengine.platform.game.web;

import com.structengine.platform.ai.AiChatService;
import com.structengine.platform.ai.ModelGenerationService;
import com.structengine.platform.chat.ChatMessage;
import com.structengine.platform.chat.ChatRecord;
import com.structengine.platform.chat.ChatRepository;
import com.structengine.platform.common.ApiResponse;
import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.domain.VoxelBlock;
import com.structengine.platform.game.protocol.HistoryEntry;
import com.structengine.platform.game.service.BaseUrlResolver;
import com.structengine.platform.game.service.DeployService;
import com.structengine.platform.id.SnowflakeIdGenerator;
import com.structengine.platform.model.ModelArtifact;
import com.structengine.platform.model.ModelArtifactService;
import com.structengine.platform.model.ModelDraft;
import com.structengine.platform.model.ModelView;
import com.structengine.platform.security.AuthPrincipal;
import com.structengine.platform.server.MinecraftServer;
import com.structengine.platform.server.ServerService;
import com.structengine.platform.service.GenerateService;
import com.structengine.platform.service.HistoryRpcService;
import com.structengine.platform.task.BuildTask;
import com.structengine.platform.task.BuildTaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 服务器上下文业务接口：AI 对话、模型生成、模型产物、部署/任务、插件历史。
 *
 * <p>所有入口先按 serverId 校验归属；管理员可操作全部服务器。</p>
 */
@RestController
@RequestMapping("/api/servers/{serverId}")
public class ServerBusinessController {

    private final ServerService serverService;
    private final AiChatService aiChatService;
    private final ChatRepository chatRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final ModelGenerationService modelGenerationService;
    private final GenerateService generateService;
    private final ModelArtifactService modelService;
    private final DeployService deployService;
    private final BuildTaskService taskService;
    private final HistoryRpcService historyRpcService;
    private final BaseUrlResolver baseUrlResolver;

    public ServerBusinessController(ServerService serverService, AiChatService aiChatService,
                                    ChatRepository chatRepository,
                                    SnowflakeIdGenerator idGenerator,
                                    ModelGenerationService modelGenerationService,
                                    GenerateService generateService,
                                    ModelArtifactService modelService,
                                    DeployService deployService,
                                    BuildTaskService taskService,
                                    HistoryRpcService historyRpcService,
                                    BaseUrlResolver baseUrlResolver) {
        this.serverService = serverService;
        this.aiChatService = aiChatService;
        this.chatRepository = chatRepository;
        this.idGenerator = idGenerator;
        this.modelGenerationService = modelGenerationService;
        this.generateService = generateService;
        this.modelService = modelService;
        this.deployService = deployService;
        this.taskService = taskService;
        this.historyRpcService = historyRpcService;
        this.baseUrlResolver = baseUrlResolver;
    }

    @PostMapping("/ai/chat")
    public ApiResponse<ChatReply> chat(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable String serverId,
                                       @Valid @RequestBody ChatRequest request) {
        MinecraftServer server = accessible(principal, serverId);
        String sessionId = normalizeSession(request.sessionId());
        appendChat(principal, server, sessionId, "user", request.message());
        String reply = aiChatService.complete("""
                你是 StructEngine 的建筑设计助手。
                与用户讨论建筑风格、尺寸、材料和结构。
                不要输出 voxel JSON，最终模型由独立的生成接口完成。
                回答使用中文，简洁、具体、可执行。
                """, request.message());
        appendChat(principal, server, sessionId, "assistant", reply);
        return ApiResponse.ok(new ChatReply(reply));
    }

    @GetMapping("/ai/chat/{sessionId}")
    public ApiResponse<List<ChatRecord>> chatHistory(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @PathVariable String serverId,
                                                     @PathVariable String sessionId) {
        MinecraftServer server = accessible(principal, serverId);
        return ApiResponse.ok(chatRepository.list(server.id(), principal.type().name(),
                        principal.id(), normalizeSession(sessionId))
                .stream()
                .map(message -> new ChatRecord(message.role(), message.content(), message.createdAt()))
                .toList());
    }

    @PostMapping("/models/generate")
    public ApiResponse<ModelGenerationReply> generateModel(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String serverId,
            @Valid @RequestBody GenerateRequest request,
            HttpServletRequest httpRequest) {
        MinecraftServer server = accessible(principal, serverId);
        ModelDraft draft = modelGenerationService.generate(server.ownerUserId(), server.id(),
                request.prompt(), baseUrlResolver.resolve(httpRequest));
        return ApiResponse.ok(toReply(draft));
    }

    @PostMapping("/models/generate-from-images")
    public ApiResponse<ModelGenerationReply> generateFromImages(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String serverId,
            @RequestParam(value = "images", required = false) MultipartFile[] images,
            @RequestParam(value = "description", required = false) String description,
            HttpServletRequest httpRequest) {
        MinecraftServer server = accessible(principal, serverId);
        ModelDraft draft = generateService.generate(server.ownerUserId(), server.id(),
                images, description, baseUrlResolver.resolve(httpRequest));
        return ApiResponse.ok(toReply(draft));
    }

    @GetMapping("/models")
    public ApiResponse<com.structengine.platform.auth.PageResponse<ModelView>> models(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String serverId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String source) {
        MinecraftServer server = accessible(principal, serverId);
        return ApiResponse.ok(modelService.listByServer(server.id(), source, page, size));
    }

    @DeleteMapping("/models/{modelId}")
    public ApiResponse<Void> deleteModel(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable String serverId,
                                         @PathVariable String modelId) {
        MinecraftServer server = accessible(principal, serverId);
        modelService.softDelete(server.id(), parseId(modelId));
        return ApiResponse.ok(null);
    }

    @PostMapping("/tasks/deploy")
    public ApiResponse<TaskView> deploy(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable String serverId,
                                        @Valid @RequestBody DeployRequest request,
                                        HttpServletRequest httpRequest) {
        MinecraftServer server = accessible(principal, serverId);
        BuildTask task = deployService.deploy(server.ownerUserId(), server.id(),
                request.playerUuid(), request.playerName(), parseId(request.modelId()),
                request.centerX(), request.centerZ(), request.rotation(),
                baseUrlResolver.resolve(httpRequest));
        return ApiResponse.ok(toView(task));
    }

    @GetMapping("/tasks")
    public ApiResponse<List<TaskView>> tasks(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable String serverId,
                                             @RequestParam(required = false) String playerUuid) {
        MinecraftServer server = accessible(principal, serverId);
        return ApiResponse.ok(taskService.list(server.id(), playerUuid).stream()
                .map(ServerBusinessController::toView).toList());
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<TaskView> task(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable String serverId,
                                      @PathVariable String taskId) {
        MinecraftServer server = accessible(principal, serverId);
        BuildTask task = taskService.find(parseId(taskId))
                .filter(value -> value.serverId() == server.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "任务不存在"));
        return ApiResponse.ok(toView(task));
    }

    @GetMapping("/history")
    public ApiResponse<List<HistoryEntry>> history(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable String serverId,
                                                   @RequestParam String playerUuid,
                                                   @RequestParam String playerName) {
        MinecraftServer server = accessible(principal, serverId);
        return ApiResponse.ok(historyRpcService.history(Long.toString(server.id()),
                playerUuid, playerName));
    }

    @PostMapping("/history/{historyId}/rollback")
    public ApiResponse<HistoryAction> rollback(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable String serverId,
                                               @PathVariable String historyId,
                                               @Valid @RequestBody HistoryActionRequest request) {
        MinecraftServer server = accessible(principal, serverId);
        String requestId = historyRpcService.rollback(Long.toString(server.id()),
                request.playerUuid(), request.playerName(), historyId);
        return ApiResponse.ok(new HistoryAction(requestId));
    }

    @PostMapping("/history/{historyId}/redo")
    public ApiResponse<HistoryAction> redo(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable String serverId,
                                           @PathVariable String historyId,
                                           @Valid @RequestBody HistoryActionRequest request) {
        MinecraftServer server = accessible(principal, serverId);
        String requestId = historyRpcService.redo(Long.toString(server.id()),
                request.playerUuid(), request.playerName(), historyId);
        return ApiResponse.ok(new HistoryAction(requestId));
    }

    private MinecraftServer accessible(AuthPrincipal principal, String serverId) {
        return serverService.requireAccessible(principal, parseId(serverId));
    }

    private void appendChat(AuthPrincipal principal, MinecraftServer server, String sessionId,
                            String role, String content) {
        chatRepository.insert(new ChatMessage(idGenerator.nextId(), server.id(), sessionId,
                principal.type().name(), principal.id(), role, content,
                System.currentTimeMillis()));
    }

    private static String normalizeSession(String sessionId) {
        String value = sessionId == null || sessionId.isBlank() ? "default" : sessionId.trim();
        if (value.length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "sessionId 不能超过 64 个字符");
        }
        return value;
    }

    private static long parseId(String value) {
        try {
            return SnowflakeIdGenerator.parse(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ID 格式非法");
        }
    }

    private static ModelGenerationReply toReply(ModelDraft draft) {
        ModelArtifact model = draft.artifact();
        return new ModelGenerationReply(Long.toString(model.id()), model.source(),
                model.modelUrl(), model.blockCount(), model.minX(), model.maxX(),
                model.minY(), model.maxY(), model.minZ(), model.maxZ(), model.prompt(),
                draft.blocks());
    }

    private static TaskView toView(BuildTask task) {
        return new TaskView(Long.toString(task.id()), Long.toString(task.serverId()),
                Long.toString(task.modelId()), task.playerUuid(), task.playerName(),
                task.centerX(), task.centerY(), task.centerZ(), task.originX(), task.originY(),
                task.originZ(), task.rotation(), task.status(), task.placed(), task.total(),
                task.error(), task.createdAt(), task.updatedAt());
    }

    public record ChatRequest(String sessionId,
                              @NotBlank(message = "消息不能为空") String message) {
    }

    public record ChatReply(String reply) {
    }

    public record GenerateRequest(@NotBlank(message = "提示词不能为空")
                                  @Size(max = 1000, message = "提示词不能超过 1000 字")
                                  String prompt) {
    }

    public record ModelGenerationReply(String modelId, String source, String modelUrl,
                                       int blockCount,
                                       int minX, int maxX, int minY, int maxY,
                                       int minZ, int maxZ, String prompt,
                                       List<VoxelBlock> blocks) {
    }

    public record DeployRequest(String playerUuid,
                                @NotBlank String playerName,
                                @NotBlank String modelId,
                                int centerX, int centerZ, int rotation) {
    }

    public record TaskView(String id, String serverId, String modelId, String playerUuid,
                           String playerName, int centerX, int centerY, int centerZ,
                           int originX, int originY, int originZ, int rotation,
                           String status, int placed, int total, String error,
                           long createdAt, long updatedAt) {
    }

    public record HistoryActionRequest(@NotBlank String playerUuid,
                                       @NotBlank String playerName) {
    }

    public record HistoryAction(String requestId) {
    }
}
