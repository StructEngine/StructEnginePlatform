package com.structengine.platform.ai;

import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.llm.ImageMimeType;
import com.structengine.platform.llm.PromptProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI 多模态参考图推理。所有 AI 调用统一走 spring.ai.openai.* 配置。
 */
@Service
public class VisionAiService {

    private final ChatClient chatClient;

    public VisionAiService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String generate(byte[][] images, String instruction) {
        if (images == null || images.length == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "至少需要 1 张参考图");
        }
        List<Media> media = new ArrayList<>(images.length);
        for (byte[] image : images) {
            String mime = ImageMimeType.detect(image);
            MimeType mimeType = MimeTypeUtils.parseMimeType(
                    mime == null ? MimeTypeUtils.IMAGE_JPEG_VALUE : mime);
            media.add(Media.builder()
                    .mimeType(mimeType)
                    .data(new ByteArrayResource(image))
                    .build());
        }
        UserMessage userMessage = UserMessage.builder()
                .text(instruction == null ? "" : instruction)
                .media(media)
                .build();
        try {
            String content = chatClient.prompt()
                    .system(PromptProvider.systemPrompt())
                    .messages(userMessage)
                    .call()
                    .content();
            if (content == null || content.isBlank()) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "AI 返回为空");
            }
            return content;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "参考图推理失败");
        }
    }
}
