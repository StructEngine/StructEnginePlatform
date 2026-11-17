package com.structengine.platform.web;

import com.structengine.platform.auth.PageResponse;
import com.structengine.platform.common.ApiResponse;
import com.structengine.platform.model.ModelArtifactService;
import com.structengine.platform.model.ModelView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员查看全部模型产物。
 */
@RestController
@RequestMapping("/api/admin/models")
@PreAuthorize("hasRole('ADMIN')")
public class AdminModelController {

    private final ModelArtifactService modelService;

    public AdminModelController(ModelArtifactService modelService) {
        this.modelService = modelService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ModelView>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String source) {
        return ApiResponse.ok(modelService.listAll(source, page, size));
    }
}
