package com.company.kra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The throttle in front of the endpoints an attacker can reach without a
 * token. The counter itself and the filter's choice of budget are both worth
 * pinning down: too tight and a shared office IP locks itself out.
 */
class RateLimitTest {

    @Nested
    @DisplayName("The counter")
    class Counter {

        private final RateLimiter limiter = new RateLimiter(Duration.ofMinutes(1));

        @Test
        @DisplayName("Allows up to the limit and refuses the one after it")
        void allowsUpToTheLimit() {
            for (int attempt = 1; attempt <= 3; attempt++) {
                assertThat(limiter.allow("ip:1.2.3.4", 3))
                        .as("attempt %d", attempt)
                        .isTrue();
            }
            assertThat(limiter.allow("ip:1.2.3.4", 3)).isFalse();
        }

        @Test
        @DisplayName("Counts each key separately, so one address cannot block another")
        void keysAreIndependent() {
            limiter.allow("ip:1.1.1.1", 1);
            assertThat(limiter.allow("ip:1.1.1.1", 1)).isFalse();
            assertThat(limiter.allow("ip:2.2.2.2", 1)).isTrue();
        }

        @Test
        @DisplayName("A reset clears the count, so a successful sign-in costs nothing")
        void resetClearsTheWindow() {
            limiter.allow("ip:1.1.1.1", 1);
            assertThat(limiter.used("ip:1.1.1.1")).isEqualTo(1);

            limiter.reset("ip:1.1.1.1");

            assertThat(limiter.used("ip:1.1.1.1")).isZero();
            assertThat(limiter.allow("ip:1.1.1.1", 1)).isTrue();
        }

        @Test
        @DisplayName("An expired window starts again from zero")
        void windowExpires() throws InterruptedException {
            RateLimiter shortWindow = new RateLimiter(Duration.ofMillis(60));
            assertThat(shortWindow.allow("ip:9.9.9.9", 1)).isTrue();
            assertThat(shortWindow.allow("ip:9.9.9.9", 1)).isFalse();

            Thread.sleep(120);

            assertThat(shortWindow.allow("ip:9.9.9.9", 1)).isTrue();
        }
    }

    @Nested
    @DisplayName("The filter")
    class Filter {

        private final RateLimiter limiter = new RateLimiter(Duration.ofMinutes(1));
        private final RateLimitFilter filter =
                new RateLimitFilter(limiter, new ObjectMapper(), true, 2, 5);

        @Test
        @DisplayName("Sign-in gets the tighter budget and is refused with 429 past it")
        void loginIsThrottled() throws Exception {
            FilterChain chain = mock(FilterChain.class);

            MockHttpServletResponse first = call("/api/v1/auth/login", "10.0.0.1", chain);
            MockHttpServletResponse second = call("/api/v1/auth/login", "10.0.0.1", chain);
            MockHttpServletResponse third = call("/api/v1/auth/login", "10.0.0.1", chain);

            assertThat(first.getStatus()).isEqualTo(200);
            assertThat(second.getStatus()).isEqualTo(200);
            assertThat(third.getStatus()).isEqualTo(429);
            assertThat(third.getContentAsString()).contains("RATE_LIMITED");
            assertThat(third.getHeader("Retry-After")).isEqualTo("60");

            // The request that was refused never reached the application.
            verify(chain, times(2)).doFilter(any(), any());
        }

        @Test
        @DisplayName("Refresh has its own, looser budget: it is a background call")
        void refreshHasItsOwnBudget() throws Exception {
            FilterChain chain = mock(FilterChain.class);

            for (int attempt = 0; attempt < 5; attempt++) {
                assertThat(call("/api/v1/auth/refresh", "10.0.0.2", chain).getStatus()).isEqualTo(200);
            }
            assertThat(call("/api/v1/auth/refresh", "10.0.0.2", chain).getStatus()).isEqualTo(429);
        }

        @Test
        @DisplayName("Two addresses do not share a budget")
        void addressesAreIndependent() throws Exception {
            FilterChain chain = mock(FilterChain.class);

            call("/api/v1/auth/login", "10.0.0.3", chain);
            call("/api/v1/auth/login", "10.0.0.3", chain);
            assertThat(call("/api/v1/auth/login", "10.0.0.3", chain).getStatus()).isEqualTo(429);

            assertThat(call("/api/v1/auth/login", "10.0.0.4", chain).getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("A proxied request is counted against the caller, not the proxy")
        void honoursForwardedFor() throws Exception {
            FilterChain chain = mock(FilterChain.class);

            MockHttpServletRequest request = request("/api/v1/auth/login", "172.16.0.1");
            request.addHeader("X-Forwarded-For", "203.0.113.9, 172.16.0.1");
            filter.doFilter(request, new MockHttpServletResponse(), chain);

            assertThat(limiter.used("auth-sensitive:203.0.113.9")).isEqualTo(1);
            assertThat(limiter.used("auth-sensitive:172.16.0.1")).isZero();
        }

        @Test
        @DisplayName("Authenticated traffic is not filtered at all")
        void skipsEverythingElse() throws Exception {
            FilterChain chain = mock(FilterChain.class);

            for (int attempt = 0; attempt < 20; attempt++) {
                filter.doFilter(request("/api/v1/worklogs", "10.0.0.5"),
                        new MockHttpServletResponse(), chain);
            }
            verify(chain, times(20)).doFilter(any(), any());
        }

        @Test
        @DisplayName("With the limiter switched off nothing is counted")
        void canBeSwitchedOff() throws Exception {
            RateLimiter quiet = new RateLimiter(Duration.ofMinutes(1));
            RateLimitFilter disabled = new RateLimitFilter(quiet, new ObjectMapper(), false, 1, 1);
            FilterChain chain = mock(FilterChain.class);

            disabled.doFilter(request("/api/v1/auth/login", "10.0.0.6"),
                    new MockHttpServletResponse(), chain);
            disabled.doFilter(request("/api/v1/auth/login", "10.0.0.6"),
                    new MockHttpServletResponse(), chain);

            verify(chain, times(2)).doFilter(any(), any());
            assertThat(quiet.used("auth-sensitive:10.0.0.6")).isZero();
        }

        @Test
        @DisplayName("A refused request is never passed down the chain")
        void refusedRequestStopsThere() throws Exception {
            FilterChain chain = mock(FilterChain.class);
            RateLimitFilter tight = new RateLimitFilter(new RateLimiter(Duration.ofMinutes(1)),
                    new ObjectMapper(), true, 0, 0);

            MockHttpServletResponse response = new MockHttpServletResponse();
            tight.doFilter(request("/api/v1/auth/login", "10.0.0.7"), response, chain);

            assertThat(response.getStatus()).isEqualTo(429);
            verify(chain, never()).doFilter(any(), any());
        }

        private MockHttpServletResponse call(String path, String ip, FilterChain chain)
                throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request(path, ip), response, chain);
            return response;
        }

        private MockHttpServletRequest request(String path, String ip) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            request.setRequestURI(path);
            request.setRemoteAddr(ip);
            return request;
        }
    }
}
