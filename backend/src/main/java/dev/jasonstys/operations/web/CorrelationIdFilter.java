/*
 * File: CorrelationIdFilter.java
 * Purpose: Validates or creates request correlation IDs and returns them to callers.
 * Symbols: ATTRIBUTE/HEADER constants and doFilterInternal(); exact lines are in docs/code-index.md.
 * Variables: accepted IDs are limited to 80 safe characters to prevent log/header injection.
 */
package dev.jasonstys.operations.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Request filter providing trace-friendly, injection-safe identifiers. */
@Component
public final class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = CorrelationIdFilter.class.getName() + ".correlationId";
    public static final String HEADER = "X-Correlation-Id";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,80}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String correlationId = supplied != null && SAFE_ID.matcher(supplied).matches()
                ? supplied : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, correlationId);
        response.setHeader(HEADER, correlationId);
        MDC.put("correlationId", correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
