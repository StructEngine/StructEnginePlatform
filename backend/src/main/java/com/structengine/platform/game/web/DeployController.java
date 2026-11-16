package com.structengine.platform.game.web;

import com.structengine.platform.common.ApiResponse;
import com.structengine.platform.game.service.BaseUrlResolver;
import com.structengine.platform.game.service.DeployService;
import com.structengine.platform.service.PlatformStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生成模型后的部署与任务状态接口。
 */
@RestController
@RequestMapping("/api/tasks")
public class DeployController {

    private final DeployService deployService;
    private final BaseUrlResolver baseUrlResolver;
    private final PlatformStore store;

    public DeployController(DeployService deployService, BaseUrlResolver baseUrlResolver,
                            PlatformStore store) {
        this.deployService = deployService;
        this.baseUrlResolver = baseUrlResolver;
        this.store = store;
    }

    @PostMapping("/deploy")
    public ApiResponse<PlatformStore.TaskRecord> deploy(@RequestBody DeployRequest request,
                                                        HttpServletRequest httpRequest) {
        String baseUrl = baseUrlResolver.resolve(httpRequest);
        return ApiResponse.ok(deployService.deploy(request.serverId(), request.playerUuid(),
                request.playerName(), request.modelId(), request.centerX(), request.centerZ(),
                request.rotation(), baseUrl));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<PlatformStore.TaskRecord> task(@PathVariable String taskId) {
        return store.findTask(taskId)
                .map(ApiResponse::ok)
                .orElseGet(() -> new ApiResponse<>(404, "任务不存在", null));
    }

    @GetMapping
    public ApiResponse<java.util.List<PlatformStore.TaskRecord>> tasks(
            @org.springframework.web.bind.annotation.RequestParam String serverId,
            @org.springframework.web.bind.annotation.RequestParam String playerUuid) {
        return ApiResponse.ok(store.listTasks(serverId, playerUuid));
    }

    public record DeployRequest(String serverId, String playerUuid, String playerName,
                                String modelId, int centerX, int centerZ, int rotation) {
    }
}
