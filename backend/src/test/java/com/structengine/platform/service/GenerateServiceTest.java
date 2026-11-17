package com.structengine.platform.service;

import com.structengine.platform.ai.VisionAiService;
import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.config.AppProperties;
import com.structengine.platform.id.SnowflakeIdGenerator;
import com.structengine.platform.llm.JsonSanitizer;
import com.structengine.platform.model.ModelArtifact;
import com.structengine.platform.model.ModelArtifactService;
import com.structengine.platform.model.ModelDraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerateServiceTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};

    private AppProperties props;
    private VisionAiService vision;
    private FileStorageService fileStorage;
    private ModelArtifactService modelService;
    private SnowflakeIdGenerator idGenerator;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        props.setMaxImageMb(10);
        vision = mock(VisionAiService.class);
        fileStorage = mock(FileStorageService.class);
        modelService = mock(ModelArtifactService.class);
        idGenerator = mock(SnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(100L);
        when(modelService.create(anyLong(), anyLong(), anyLong(), anyString(), any(),
                anyString(), anyList())).thenReturn(model(100L));
    }

    private GenerateService service() {
        return new GenerateService(props, vision, new JsonSanitizer(), fileStorage,
                modelService, idGenerator);
    }

    private static ModelArtifact model(long id) {
        return new ModelArtifact(id, 1L, 2L, "REFERENCE_IMAGE", "des", "http://x/models/100.json",
                1, 0, 0, 0, 0, 0, 0, 1L, null);
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("images", "a.jpg", "image/jpeg", JPEG);
    }

    @Test
    void generateSuccess() {
        when(vision.generate(any(byte[][].class), anyString()))
                .thenReturn("[[\"minecraft:stone\",0,0,0]]");

        ModelDraft draft = service().generate(1L, 2L,
                new MultipartFile[]{image()}, null, "http://example.com");

        assertEquals("100", Long.toString(draft.artifact().id()));
        assertTrue(draft.artifact().modelUrl().endsWith("/models/100.json"));
        assertEquals(1, draft.blocks().size());
    }

    @Test
    void acceptsSingleImageAndAsksForCompletion() {
        when(vision.generate(any(byte[][].class), anyString()))
                .thenReturn("[[\"minecraft:stone\",0,0,0]]");

        service().generate(1L, 2L, new MultipartFile[]{image()},
                "一座带红色屋顶的小木屋", "http://h");

        ArgumentCaptor<String> instruction = ArgumentCaptor.forClass(String.class);
        verify(vision).generate(any(byte[][].class), instruction.capture());
        assertTrue(instruction.getValue().contains("参考图数量少于 3 张"));
        assertTrue(instruction.getValue().contains("推断缺失方向"));
        assertTrue(instruction.getValue().contains("一座带红色屋顶的小木屋"));
    }

    @Test
    void acceptsTwoImages() {
        when(vision.generate(any(byte[][].class), anyString()))
                .thenReturn("[[\"minecraft:stone\",0,0,0]]");
        ModelDraft draft = service().generate(1L, 2L,
                new MultipartFile[]{image(), image()}, "", "http://example.com");
        assertTrue(draft.artifact().modelUrl().endsWith(".json"));
    }

    @Test
    void acceptsThreeImagesAndUsesIntersectionInstruction() {
        when(vision.generate(any(byte[][].class), anyString()))
                .thenReturn("[[\"minecraft:stone\",0,0,0]]");

        service().generate(1L, 2L,
                new MultipartFile[]{image(), image(), image()}, null, "http://h");

        ArgumentCaptor<String> instruction = ArgumentCaptor.forClass(String.class);
        verify(vision).generate(any(byte[][].class), instruction.capture());
        assertTrue(instruction.getValue().contains("参考图覆盖三视图"));
    }

    @Test
    void rejectsEmptyImageList() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service().generate(1L, 2L, new MultipartFile[]{}, null, "http://h"));
        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());
    }

    @Test
    void rejectsNullImageList() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service().generate(1L, 2L, null, null, "http://h"));
        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());
    }

    @Test
    void rejectsMoreThanThreeImages() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service().generate(1L, 2L,
                        new MultipartFile[]{image(), image(), image(), image()}, null, "http://h"));
        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());
    }

    @Test
    void rejectsTooLongDescription() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service().generate(1L, 2L, new MultipartFile[]{image()},
                        "a".repeat(1001), "http://h"));
        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());
    }

    @Test
    void rejectsNonImage() {
        MockMultipartFile txt = new MockMultipartFile("images", "a.txt", "text/plain", "hello".getBytes());
        BusinessException e = assertThrows(BusinessException.class,
                () -> service().generate(1L, 2L, new MultipartFile[]{txt}, null, "http://h"));
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
                () -> service().generate(1L, 2L, new MultipartFile[]{file}, null, "http://h"));
        assertEquals(ErrorCode.TOO_LARGE, e.errorCode());
    }

    @Test
    void mapsAiFailureToUpstreamError() {
        when(vision.generate(any(byte[][].class), anyString()))
                .thenThrow(new BusinessException(ErrorCode.UPSTREAM_ERROR));
        BusinessException e = assertThrows(BusinessException.class,
                () -> service().generate(1L, 2L, new MultipartFile[]{image()}, null, "http://h"));
        assertEquals(ErrorCode.UPSTREAM_ERROR, e.errorCode());
    }

    @Test
    void mapsParseFailureToUnprocessable() {
        when(vision.generate(any(byte[][].class), anyString())).thenReturn("garbage");
        BusinessException e = assertThrows(BusinessException.class,
                () -> service().generate(1L, 2L, new MultipartFile[]{image()}, null, "http://h"));
        assertEquals(ErrorCode.UNPROCESSABLE, e.errorCode());
    }

    @Test
    void rejectsModelExceedingBlockLimit() {
        props.setMaxModelBlocks(1);
        when(vision.generate(any(byte[][].class), anyString()))
                .thenReturn("[[\"minecraft:stone\",0,0,0],[\"minecraft:stone\",1,0,0]]");
        BusinessException e = assertThrows(BusinessException.class,
                () -> service().generate(1L, 2L, new MultipartFile[]{image()}, null, "http://h"));
        assertEquals(ErrorCode.UNPROCESSABLE, e.errorCode());
    }
}
