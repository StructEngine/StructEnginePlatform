package com.structengine.platform.game.service;

import com.structengine.platform.domain.ModelData;
import com.structengine.platform.domain.VoxelBlock;
import com.structengine.platform.game.model.StructureRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 确定性的 Mock 建筑生成器（Spring AI 接入前的占位实现）。
 *
 * <p>根据玩家名 + 提示词生成一座带地基、墙体、门窗、双层屋顶与灯笼的小屋。
 * 所有坐标均为相对原点 (0,0,0) 的非负整数：地基从 y=1 开始，避免直接覆盖
 * 插件回传的地表方块；Plugin 侧使用 {@code origin + offset} 得到绝对坐标。</p>
 */
@Service
public class MockStructureGenerator implements StructureGenerator {

    private static final int MAX_BLOCKS = 5000;

    @Override
    public ModelData generate(StructureRequest request) {
        int seed = seed(request.playerName(), request.prompt());
        Palette palette = palette(request.prompt(), seed);

        int width = 7 + ((seed >>> 2) % 3) * 2;
        int depth = 7 + ((seed >>> 4) % 3) * 2;
        int wallHeight = 3 + ((seed >>> 6) % 2);
        int centerX = 1 + width / 2;
        int centerZ = 1 + depth / 2;

        Map<String, VoxelBlock> blocks = new LinkedHashMap<>();

        // 地基：y=1 铺满内部，保证建筑不悬空。
        for (int x = 1; x <= width; x++) {
            for (int z = 1; z <= depth; z++) {
                add(blocks, palette.floor(), x, 1, z);
            }
        }

        // 墙体：四周边界；正面中央留 2 格高门洞。
        for (int y = 2; y <= 1 + wallHeight; y++) {
            for (int x = 1; x <= width; x++) {
                for (int z = 1; z <= depth; z++) {
                    boolean border = x == 1 || x == width || z == 1 || z == depth;
                    if (!border) {
                        continue;
                    }
                    boolean door = z == 1 && x == centerX && y <= 3;
                    if (door) {
                        continue;
                    }
                    boolean corner = (x == 1 || x == width) && (z == 1 || z == depth);
                    add(blocks, corner ? palette.accent() : palette.wall(), x, y, z);
                }
            }
        }

        // 窗户：四面各开两扇，覆盖墙体方块。
        int windowY = 3;
        if (wallHeight >= 3) {
            for (int x = 2; x < width; x += 2) {
                if (x != centerX) {
                    add(blocks, palette.window(), x, windowY, 1);
                    add(blocks, palette.window(), x, windowY, depth);
                }
            }
            for (int z = 2; z < depth; z += 2) {
                add(blocks, palette.window(), 1, windowY, z);
                add(blocks, palette.window(), width, windowY, z);
            }
        }

        // 双层屋顶：底层外扩 1 格形成屋檐，上层内收。
        for (int x = 0; x <= width + 1; x++) {
            for (int z = 0; z <= depth + 1; z++) {
                add(blocks, palette.roof(), x, wallHeight + 2, z);
            }
        }
        for (int x = 1; x <= width; x++) {
            for (int z = 1; z <= depth; z++) {
                add(blocks, palette.roof(), x, wallHeight + 3, z);
            }
        }

        // 屋顶灯笼与门前小路。
        add(blocks, "minecraft:lantern", centerX, wallHeight + 4, centerZ);
        add(blocks, "minecraft:gravel", centerX, 1, 0);

        List<VoxelBlock> result = new ArrayList<>(blocks.values());
        result.sort(Comparator.comparingInt(VoxelBlock::y)
                .thenComparingInt(VoxelBlock::x)
                .thenComparingInt(VoxelBlock::z));
        if (result.size() > MAX_BLOCKS) {
            result = new ArrayList<>(result.subList(0, MAX_BLOCKS));
        }
        return new ModelData(result);
    }

    private static int seed(String playerName, String prompt) {
        String text = (playerName == null ? "" : playerName) + "|" + (prompt == null ? "" : prompt);
        return text.hashCode() & 0x7fffffff;
    }

    private static Palette palette(String prompt, int seed) {
        String text = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        String wall;
        String floor;
        String roof;
        String accent;
        String window;

        if (containsAny(text, "红", "red", "砖", "brick")) {
            wall = "minecraft:bricks";
            floor = "minecraft:stone";
            roof = "minecraft:red_wool";
            accent = "minecraft:polished_blackstone";
            window = "minecraft:glass";
        } else if (containsAny(text, "冰", "雪", "ice", "snow", "frost")) {
            wall = "minecraft:packed_ice";
            floor = "minecraft:snow_block";
            roof = "minecraft:snow_block";
            accent = "minecraft:blue_ice";
            window = "minecraft:tinted_glass";
        } else if (containsAny(text, "现代", "玻璃", "modern", "glass", "quartz")) {
            wall = "minecraft:quartz_block";
            floor = "minecraft:smooth_stone";
            roof = "minecraft:glass";
            accent = "minecraft:quartz_pillar";
            window = "minecraft:glass";
        } else if (containsAny(text, "金", "豪华", "gold", "luxury")) {
            wall = "minecraft:white_terracotta";
            floor = "minecraft:polished_andesite";
            roof = "minecraft:gold_block";
            accent = "minecraft:quartz_pillar";
            window = "minecraft:glass";
        } else if (containsAny(text, "石", "城堡", "stone", "castle")) {
            wall = "minecraft:stone_bricks";
            floor = "minecraft:cobblestone";
            roof = "minecraft:deepslate_tiles";
            accent = "minecraft:polished_blackstone";
            window = "minecraft:glass";
        } else if (containsAny(text, "木", "wood", "cabin", "中世纪")) {
            wall = "minecraft:spruce_planks";
            floor = "minecraft:cobblestone";
            roof = "minecraft:dark_oak_planks";
            accent = "minecraft:stripped_oak_log";
            window = "minecraft:glass";
        } else {
            String[] walls = {
                    "minecraft:oak_planks",
                    "minecraft:stone_bricks",
                    "minecraft:white_terracotta",
                    "minecraft:spruce_planks",
                    "minecraft:deepslate_tiles"
            };
            String[] roofs = {
                    "minecraft:dark_oak_planks",
                    "minecraft:deepslate_tiles",
                    "minecraft:copper_block",
                    "minecraft:red_wool"
            };
            wall = walls[seed % walls.length];
            roof = roofs[(seed >>> 3) % roofs.length];
            floor = "minecraft:stone";
            accent = "minecraft:stripped_oak_log";
            window = "minecraft:glass";
        }
        return new Palette(wall, floor, roof, accent, window);
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static void add(Map<String, VoxelBlock> blocks, String id, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0) {
            throw new IllegalStateException("模型生成器产生了负坐标");
        }
        blocks.put(x + "," + y + "," + z, new VoxelBlock(id, x, y, z));
    }

    private record Palette(String wall, String floor, String roof, String accent, String window) {
    }
}
