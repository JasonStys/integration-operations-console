/*
 * File: DesktopController.java
 * Purpose: Native-only public readiness and session-protected help/lifecycle metadata.
 * State: one DesktopState; exact declarations and variables: docs/code-index.md.
 */
package dev.jasonstys.operations.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

/** No private paths, datasource credentials or session token are returned. */
@RestController
@ConditionalOnProperty(name = "ioc.desktop.enabled", havingValue = "true")
public class DesktopController {
    private final DesktopState state;

    /** @param state native ownership and graceful close coordinator */
    public DesktopController(DesktopState state) {
        this.state = state;
    }

    /** @return nonsecret readiness correlation; this does not authorize any API action */
    @GetMapping("/desktop/health")
    public Map<String, Object> health() {
        return state.health();
    }

    /** @return safe workspace scope/help information for this active browser session */
    @GetMapping("/desktop/info")
    public Map<String, Object> info() {
        return Map.of("version", DesktopState.VERSION, "persistence", state.persistence(),
                "author", "Jason Stys", "mode", "synthetic-local-demonstration");
    }

    /** Flush actual acknowledgement before requesting graceful service/database shutdown. */
    @PostMapping("/desktop/quit")
    public void quit(HttpServletResponse response) throws IOException {
        byte[] body = "{\"stopping\":true}".getBytes(StandardCharsets.UTF_8);
        response.setStatus(202);
        response.setContentType("application/json");
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
        response.flushBuffer();
        state.requestClose();
    }
}
