package com.structengine.platform.service;

import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.config.AppProperties;
import com.structengine.platform.llm.JsonSanitizer;
import com.structengine.platform.llm.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerateServiceTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};

    @TempDir
    Path tempDir;

    private AppProperties props;
    private LlmClient llmClient;
    private FileStorageService fileStorage;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        props.setDataDir(tempDir.toString());
        props.setMaxImageMb(10);
        llmClient = mock(LlmClient.class);
        fileStorage = mock(FileStorageService.class);
    }

    private GenerateService service() {
        return new GenerateService(props, llmClient, new JsonSanitizer(), fileStorage);
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("images", "a.jpg", "image/jpeg", JPEG);
    }

    @Test
    void generateSuccess() {
        when(llmClient.generate(any(byte[][].class), anyString()))
                .thenReturn("[[\"minecraft:stone\",0,0,0]]");
        String jsonUrl = service().generate(new MultipartFile[]{image()}, null, "example.com");
        assertTrue(jsonUrl.startsWith("http://example.com/models/"));
        assertTrue(jsonUrl.endsWith(".json"));
    }

    @Test
    void acceptsSingleImageAndAsksForCompletion() {
        when(llmClient.generate(any(byte[][].class), anyString()))
                .thenReturn("[[\"minecraft:stone\",0,0,0]]");

        service().generate(new MultipartFile[]{image()}, "一座带红色屋顶的小木屋", "h");

        ArgumentCaptor<String> instruction = ArgumentCaptor.forClass(String.class);
        verify(llmClient).generate(any(byte[][].class), instruction.capture());
        assertTrue(instruction.getValue().contains("参考图数量少于 3 张"));
        assertTrue(instruction.getValue().contains("推断缺失方向"));
        assertTrue(instruction.getValue().contains("一座带红色屋顶的小木屋"));
    }

    @Test
    void acceptsTwoImages() {
        when(llmClient.generate(any(byte[][].class), anyString()))
                .thenReturn("[[\"minecraft:stone\",0,0,0]]");

        String jsonUrl = service().generate(new MultipartFile[]{image(), image()}, "", "example.com");

        assertTrue(jsonUrl.endsWith(".json"));
    }

    @Test
    void acceptsThreeImagesAndUsesIntersectionInstruction() {
        when(llmClient.generate(any(byte[][].class), anyString()))
                .thenReturn("[[\"minecraft:stone\",0,0,0]]");

        service().generate(new MultipartFile[]{image(), image(), image()}, null, "h");

        ArgumentCaptor<String> instruction = ArgumentCaptor.forClass(String.class);
        verify(llmClient).generate(any(byte[][].class), instruction.capture());
        assertTrue(instruction.getValue().contains("参考图覆盖三视图"));
    }

    @Test
    void rejectsEmptyImageList() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service().generate(new MultipartFile[]{}, null, "h"));
        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());
    }

    @Test
    void rejectsNullImageList() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service().generate(null, null, "h"));
        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());
    }

    @Test
    void rejectsMoreThanThreeImages() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service().generate(
                        new MultipartFile[]{image(), image(), image(), image()}, null, "h"));
        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());
    }

    @Test
    void rejectsTooLongDescription() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service().generate(new MultipartFile[]{image()}, "a".repeat(1001), "h"));
        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());
    }

    @Test
    void rejectsNonImage() {
        MockMultipartFile txt = new MockMultipartFile("images", "a.txt", "text/plain", "hello".getBytes());
        BusinessException e = assertThrows(BusinessException.class,
                () -> service().generate(new MultipartFile[]{txt}, null, "h"));
        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());
    }

    @Test
    void rejectsTooLarge() {
        props.setMaxImageMb(1);
        byte[] big = new byte[2 * 1024 * 1024];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        big[2] = (byte) 0xFF;
        MockMultipartFile file = new MockMultipartFile("images", "big.jpg", "image/jpeg", big);
        BusinessException e = assertThrows(BusinessException.class,
                () -> service().generate(new MultipartFile[]{file}, null, "h"));
        assertEquals(ErrorCode.TOO_LARGE, e.errorCode());
    }

    @Test
    void mapsAiFailureToUpstreamError() {
        when(llmClient.generate(any(byte[][].class), anyString()))
                .thenThrow(new BusinessException(ErrorCode.UPSTREAM_ERROR));
        BusinessException e = assertThrows(BusinessException.class,
                () -> service().generate(new MultipartFile[]{image()}, null, "h"));
        assertEquals(ErrorCode.UPSTREAM_ERROR, e.errorCode());
    }

    @Test
    void mapsParseFailureToUnprocessable() {
        when(llmClient.generate(any(byte[][].class), anyString())).thenReturn("garbage");
        BusinessException e = assertThrows(BusinessException.class,
                () -> service().generate(new MultipartFile[]{image()}, null, "h"));
        assertEquals(ErrorCode.UNPROCESSABLE, e.errorCode());
    }

    @Test
    void rejectsModelExceedingBlockLimit() {
        props.setMaxModelBlocks(1);
        when(llmClient.generate(any(byte[][].class), anyString()))
                .thenReturn("[[\"minecraft:stone\",0,0,0],[\"minecraft:stone\",1,0,0]]");
        BusinessException e = assertThrows(BusinessException.class,
                () -> service().generate(new MultipartFile[]{image()}, null, "h"));
        assertEquals(ErrorCode.UNPROCESSABLE, e.errorCode());
    }
}
