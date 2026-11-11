/*
 * File: DesktopBoundaryFilter.java
 * Purpose: Native-only Host/Origin/session boundary and no-third-party browser policy.
 * State: a per-launch DesktopState; exact declarations are in docs/code-index.md.
 */
package dev.jasonstys.operations.desktop;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Bind native API requests to the actual listener and an unpredictable per-launch session. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "ioc.desktop.enabled", havingValue = "true")
public class DesktopBoundaryFilter extends OncePerRequestFilter {
    private final DesktopState state;

    /** @param state per-launch session, never a shared demo credential */
    public DesktopBoundaryFilter(DesktopState state) {
        this.state = state;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String host = "127.0.0.1:" + request.getLocalPort();
        String origin = request.getHeader("Origin");
        String route = request.getRequestURI();
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; "
                + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; "
                + "object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
        response.setHeader("Cache-Control", "no-store");
        if (!host.equals(request.getHeader("Host"))
                || !("127.0.0.1".equals(request.getRemoteAddr()) || "0:0:0:0:0:0:0:1".equals(request.getRemoteAddr()))
                || (origin != null && !("http://" + host).equals(origin))
                || "cross-site".equals(request.getHeader("Sec-Fetch-Site"))) {
            deny(response, 403);
            return;
        }
        boolean protectedRoute = route.startsWith("/api/") || route.startsWith("/actuator")
                || (route.startsWith("/desktop/") && !route.equals("/desktop/health"));
        if (protectedRoute && !state.authorized(request.getHeader("X-IOC-Session"))) {
            deny(response, 403);
            return;
        }
        if (state.stopping() && !"GET".equals(request.getMethod())) {
            deny(response, 503);
            return;
        }
        chain.doFilter(request, response);
    }

    private static void deny(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"title\":\"Local application session unavailable\",\"status\":"
                + status + "}");
    }
}
