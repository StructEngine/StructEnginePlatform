package com.structengine.platform.model;

import com.structengine.platform.auth.PageResponse;
import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.domain.VoxelBlock;
import com.structengine.platform.id.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 模型产物登记、查询与软删除。
 */
@Service
public class ModelArtifactService {

    private final ModelArtifactRepository repository;
    private final SnowflakeIdGenerator idGenerator;

    public ModelArtifactService(ModelArtifactRepository repository,
                                SnowflakeIdGenerator idGenerator) {
        this.repository = repository;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public ModelArtifact create(long ownerUserId, long serverId, String source,
                                String prompt, String modelUrl, List<VoxelBlock> blocks) {
        return create(idGenerator.nextId(), ownerUserId, serverId, source, prompt, modelUrl, blocks);
    }

    @Transactional
    public ModelArtifact create(long id, long ownerUserId, long serverId, String source,
                                String prompt, String modelUrl, List<VoxelBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            throw new BusinessException(ErrorCode.UNPROCESSABLE);
        }
        int minX = blocks.stream().mapToInt(VoxelBlock::x).min().orElse(0);
        int maxX = blocks.stream().mapToInt(VoxelBlock::x).max().orElse(0);
        int minY = blocks.stream().mapToInt(VoxelBlock::y).min().orElse(0);
        int maxY = blocks.stream().mapToInt(VoxelBlock::y).max().orElse(0);
        int minZ = blocks.stream().mapToInt(VoxelBlock::z).min().orElse(0);
        int maxZ = blocks.stream().mapToInt(VoxelBlock::z).max().orElse(0);
        ModelArtifact artifact = new ModelArtifact(id, ownerUserId, serverId,
                source, prompt, modelUrl, blocks.size(), minX, maxX, minY, maxY, minZ, maxZ,
                System.currentTimeMillis(), null);
        repository.insert(artifact);
        return artifact;
    }

    public ModelArtifact requireActive(long serverId, long modelId) {
        return repository.findById(modelId)
                .filter(model -> !model.deleted())
                .filter(model -> model.serverId() == serverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型不存在"));
    }

    public PageResponse<ModelView> listByServer(long serverId, String source, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        long total = repository.countByServer(serverId, source);
        List<ModelView> items = repository.listByServer(serverId, source,
                        (safePage - 1) * safeSize, safeSize)
                .stream().map(ModelArtifactService::toView).toList();
        return new PageResponse<>(items, safePage, safeSize, total);
    }

    public PageResponse<ModelView> listAll(String source, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        long total = repository.countAll(source);
        List<ModelView> items = repository.listAll(source, (safePage - 1) * safeSize, safeSize)
                .stream().map(ModelArtifactService::toView).toList();
        return new PageResponse<>(items, safePage, safeSize, total);
    }

    @Transactional
    public void softDelete(long serverId, long modelId) {
        ModelArtifact model = requireActive(serverId, modelId);
        repository.softDelete(model.id(), System.currentTimeMillis());
    }

    public static ModelView toView(ModelArtifact model) {
        return new ModelView(Long.toString(model.id()), model.source(), model.prompt(),
                model.modelUrl(), model.blockCount(), model.minX(), model.maxX(),
                model.minY(), model.maxY(), model.minZ(), model.maxZ(), model.createdAt(),
                Long.toString(model.serverId()), Long.toString(model.ownerUserId()));
    }
}
