package com.structengine.platform.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppPropertiesTest {

    @Test
    void defaults() {
        AppProperties props = new AppProperties();
        assertEquals("./data", props.getDataDir());
        assertEquals(10, props.getMaxImageMb());
        assertEquals(8080, props.getServerPort());
        assertEquals(5, props.getGameRpcTimeoutSeconds());
        assertEquals(600, props.getTaskTimeoutSeconds());
        assertEquals("", props.getPublicBaseUrl());
    }

    @Test
    void dataPathIsAbsolute() {
        AppProperties props = new AppProperties();
        props.setDataDir("./data");
        assertTrue(props.dataPath().isAbsolute());
        assertTrue(props.dataPath().endsWith("data"));
    }

    @Test
    void relativeDataDirResolvesAgainstConfiguredProjectRoot() {
        AppProperties props = new AppProperties();
        props.setProjectRoot("/srv/structengine");
        props.setDataDir("./data");
        assertEquals("/srv/structengine/data", props.dataPath().toString());
    }

    @Test
    void absoluteDataDirIgnoresProjectRoot() {
        AppProperties props = new AppProperties();
        props.setProjectRoot("/srv/structengine");
        props.setDataDir("/var/lib/structengine");
        assertEquals("/var/lib/structengine", props.dataPath().toString());
    }

    @Test
    void maxImageBytesConverts() {
        AppProperties props = new AppProperties();
        props.setMaxImageMb(3);
        assertEquals(3L * 1024 * 1024, props.maxImageBytes());
    }
}