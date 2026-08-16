package com.dpom.agent.web;

import com.dpom.agent.web.controller.DevelopmentHandoffController;
import com.dpom.agent.web.controller.ProductionHandoffController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * production Profile 装配隔离：只暴露升级/打包/审批/上传，不暴露研发侧 verify/import。
 */
@SpringBootTest(properties = "dpom.handoff.mode=production")
@AutoConfigureMockMvc
class ProductionProfileTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void productionExposesOnlyProductionController() {
        assertThat(context.getBeansOfType(ProductionHandoffController.class)).isNotEmpty();
        assertThat(context.getBeansOfType(DevelopmentHandoffController.class)).isEmpty();
    }

    @Test
    void verifyEndpointIsNotMappedInProduction() throws Exception {
        mockMvc.perform(post("/api/v1/handoff/verify").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void escalationEndpointIsMappedInProduction() throws Exception {
        mockMvc.perform(get("/api/v1/investigations/999999999/escalation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }
}
