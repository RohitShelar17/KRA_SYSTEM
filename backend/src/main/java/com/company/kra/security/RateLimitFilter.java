package com.company.kra.security;

import com.company.kra.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-IP throttling on the endpoints an attacker can reach without a token.
 *
 * <p>The account lockout added in Phase 1 protects one account from repeated
 * guesses; this protects the <em>service</em> from someone spraying one guess
 * across many accounts, which no per-account counter would ever notice.</p>
 *
 * <p>Authenticated traffic is left alone: it is already attributable, already
 * scope-limited, and rate-limiting it would mean throttling a manager
 * approving forty timesheets on a Monday morning.</p>
 */
@Slf4j
// Before the security chain as well: a request that is over its budget should
// not cost a token parse or a database read to find that out.
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PREFIX = "/api/v1/auth/";

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int loginLimit;
    private final int authLimit;

    public RateLimitFilter(RateLimiter rateLimiter,
                           ObjectMapper objectMapper,
                           @Value("${app.security.rate-limit.enabled:true}") boolean enabled,
                           @Value("${app.security.rate-limit.login-per-minute:10}") int loginLimit,
                           @Value("${app.security.rate-limit.auth-per-minute:40}") int authLimit) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.loginLimit = loginLimit;
        this.authLimit = authLimit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getRequestURI().startsWith(AUTH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        // Sign-in and password reset are the guessable ones, so they get the
        // tighter budget; token refresh is a legitimate background call.
        boolean sensitive = path.endsWith("/login") || path.endsWith("/forgot-password")
                || path.endsWith("/reset-password");
        int limit = sensitive ? loginLimit : authLimit;
        String key = (sensitive ? "auth-sensitive:" : "auth:") + clientIp(request);

        if (!rateLimiter.allow(key, limit)) {
            log.warn("Rate limit hit for {} on {}", key, path);
            reject(response, limit);
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int limit) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, "60");

        // The same envelope every other error uses, so the frontend reacts to
        // the code rather than parsing prose.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("error", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        body.put("code", ErrorCode.RATE_LIMITED.name());
        body.put("message", "Too many attempts from this address. Wait a minute and try again.");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    /** Honours a proxy header when present, because otherwise every caller is the proxy. */
    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
