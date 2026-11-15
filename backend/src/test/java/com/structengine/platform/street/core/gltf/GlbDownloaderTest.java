package com.structengine.platform.street.core.gltf;

import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.street.config.StreetProperties;
import com.structengine.platform.street.model.StreetTask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class GlbDownloaderTest {

    @Test
    void missingApiKeyFailsOnlyWhenStreetIsUsed() {
        StreetProperties props = new StreetProperties();
        GlbDownloader downloader = new GlbDownloader(props);

        BusinessException error = assertThrows(BusinessException.class,
                () -> downloader.download(mock(StreetTask.class)));

        assertEquals(ErrorCode.UPSTREAM_ERROR, error.errorCode());
        assertEquals("街景功能未配置 STRUCTENGINE_GOOGLE_STREET_API_KEY", error.message());
    }
}
