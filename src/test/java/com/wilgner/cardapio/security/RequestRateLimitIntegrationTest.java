package com.wilgner.cardapio.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.rate-limit.login=5", "app.rate-limit.refresh=2",
        "app.rate-limit.setup=2", "app.rate-limit.public=2"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestRateLimitIntegrationTest {
    @Autowired private MockMvc mvc;

    @Test
    void loginLimitCannotBeBypassedByForwardedHeadersOrChangingUsername() throws Exception {
        for (int i = 0; i < 6; i++) {
            mvc.perform(post("/auth/login").with(r -> { r.setRemoteAddr("192.0.2.1"); return r; })
                            .header("X-Forwarded-For", "198.51.100." + i)
                            .header("Forwarded", "for=198.51.100." + i)
                            .contentType("application/json")
                            .content("{\"username\":\"missing" + i + "\",\"password\":\"test-password\"}"))
                    .andExpect(status().is(i < 5 ? 401 : 429));
        }
        mvc.perform(post("/auth/login").with(r -> { r.setRemoteAddr("192.0.2.1"); return r; })
                        .contentType("application/json").content("{}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
        mvc.perform(post("/auth/login").with(r -> { r.setRemoteAddr("192.0.2.2"); return r; })
                        .contentType("application/json")
                        .content("{\"username\":\"missing\",\"password\":\"test-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshSetupAndPublicRoutesHaveIndependentLimits() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/auth/refresh").with(r -> { r.setRemoteAddr("192.0.2.3"); return r; }))
                    .andExpect(status().is(i < 2 ? 401 : 429));
            mvc.perform(post("/setup/admin").with(r -> { r.setRemoteAddr("192.0.2.3"); return r; }))
                    .andExpect(status().is(i < 2 ? 403 : 429));
            mvc.perform(get("/public/restaurante/cardapio").with(r -> { r.setRemoteAddr("192.0.2.3"); return r; }))
                    .andExpect(status().is(i < 2 ? 200 : 429));
        }
        // Administrative traffic and health probes remain outside these public budgets.
        mvc.perform(get("/actuator/health").with(r -> { r.setRemoteAddr("192.0.2.3"); return r; }))
                .andExpect(status().isOk());
        mvc.perform(get("/painel/produtos").with(r -> { r.setRemoteAddr("192.0.2.3"); return r; }))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledSetupIsUnavailableEvenWithCsrf() throws Exception {
        mvc.perform(post("/setup/admin").with(csrf()).contentType("application/json").content("{}")
                        .with(r -> { r.setRemoteAddr("192.0.2.4"); return r; }))
                .andExpect(status().isNotFound());
    }

    @Test
    void encodedLoginPathSharesTheSameBudget() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post(java.net.URI.create("/auth/log%69n"))
                            .with(r -> { r.setRemoteAddr("192.0.2.5"); return r; })
                            .contentType("application/json")
                            .content("{\"username\":\"missing\",\"password\":\"test-password\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/auth/login").with(r -> { r.setRemoteAddr("192.0.2.5"); return r; })
                        .contentType("application/json").content("{}"))
                .andExpect(status().isTooManyRequests());
    }
}
