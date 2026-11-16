package com.structengine.platform.service;

import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.config.AppProperties;
import com.structengine.platform.domain.ModelData;
import com.structengine.platform.domain.Task;
import com.structengine.platform.domain.VoxelBlock;
import com.structengine.platform.llm.ImageMimeType;
import com.structengine.platform.llm.JsonSanitizer;
import com.structengine.platform.llm.LlmClient;
import com.structengine.platform.llm.PromptProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 参考图转换主流程编排：校验 → 落盘原图 → 多模态推理 → 清洗 → 校验 → 落盘模型 → 返回 json_url。
 *
 * <p>三视图只是推荐输入，1~3 张参考图均可提交；不足三视图时由多模态模型推断补全。</p>
 */
@Service
public class GenerateService {

    public static final int MAX_IMAGES = 3;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;

    private final AppProperties props;
    private final LlmClient llmClient;
    private final JsonSanitizer sanitizer;
    private final FileStorageService fileStorage;

    public GenerateService(AppProperties props, LlmClient llmClient,
                           JsonSanitizer sanitizer, FileStorageService fileStorage) {
        this.props = props;
        this.llmClient = llmClient;
        this.sanitizer = sanitizer;
        this.fileStorage = fileStorage;
    }

    public String generate(MultipartFile[] images, String description, String host) {
        Task task = new Task();
        validateImageCount(images);
        String extraDescription = normalizeDescription(description);

        List<byte[]> imageBytes = new ArrayList<>(images.length);
        for (MultipartFile image : images) {
            byte[] data;
            try {
                data = image.getBytes();
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "读取上传文件失败");
            }
            if (ImageMimeType.detect(data) == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "文件类型不合法：仅支持图片");
            }
            if (image.getSize() > props.maxImageBytes()) {
                throw new BusinessException(ErrorCode.TOO_LARGE);
            }
            imageBytes.add(data);
        }

        for (int i = 0; i < imageBytes.size(); i++) {
            fileStorage.saveImage(task.id(), i, imageBytes.get(i));
        }

        String instruction = PromptProvider.userInstruction(
                imageBytes.size(), extraDescription, props.getMaxModelBlocks());
        String raw = llmClient.generate(imageBytes.toArray(new byte[0][]), instruction);

        List<VoxelBlock> blocks = sanitizer.sanitize(raw);
        if (blocks.size() > props.getMaxModelBlocks()) {
            throw new BusinessException(ErrorCode.UNPROCESSABLE,
                    "模型方块数超过限制 " + props.getMaxModelBlocks());
        }

        ModelData model = new ModelData(blocks);
        try {
            model.validate();
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.UNPROCESSABLE);
        }

        String payload = model.serialize();
        fileStorage.saveModel(task.id(), payload);

        return "http://" + host + "/models/" + task.modelPath();
    }

    private void validateImageCount(MultipartFile[] images) {
        if (images == null || images.length == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "请至少上传 1 张参考图（三视图只是建议，1~3 张均可）");
        }
        if (images.length > MAX_IMAGES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "最多支持 " + MAX_IMAGES + " 张参考图");
        }
    }

    private String normalizeDescription(String description) {
        String value = description == null ? "" : description.trim();
        if (value.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "补充说明不能超过 " + MAX_DESCRIPTION_LENGTH + " 字");
        }
        return value;
    }
}
