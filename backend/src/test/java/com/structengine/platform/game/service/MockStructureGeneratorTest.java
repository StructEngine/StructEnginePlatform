package com.structengine.platform.game.service;

import com.structengine.platform.domain.ModelData;
import com.structengine.platform.domain.VoxelBlock;
import com.structengine.platform.game.model.StructureRequest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockStructureGeneratorTest {

    private final MockStructureGenerator generator = new MockStructureGenerator();

    @Test
    void generatesValidNonNegativeUniqueBlocks() {
        ModelData model = generator.generate(new StructureRequest("Steve", 100, 64, -200, "一座小木屋"));
        model.validate();

        assertTrue(model.blocks().size() > 100);
        Set<String> coordinates = new HashSet<>();
        for (VoxelBlock block : model.blocks()) {
            assertTrue(block.x() >= 0);
            assertTrue(block.y() >= 1, "地基应从 y=1 开始，避免覆盖地表方块");
            assertTrue(block.z() >= 0);
            assertTrue(VoxelBlock.isValidId(block.id()));
            assertTrue(coordinates.add(block.x() + "," + block.y() + "," + block.z()), "方块坐标不能重复");
        }
    }

    @Test
    void isDeterministicForSamePromptAndPlayer() {
        StructureRequest request = new StructureRequest("Alex", 0, 70, 0, "中世纪木屋");
        assertEquals(generator.generate(request).serialize(), generator.generate(request).serialize());
    }

    @Test
    void promptKeywordsSelectPalette() {
        ModelData red = generator.generate(new StructureRequest("Steve", 0, 64, 0, "红石城堡"));
        assertTrue(red.serialize().contains("minecraft:red_wool"));
        assertTrue(red.serialize().contains("minecraft:bricks"));

        ModelData ice = generator.generate(new StructureRequest("Steve", 0, 64, 0, "冰雪塔楼"));
        assertTrue(ice.serialize().contains("minecraft:packed_ice"));

        ModelData modern = generator.generate(new StructureRequest("Steve", 0, 64, 0, "现代玻璃别墅"));
        assertTrue(modern.serialize().contains("minecraft:quartz_block"));
    }

    @Test
    void differentPromptsProduceDifferentStructures() {
        ModelData first = generator.generate(new StructureRequest("Steve", 0, 64, 0, "塔"));
        ModelData second = generator.generate(new StructureRequest("Steve", 0, 64, 0, "城堡"));
        assertFalse(first.serialize().equals(second.serialize()));
    }

    @Test
    void containsWindowsDoorGapAndLantern() {
        ModelData model = generator.generate(new StructureRequest("Steve", 0, 64, 0, "温馨小屋"));
        String json = model.serialize();
        assertTrue(json.contains("minecraft:glass"));
        assertTrue(json.contains("minecraft:lantern"));
        assertTrue(json.contains("minecraft:gravel"));

        Set<Integer> frontWall = new HashSet<>();
        int maxX = 0;
        for (VoxelBlock block : model.blocks()) {
            if (block.y() == 2 && block.z() == 1) {
                frontWall.add(block.x());
                maxX = Math.max(maxX, block.x());
            }
        }
        long gaps = 0;
        for (int x = 1; x <= maxX; x++) {
            if (!frontWall.contains(x)) {
                gaps++;
            }
        }
        assertEquals(1, gaps, "正面中央应留出 1 列 2 格高门洞");
    }
}
