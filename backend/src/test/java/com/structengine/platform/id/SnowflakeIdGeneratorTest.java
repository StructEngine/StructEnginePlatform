package com.structengine.platform.id;

import com.structengine.platform.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowflakeIdGeneratorTest {

    @Test
    void generatesUniqueIncreasingIds() {
        AppProperties props = new AppProperties();
        props.setSnowflakeWorkerId(3);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(props);

        long previous = 0;
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            long id = generator.nextId();
            assertTrue(id > previous, "ID 必须递增");
            assertTrue(ids.add(id), "ID 不能重复");
            previous = id;
        }
    }

    @Test
    void stringAndParseRoundTrip() {
        AppProperties props = new AppProperties();
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(props);
        long id = generator.nextId();
        assertEquals(id, SnowflakeIdGenerator.parse(Long.toString(id)));
        assertEquals(Long.toString(id), SnowflakeIdGenerator.asString(id));
        assertThrows(IllegalArgumentException.class, () -> SnowflakeIdGenerator.parse("abc"));
        assertThrows(IllegalArgumentException.class, () -> SnowflakeIdGenerator.parse(""));
    }

    @Test
    void rejectsInvalidWorkerId() {
        AppProperties props = new AppProperties();
        props.setSnowflakeWorkerId(1024);
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(props));
    }
}
