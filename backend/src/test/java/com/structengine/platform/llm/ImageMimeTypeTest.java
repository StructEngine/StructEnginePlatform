package com.structengine.platform.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImageMimeTypeTest {

    @Test
    void detectsSupportedFormats() {
        assertEquals("image/jpeg", ImageMimeType.detect(
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}));
        assertEquals("image/png", ImageMimeType.detect(
                new byte[]{(byte) 0x89, 'P', 'N', 'G'}));
        assertEquals("image/gif", ImageMimeType.detect(
                new byte[]{'G', 'I', 'F', '8', '9', 'a'}));
        assertEquals("image/webp", ImageMimeType.detect(
                new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}));
        assertEquals("image/bmp", ImageMimeType.detect(
                new byte[]{'B', 'M', 0, 0}));
    }

    @Test
    void rejectsUnknownOrShortData() {
        assertNull(ImageMimeType.detect(null));
        assertNull(ImageMimeType.detect(new byte[]{(byte) 0xFF}));
        assertNull(ImageMimeType.detect("hello".getBytes()));
    }
}
