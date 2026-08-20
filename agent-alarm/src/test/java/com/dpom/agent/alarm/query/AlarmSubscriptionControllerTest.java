package com.dpom.agent.alarm.query;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 告警订阅控制器切片测试。
 */
@WebMvcTest(AlarmSubscriptionController.class)
class AlarmSubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlarmSubscriptionRegistry registry;
    @MockBean
    private SubscriptionPushClient pushClient;

    @Test
    void registerReturnsRegistered() throws Exception {
        doNothing().when(registry).register(org.mockito.ArgumentMatchers.any());
        when(registry.size()).thenReturn(1);

        mockMvc.perform(post("/api/v1/alarms/subscriptions")
                        .contentType("application/json")
                        .content("{\"source\":\"AOM\",\"severity\":\"CRITICAL\","
                                + "\"callbackUrl\":\"https://hook/cb\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("registered"))
                .andExpect(jsonPath("$.totalSubscriptions").value(1));
    }
}
