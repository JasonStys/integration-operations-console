/*
 * File: DesktopBoundaryTest.java
 * Purpose: Session/Host/Origin/headers and graceful-exit acknowledgement checks with inert mocks.
 * Symbols and fixture state are indexed in docs/code-index.md.
 */
package dev.jasonstys.operations.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** No real service, user profile or process is started by boundary tests. */
class DesktopBoundaryTest {
    private MockHttpServletRequest request(String route) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", route);
        request.setRequestURI(route);
        request.setLocalPort(38123);
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Host", "127.0.0.1:38123");
        return request;
    }

    @Test
    void publicHealthIsCorrelatedButCannotAuthorizeData() throws Exception {
        DesktopState state = mock(DesktopState.class);
        DesktopBoundaryFilter filter = new DesktopBoundaryFilter(state);
        MockHttpServletResponse health = new MockHttpServletResponse();
        filter.doFilter(request("/desktop/health"), health, (input, output) -> output.getWriter().write("ready"));
        assertEquals("ready", health.getContentAsString());
        assertTrue(health.getHeader("Content-Security-Policy").contains("frame-ancestors 'none'"));
        assertEquals("no-referrer", health.getHeader("Referrer-Policy"));
        for (String route : new String[]{"/api/v1/accounts", "/actuator/health", "/desktop/info", "/desktop/quit"}) {
            MockHttpServletResponse denied = new MockHttpServletResponse();
            filter.doFilter(request(route), denied, (input, output) -> output.getWriter().write("must not run"));
            assertEquals(403, denied.getStatus());
            assertFalse(denied.getContentAsString().contains("must not run"));
        }
    }

    @Test
    void correctSessionCannotBypassHostOriginOrCrossSiteChecks() throws Exception {
        DesktopState state = mock(DesktopState.class);
        when(state.authorized("synthetic-session")).thenReturn(true);
        DesktopBoundaryFilter filter = new DesktopBoundaryFilter(state);
        for (String defect : new String[]{"host", "origin", "remote", "fetch-site", "stopping", "valid"}) {
            MockHttpServletRequest request = request("/api/v1/accounts");
            request.addHeader("X-IOC-Session", "synthetic-session");
            request.addHeader("Origin", "http://127.0.0.1:38123");
            if (defect.equals("host")) {
                request.removeHeader("Host");
                request.addHeader("Host", "example.invalid:38123");
            } else if (defect.equals("origin")) {
                request.removeHeader("Origin");
                request.addHeader("Origin", "http://127.0.0.1:9999");
            } else if (defect.equals("remote")) {
                request.setRemoteAddr("192.0.2.1");
            } else if (defect.equals("fetch-site")) {
                request.addHeader("Sec-Fetch-Site", "cross-site");
            } else if (defect.equals("stopping")) {
                request.setMethod("POST");
                when(state.stopping()).thenReturn(true);
            } else {
                when(state.stopping()).thenReturn(false);
            }
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (input, output) -> output.getWriter().write("allowed"));
            assertEquals(defect.equals("valid") ? 200 : defect.equals("stopping") ? 503 : 403,
                    response.getStatus());
        }
    }

    @Test
    void acknowledgedQuitFlushesTheExactBodyBeforeRequestingClose() throws Exception {
        DesktopState state = mock(DesktopState.class);
        DesktopController controller = new DesktopController(state);
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.quit(response);
        assertEquals(202, response.getStatus());
        assertEquals("{\"stopping\":true}", response.getContentAsString());
        assertTrue(response.isCommitted());
        verify(state).requestClose();
    }
}
