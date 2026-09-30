package com.company.kra.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fixed-window counter per key.
 *
 * <p>Deliberately in-process. A single instance is what this deployment runs,
 * and an in-memory limiter that works beats a distributed one that is
 * configured but unreachable. Behind more than one instance the window becomes
 * per-instance — that is a real limitation, and the fix is a shared store
 * rather than a bigger map.</p>
 *
 * <p>Entries expire on their own, so a burst of one-off addresses cannot grow
 * the store without bound.</p>
 */
@Component
public class RateLimiter {

    private final Cache<String, AtomicInteger> windows;

    public RateLimiter() {
        this(Duration.ofMinutes(1));
    }

    RateLimiter(Duration window) {
        this.windows = Caffeine.newBuilder()
                .expireAfterWrite(window)
                .maximumSize(100_000)
                .build();
    }

    /**
     * Counts one request against a key.
     *
     * @return true when the caller is still inside its allowance
     */
    public boolean allow(String key, int limit) {
        AtomicInteger counter = windows.get(key, ignored -> new AtomicInteger());
        return counter.incrementAndGet() <= limit;
    }

    /** How many requests this key has made in the current window. */
    public int used(String key) {
        AtomicInteger counter = windows.getIfPresent(key);
        return counter == null ? 0 : counter.get();
    }

    /** Clears a key — used after a successful sign-in, so one typo costs nothing. */
    public void reset(String key) {
        windows.invalidate(key);
    }
}
