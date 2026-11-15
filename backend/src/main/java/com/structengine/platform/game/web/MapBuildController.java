package com.structengine.platform.game.web;

import com.structengine.platform.common.ApiResponse;
import com.structengine.platform.game.model.BuildRequest;
import com.structengine.platform.game.model.BuildResult;
import com.structengine.platform.game.model.GameServerInfo;
import com.structengine.platform.game.service.BaseUrlResolver;
import com.structengine.platform.game.service.MapBuildService;
import com.structengine.platform.game.ws.GameServerRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 地图选点构建接口：
 * <ul>
 *   <li>{@code GET /api/map/servers}：已连接游戏服列表；</li>
 *   <li>{@code POST /api/map/build}：查询 Y、生成模型并下发 BUILD（请求线程为虚拟线程）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/map")
public class MapBuildController {

    private final MapBuildService mapBuildService;
    private final BaseUrlResolver baseUrlResolver;
    private final GameServerRegistry registry;

    public MapBuildController(MapBuildService mapBuildService,
                              BaseUrlResolver baseUrlResolver,
                              GameServerRegistry registry) {
        this.mapBuildService = mapBuildService;
        this.baseUrlResolver = baseUrlResolver;
        this.registry = registry;
    }

    @GetMapping("/servers")
    public ApiResponse<List<GameServerInfo>> servers() {
        List<GameServerInfo> servers = registry.serverIds().stream()
                .map(GameServerInfo::new)
                .toList();
        return ApiResponse.ok(servers);
    }

    @PostMapping("/build")
    public ApiResponse<BuildResult> build(@Valid @RequestBody BuildRequest request,
                                          HttpServletRequest httpRequest) {
        String baseUrl = baseUrlResolver.resolve(httpRequest);
        return ApiResponse.ok(mapBuildService.build(request, baseUrl));
    }
}
