package com.dpom.agent.web.changeguard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChangeGuardProxyControllerTest {

    private MockRestServiceServer server;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        mvc = MockMvcBuilders.standaloneSetup(new ChangeGuardProxyController(builder, "http://localhost:8081"))
                .build();
    }

    @Test
    void forwardsOnlyMappedOperationRequestAndIdempotencyKey() throws Exception {
        server.expect(once(), requestTo("http://localhost:8081/api/v1/operations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "idem-1"))
                .andRespond(withSuccess("{\"operation\":{\"id\":\"op-1\"}}", MediaType.APPLICATION_JSON));

        mvc.perform(post("/change-guard-api/api/v1/operations")
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changeTicket\":\"CHG-1\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"operation\":{\"id\":\"op-1\"}}"));
        server.verify();
    }

    @Test
    void rejectsPathsOutsideOperationsMapping() throws Exception {
        mvc.perform(post("/change-guard-api/actuator/env"))
                .andExpect(status().isNotFound());
    }
}
