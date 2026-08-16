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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * development Profile 装配隔离：只暴露下载/校验/导入，不暴露审批/上传/打包。
 */
@SpringBootTest(properties = "dpom.handoff.mode=development")
@AutoConfigureMockMvc
class DevelopmentProfileTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void developmentExposesOnlyDevelopmentController() {
        assertThat(context.getBeansOfType(ProductionHandoffController.class)).isEmpty();
        assertThat(context.getBeansOfType(DevelopmentHandoffController.class)).isNotEmpty();
    }

    @Test
    void uploadEndpointIsNotMappedInDevelopment() throws Exception {
        mockMvc.perform(post("/api/v1/investigations/1/handoff/upload").contentType(APPLICATION_JSON)
                        .content("{\"packageId\":\"p1\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void packageEndpointIsNotMappedInDevelopment() throws Exception {
        mockMvc.perform(post("/api/v1/investigations/1/handoff/package").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void approveEndpointIsNotMappedInDevelopment() throws Exception {
        mockMvc.perform(post("/api/v1/investigations/1/handoff/approve").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void verifyEndpointIsMappedInDevelopment() throws Exception {
        mockMvc.perform(post("/api/v1/handoff/verify").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("OBS_DISABLED"));
    }
}
