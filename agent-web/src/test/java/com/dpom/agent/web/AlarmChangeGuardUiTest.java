package com.dpom.agent.web;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 告警变更管控静态 UI 与“不代理生产写”安全边界契约。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AlarmChangeGuardUiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void servesLightweightUiAndModules() throws Exception {
        mockMvc.perform(get("/change-guard/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"hero-title\"")));
        for (String resource : new String[] {"styles.css", "config.js", "api-client.js", "state.js", "app.js"}) {
            mockMvc.perform(get("/change-guard/" + resource)).andExpect(status().isOk());
        }
    }

    @Test
    void clientDoesNotPersistSecretsOrInjectRemoteHtml() throws Exception {
        String config = resource("static/change-guard/config.js");
        String client = resource("static/change-guard/api-client.js");
        String app = resource("static/change-guard/app.js");

        String html = resource("static/change-guard/index.html");
        assertThat(config).doesNotContain("AK", "SK", "Bearer ", "token:");
        assertThat(html + client + app)
                .doesNotContain("localStorage", "sessionStorage", "indexedDB", "document.cookie", "innerHTML",
                        "Bearer Token", "建立安全会话", "Authorization")
                .contains("textContent", "crypto.randomUUID");
    }

    @Test
    void ruleEditorExposesEnterpriseProjectScope() throws Exception {
        String html = resource("static/change-guard/index.html");
        String app = resource("static/change-guard/app.js");
        assertThat(html).contains("rule-eps", "留空=全部");
        assertThat(app).contains("ruleSelector");
        assertThat(app).doesNotContain("enterpriseProjectId: \"0\"");
    }

    @Test
    void dpomAgentDoesNotExposeUnscopedChangeGuardWriteRoutes() {
        assertThat(mappings.getHandlerMethods().keySet())
                .allMatch(info -> info.getPatternValues().stream()
                        .noneMatch(pattern -> pattern.startsWith("/api/v1/operations")));
    }

    private String resource(String path) throws Exception {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
