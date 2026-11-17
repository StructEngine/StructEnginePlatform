package com.structengine.platform.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单实例登录/注册限流。多实例部署时需替换为 Redis。
 */
@Component
public class LoginRateLimiter {

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public boolean allow(String key, int maxAttempts, long windowMs) {
        long now = System.currentTimeMillis();
        Deque<Long> queue = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst() <= now - windowMs) {
                queue.pollFirst();
            }
            if (queue.size() >= maxAttempts) {
                return false;
            }
            queue.addLast(now);
            return true;
        }
    }

    @Scheduled(fixedDelay = 300_000)
    public void cleanup() {
        long cutoff = System.currentTimeMillis() - 3_600_000L;
        attempts.entrySet().removeIf(entry -> {
            Deque<Long> queue = entry.getValue();
            synchronized (queue) {
                return queue.isEmpty() || queue.peekLast() < cutoff;
            }
        });
    }
}
