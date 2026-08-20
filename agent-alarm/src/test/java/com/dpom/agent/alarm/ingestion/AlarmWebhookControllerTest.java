package com.dpom.agent.alarm.ingestion;

import com.dpom.agent.common.alarm.AlarmSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 告警 webhook 控制器切片测试。
 */
@WebMvcTest(AlarmWebhookController.class)
class AlarmWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlarmIngestionService ingestionService;

    @Test
    void acceptedAlarmReturns202() throws Exception {
        when(ingestionService.ingest(eq(AlarmSource.AOM), eq("{}"), eq("webhook")))
                .thenReturn(AlarmIngestionResult.accepted(123L));

        mockMvc.perform(post("/api/v1/alarms/webhook/AOM").content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.alarmId").value(123));
    }

    @Test
    void rejectedAlarmReturns422() throws Exception {
        when(ingestionService.ingest(eq(AlarmSource.CES), eq("{}"), eq("webhook")))
                .thenReturn(AlarmIngestionResult.rejected("标准化失败或必填字段缺失"));

        mockMvc.perform(post("/api/v1/alarms/webhook/CES").content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.rejectionReason").value("标准化失败或必填字段缺失"));
    }

    @Test
    void unknownSourceReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/alarms/webhook/UNKNOWN").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.accepted").value(false));
    }
}
