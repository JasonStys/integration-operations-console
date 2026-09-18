/*
 * File: OperationsControllerIntegrationTest.java
 * Purpose: HTTP contract tests for role checks, validation, creation, and correlation headers.
 * Symbols: MockMvc request scenarios; exact lines are generated in docs/code-index.md.
 */
package dev.jasonstys.operations;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Boots the real filter/controller/service/store stack without a listening socket. */
@SpringBootTest
@AutoConfigureMockMvc
class OperationsControllerIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void viewerCannotMutateButCanRead() throws Exception {
        mvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectorId":"atlas-ads","displayName":"Viewer","externalReference":"viewer-01"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Operator role required"));

        mvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCounts.QUEUED").isNumber());
    }

    @Test
    void accountCreationReturnsLocationAndCorrelationId() throws Exception {
        String reference = "api-" + UUID.randomUUID();

        mvc.perform(post("/api/v1/accounts")
                        .header("X-Demo-Role", "operator")
                        .header("X-Correlation-Id", "contract-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectorId":"atlas-ads","displayName":"API Test","externalReference":"%s"}
                                """.formatted(reference)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", "contract-test"))
                .andExpect(header().string("Location", matchesPattern("/api/v1/accounts/[0-9a-f-]+")))
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.resource.connectorId").value("atlas-ads"));
    }

    @Test
    void invalidInputReturnsProblemDetails() throws Exception {
        mvc.perform(post("/api/v1/accounts")
                        .header("X-Demo-Role", "operator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectorId":"INVALID CONNECTOR","displayName":"","externalReference":"?"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Request validation failed"));
    }

    @Test
    void invalidQueryParameterReturnsSafeProblemDetails() throws Exception {
        mvc.perform(get("/api/v1/jobs").queryParam("limit", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("Request validation failed"));
    }

    @Test
    void malformedJsonReturnsSafeProblemDetails() throws Exception {
        mvc.perform(post("/api/v1/accounts")
                        .header("X-Demo-Role", "operator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("Request validation failed"));
    }
}
