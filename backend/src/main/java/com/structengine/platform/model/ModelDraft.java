package com.structengine.platform.model;

import com.structengine.platform.domain.VoxelBlock;

import java.util.List;

public record ModelDraft(ModelArtifact artifact, List<VoxelBlock> blocks) {
}
