package com.vemo.codereview.common.service;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyService {

    private static final Duration DEFAULT_LOCK_TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "code-reviewer:idempotent:";
    private static final long CLEANUP_INTERVAL = 100L;

    private final ConcurrentMap<String, Long> localLocks = new ConcurrentHashMap<String, Long>();
    private final AtomicLong accessCounter = new AtomicLong(0L);

    public boolean tryAcquire(String idempotencyKey) {
        long now = System.currentTimeMillis();
        cleanupIfNeeded(now);

        String cacheKey = KEY_PREFIX + idempotencyKey;
        long expiresAt = now + DEFAULT_LOCK_TTL.toMillis();

        while (true) {
            Long existing = localLocks.putIfAbsent(cacheKey, expiresAt);
            if (existing == null) {
                return true;
            }
            if (existing.longValue() <= now) {
                if (localLocks.replace(cacheKey, existing, expiresAt)) {
                    return true;
                }
                continue;
            }
            return false;
        }
    }

    private void cleanupIfNeeded(long now) {
        if (accessCounter.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }
        Iterator<Map.Entry<String, Long>> iterator = localLocks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            Long expiresAt = entry.getValue();
            if (expiresAt != null && expiresAt.longValue() <= now) {
                iterator.remove();
            }
        }
    }
}
