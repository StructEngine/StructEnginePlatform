package com.structengine.platform.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Spring AI OpenAI-compatible 对话封装。
 */
@Service
public class AiChatService {

    private final ChatClient chatClient;

    public AiChatService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String complete(String systemPrompt, String userPrompt) {
        String content = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("AI 返回为空");
        }
        return content;
    }
}
