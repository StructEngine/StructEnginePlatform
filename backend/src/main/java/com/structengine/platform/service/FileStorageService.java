package com.structengine.platform.service;

import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.config.AppProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 原图与模型文件的落盘/读取统一封装。
 */
@Service
public class FileStorageService {

    private final AppProperties props;

    public FileStorageService(AppProperties props) {
        this.props = props;
    }

    public void saveImage(String taskId, int index, byte[] data) {
        Path path = props.dataPath().resolve("images").resolve(taskId + "_" + index + ".jpg");
        write(path, data);
    }

    public void saveModel(String taskId, String content) {
        Path path = props.dataPath().resolve("models").resolve(taskId + ".json");
        write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private void write(Path path, byte[] data) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, data);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}