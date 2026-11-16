package com.structengine.platform.llm;

import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.config.AppProperties;
import com.structengine.platform.llm.dto.ChatCompletionDtos.ChatCompletionRequest;
import com.structengine.platform.llm.dto.ChatCompletionDtos.ChatCompletionResponse;
import com.structengine.platform.llm.dto.ChatCompletionDtos.ImageContent;
import com.structengine.platform.llm.dto.ChatCompletionDtos.ImageUrl;
import com.structengine.platform.llm.dto.ChatCompletionDtos.Message;
import com.structengine.platform.llm.dto.ChatCompletionDtos.TextContent;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 基于 RestClient 的 OpenAI 兼容多模态调用实现。
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final AppProperties props;
    private final RestClient restClient;

    public OpenAiCompatibleLlmClient(AppProperties props, RestClient restClient) {
        this.props = props;
        this.restClient = restClient;
    }

    @Override
    public String generate(byte[][] images, String instruction) {
        List<Object> parts = new ArrayList<>(images.length + 1);
        String text = instruction == null ? "" : instruction.trim();
        if (!text.isEmpty()) {
            parts.add(new TextContent("text", text));
        }
        for (byte[] image : images) {
            String mimeType = ImageMimeType.detect(image);
            String encoded = Base64.getEncoder().encodeToString(image);
            parts.add(new ImageContent("image_url",
                    new ImageUrl("data:" + (mimeType == null ? "image/jpeg" : mimeType) + ";base64," + encoded)));
        }

        ChatCompletionRequest request = new ChatCompletionRequest(
                props.getAiModel(),
                List.of(
                        new Message("system", PromptProvider.systemPrompt()),
                        new Message("user", parts)
                )
        );

        String url = trimTrailingSlash(props.getAiApiUrl()) + "/chat/completions";

        try {
            ChatCompletionResponse response = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + props.getAiApiKey())
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR);
            }
            return response.choices().get(0).message().content();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR);
        }
    }

    private String trimTrailingSlash(String url) {
        String result = url;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
