package com.dpom.agent.alarm.query;

import com.dpom.agent.alarm.domain.Alarm;
import com.dpom.agent.alarm.persistence.AlarmQuery;
import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.AlarmStatus;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 告警分页查询控制器切片测试。
 */
@WebMvcTest(AlarmQueryController.class)
class AlarmQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlarmQueryService queryService;

    @Test
    void returnsPageWithNextCursor() throws Exception {
        when(queryService.query(any(AlarmQuery.class))).thenReturn(new AlarmPage(
                List.of(alarm(1L), alarm(2L)), 1L));

        mockMvc.perform(get("/api/v1/alarms").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.nextCursor").value(1));
    }

    @Test
    void appliesFilters() throws Exception {
        when(queryService.query(any(AlarmQuery.class))).thenReturn(new AlarmPage(List.of(), null));

        mockMvc.perform(get("/api/v1/alarms")
                        .param("source", "AOM")
                        .param("severity", "CRITICAL")
                        .param("status", "FIRING")
                        .param("resourceId", "res-1")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    private static Alarm alarm(long id) {
        LocalDateTime now = LocalDateTime.now();
        return new Alarm(id, AlarmSource.AOM, "webhook", null, "fp", "res-1", "name",
                SeverityLevel.CRITICAL, AlarmStatus.FIRING, 1, now, now, now, "svc", "prod", "{}", null);
    }
}
