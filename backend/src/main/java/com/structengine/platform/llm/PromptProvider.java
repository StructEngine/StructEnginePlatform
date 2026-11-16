package com.structengine.platform.llm;

/**
 * 参考图逆向建模的提示词。
 *
 * <p>三视图（正视、侧视、俯视）只是推荐输入，不要求齐全；当参考图不足三视图时，
 * 由多模态模型结合已有视角与补充说明推断完整建筑结构。</p>
 */
public final class PromptProvider {

    private static final String SYSTEM_PROMPT = """
            你是 Minecraft 体素建筑生成引擎，精通从参考图逆向还原三维建筑。
            用户会提供 1~3 张参考图。三视图（正视、侧视、俯视）只是推荐输入，不要求齐全：
            1. 三视图齐全时，按三个正交投影的交集精确重建；
            2. 参考图不足三视图时，根据已有视角的轮廓、比例、颜色、重复元素与建筑常识推断缺失维度，
               补全进深、侧面和屋顶，生成完整、可建造的建筑；
            3. 参考图不是严格三视图时（例如单张透视图、照片、拼合三视图或 AI 概念图），
               先识别建筑类型、层数、屋顶、门窗和材质，再进行体素化。
            通用要求：
            - 使用 0,0,0 作为最小包围盒底角，所有坐标必须是非负整数，y=0 表示地面第一层。
            - 只在实际存在的结构位置放置方块，墙体可以中空，不要生成实心大方块。
            - 将参考图颜色映射到最接近的 Minecraft 方块 ID，必须使用 minecraft: 命名空间。
            - 建筑结构应完整、稳定、可站立、可回滚；不要只生成正面薄片或半成品。
            - 只输出纯 JSON 二维紧凑数组，每个元素为 ["minecraft:block_id", x, y, z]；
              不要 Markdown 代码块、不要解释、不要任何多余文本。
            """;

    private PromptProvider() {
    }

    public static String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    /**
     * 生成随图片一起发送的用户指令，把图片数量、缺失视图处理和补充说明显式告知多模态模型。
     *
     * @param imageCount  参考图数量（1~3）
     * @param description 可选的用户补充说明
     * @param maxBlocks   方块总数上限
     */
    public static String userInstruction(int imageCount, String description, int maxBlocks) {
        StringBuilder instruction = new StringBuilder();
        instruction.append("本次共上传 ").append(imageCount).append(" 张参考图。\n");
        if (imageCount < 3) {
            instruction.append("参考图数量少于 3 张：若图片本身包含完整的拼合三视图，请按三视图精确重建；")
                    .append("否则请利用已有视角推断缺失方向，补全建筑的进深、侧面和屋顶，")
                    .append("生成完整建筑，不要只输出正面薄片或半成品。\n");
        } else {
            instruction.append("参考图覆盖三视图：请以三个正交投影的交集精确重建。\n");
        }
        String extra = description == null ? "" : description.trim();
        if (!extra.isEmpty()) {
            instruction.append("用户补充说明：").append(extra).append('\n');
        }
        instruction.append("方块总数请控制在 ").append(Math.max(1, maxBlocks)).append(" 个以内。")
                .append("直接输出纯 JSON 数组，不要 Markdown 代码块或解释。");
        return instruction.toString();
    }
}
