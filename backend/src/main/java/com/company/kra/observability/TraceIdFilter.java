package com.company.kra.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gives every request an id that appears in its logs, its audit rows and its
 * error envelope.
 *
 * <p>Without it, "something failed at about half past three" is a search
 * through interleaved lines from every other request in flight. An id supplied
 * by an upstream proxy is honoured so a trace survives the hop.</p>
 */
// Ahead of Spring Security's chain (order -100): a 401 or a 429 is exactly
// the response somebody reports, so it has to carry the id too.
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    private static final String HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String traceId = StringUtils.hasText(incoming)
                ? incoming.replaceAll("[^A-Za-z0-9-]", "").substring(0, Math.min(incoming.length(), 36))
                : UUID.randomUUID().toString().substring(0, 8);

        MDC.put(TRACE_ID, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Threads are pooled: leaving the id behind would stamp it on
            // whatever request runs next.
            MDC.remove(TRACE_ID);
        }
    }

    /** The current request's id, when there is one. */
    public static String current() {
        return MDC.get(TRACE_ID);
    }
}
