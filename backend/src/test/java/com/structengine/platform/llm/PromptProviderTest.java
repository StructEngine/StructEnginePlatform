package com.structengine.platform.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptProviderTest {

    @Test
    void incompleteViewsAskModelToInferMissingDimensions() {
        String instruction = PromptProvider.userInstruction(2, "石砖材质", 20000);
        assertTrue(instruction.contains("参考图数量少于 3 张"));
        assertTrue(instruction.contains("推断缺失方向"));
        assertTrue(instruction.contains("石砖材质"));
        assertTrue(instruction.contains("20000"));
    }

    @Test
    void completeViewsUseIntersection() {
        String instruction = PromptProvider.userInstruction(3, "", 500);
        assertTrue(instruction.contains("参考图覆盖三视图"));
        assertTrue(instruction.contains("500"));
        assertFalse(instruction.contains("用户补充说明"));
    }

    @Test
    void systemPromptAllowsPartialReferences() {
        String prompt = PromptProvider.systemPrompt();
        assertTrue(prompt.contains("不要求齐全"));
        assertTrue(prompt.contains("minecraft:"));
        assertTrue(prompt.contains("纯 JSON"));
    }
}
