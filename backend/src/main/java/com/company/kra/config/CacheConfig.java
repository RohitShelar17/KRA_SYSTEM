package com.company.kra.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * In-process caches for the handful of reads that happen on nearly every
 * request and change a few times a year.
 *
 * <p>Each cache has a short TTL as well as explicit eviction, so a stale entry
 * is a matter of seconds even if an eviction is ever missed. Nothing
 * employee-scoped is cached: a cache keyed by the wrong thing is how one
 * person ends up reading another's data, and none of these reads is expensive
 * enough to be worth that risk.</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String COMPANY_SETTINGS = "companySettings";
    public static final String RATING_BANDS = "ratingBands";
    public static final String FORMULA_CONFIG = "formulaConfig";
    public static final String NOTIFICATION_TEMPLATES = "notificationTemplates";
    public static final String WORKING_CALENDAR = "workingCalendar";
    public static final String USER_PRINCIPALS = "userPrincipals";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                COMPANY_SETTINGS, RATING_BANDS, FORMULA_CONFIG, NOTIFICATION_TEMPLATES,
                WORKING_CALENDAR);

        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(15))
                .maximumSize(5_000)
                .recordStats());

        // The signed-in principal is rebuilt from four tables on every single
        // request, which makes it the one worth caching — and it is evicted
        // explicitly wherever roles, permissions, status or password change.
        // Two minutes is the safety net for a path somebody forgets to evict;
        // it is well inside the fifteen-minute life of the access token that
        // carried the request here, so it widens no window that was not
        // already open.
        manager.registerCustomCache(USER_PRINCIPALS, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(2))
                .maximumSize(20_000)
                .recordStats()
                .build());

        // Null is a legitimate answer for "the band covering this score" and
        // caching it would keep a lookup from being repeated pointlessly, but
        // Spring's default already declines to cache nulls, and a cached null
        // after a band is added would read as "no band" until the TTL. Left off.
        manager.setAllowNullValues(false);
        return manager;
    }
}
