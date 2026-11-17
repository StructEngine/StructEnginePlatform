package com.structengine.platform.model;

import com.structengine.platform.auth.PageResponse;
import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.domain.VoxelBlock;
import com.structengine.platform.id.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelArtifactServiceTest {

    @Mock
    private ModelArtifactRepository repository;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    private ModelArtifactService service;

    @BeforeEach
    void setUp() {
        service = new ModelArtifactService(repository, idGenerator);
    }

    @Test
    void createsArtifactWithBoundingBox() {
        when(idGenerator.nextId()).thenReturn(100L);
        List<VoxelBlock> blocks = List.of(
                new VoxelBlock("minecraft:stone", 0, 0, 0),
                new VoxelBlock("minecraft:oak_planks", 3, 5, 2));

        ModelArtifact artifact = service.create(7L, 9L, "MAP_AI", "小屋",
                "http://x/models/100.json", blocks);

        assertEquals(100L, artifact.id());
        assertEquals(7L, artifact.ownerUserId());
        assertEquals(9L, artifact.serverId());
        assertEquals(2, artifact.blockCount());
        assertEquals(0, artifact.minX());
        assertEquals(3, artifact.maxX());
        assertEquals(5, artifact.maxY());
        assertEquals(2, artifact.maxZ());

        ArgumentCaptor<ModelArtifact> captor = ArgumentCaptor.forClass(ModelArtifact.class);
        verify(repository).insert(captor.capture());
        assertEquals(100L, captor.getValue().id());
    }

    @Test
    void rejectsEmptyBlocks() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(1L, 2L, "MAP_AI", "x", "u", List.of()));
        assertEquals(ErrorCode.UNPROCESSABLE, error.errorCode());
    }

    @Test
    void requireActiveRejectsWrongServerOrDeleted() {
        ModelArtifact model = new ModelArtifact(1L, 2L, 3L, "MAP_AI", "p", "u",
                1, 0, 0, 0, 0, 0, 0, 1L, null);
        when(repository.findById(1L)).thenReturn(Optional.of(model));

        assertEquals(1L, service.requireActive(3L, 1L).id());
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.requireActive(4L, 1L));
        assertEquals(ErrorCode.NOT_FOUND, error.errorCode());

        ModelArtifact deleted = new ModelArtifact(1L, 2L, 3L, "MAP_AI", "p", "u",
                1, 0, 0, 0, 0, 0, 0, 1L, 2L);
        when(repository.findById(1L)).thenReturn(Optional.of(deleted));
        assertThrows(BusinessException.class, () -> service.requireActive(3L, 1L));
    }

    @Test
    void softDeletesExistingModel() {
        ModelArtifact model = new ModelArtifact(1L, 2L, 3L, "MAP_AI", "p", "u",
                1, 0, 0, 0, 0, 0, 0, 1L, null);
        when(repository.findById(1L)).thenReturn(Optional.of(model));

        service.softDelete(3L, 1L);

        verify(repository).softDelete(anyLong(), anyLong());
    }

    @Test
    void listsServerModelsWithPagination() {
        ModelArtifact model = new ModelArtifact(1L, 2L, 3L, "REFERENCE_IMAGE", "p", "u",
                1, 0, 0, 0, 0, 0, 0, 1L, null);
        when(repository.countByServer(3L, "REFERENCE_IMAGE")).thenReturn(1L);
        when(repository.listByServer(3L, "REFERENCE_IMAGE", 0, 20)).thenReturn(List.of(model));

        PageResponse<ModelView> page = service.listByServer(3L, "REFERENCE_IMAGE", 1, 20);

        assertEquals(1, page.items().size());
        assertEquals("1", page.items().get(0).modelId());
        assertEquals(1L, page.total());
    }
}
