package com.structengine.platform.id;

import com.structengine.platform.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Snowflake ID 生成器：41 bit 时间戳 + 10 bit workerId + 12 bit sequence。
 *
 * <p>单实例默认 workerId=0；多实例部署时必须为每个实例配置不同的
 * {@code STRUCTENGINE_SNOWFLAKE_WORKER_ID}。ID 在 Java 内部使用 long，
 * 对 API/插件输出时统一转为字符串，避免 JavaScript 64 位精度丢失。</p>
 */
@Component
public class SnowflakeIdGenerator {

    /** 自定义 epoch：2024-01-01T00:00:00Z。 */
    private static final long EPOCH = 1704067200000L;

    private static final long WORKER_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = (1L << WORKER_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long WORKER_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS;
    private static final long MAX_CLOCK_BACKWARD_MS = 5L;

    private final long workerId;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(AppProperties props) {
        long configured = props.getSnowflakeWorkerId();
        if (configured < 0 || configured > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    "snowflake-worker-id 必须在 0~" + MAX_WORKER_ID + " 之间");
        }
        this.workerId = configured;
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset > MAX_CLOCK_BACKWARD_MS) {
                throw new IllegalStateException("系统时钟回拨过大，拒绝生成 Snowflake ID");
            }
            timestamp = waitUntil(lastTimestamp);
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = waitUntil(lastTimestamp + 1);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_SHIFT)
                | sequence;
    }

    /** API/插件协议使用的字符串形式。 */
    public String nextIdString() {
        return Long.toString(nextId());
    }

    public static String asString(long id) {
        return Long.toString(id);
    }

    public static long parse(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ID 不能为空");
        }
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ID 格式非法: " + id, e);
        }
    }

    private long waitUntil(long target) {
        long timestamp = System.currentTimeMillis();
        while (timestamp < target) {
            Thread.onSpinWait();
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
