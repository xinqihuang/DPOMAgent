package com.dpom.agent.alarm.query;

import com.dpom.agent.alarm.domain.AlarmAudit;
import com.dpom.agent.alarm.domain.AlarmIncident;
import com.dpom.agent.alarm.persistence.AlarmIncidentQuery;
import com.dpom.agent.common.alarm.AlarmIncidentStatus;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 告警事件查询与审计时间线控制器切片测试。
 */
@WebMvcTest(AlarmIncidentQueryController.class)
class AlarmIncidentQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlarmIncidentQueryService queryService;

    @Test
    void incidentPageReturnsItems() throws Exception {
        when(queryService.query(any(AlarmIncidentQuery.class))).thenReturn(
                new AlarmIncidentPage(List.of(incident(1L)), null));

        mockMvc.perform(get("/api/v1/alarm-incidents").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void membersReturnsAlarmIds() throws Exception {
        when(queryService.findMembers(eq(7L))).thenReturn(List.of(10L, 11L));

        mockMvc.perform(get("/api/v1/alarm-incidents/7/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(10))
                .andExpect(jsonPath("$[1]").value(11));
    }

    @Test
    void auditTimelineReturnsEntries() throws Exception {
        when(queryService.auditTimeline(eq(7L))).thenReturn(List.of(
                new AlarmAudit(1L, "CORRELATE", "INCIDENT", 7L, null, "basis=SINGLE", "OK",
                        LocalDateTime.now())));

        mockMvc.perform(get("/api/v1/alarm-incidents/7/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("CORRELATE"));
    }

    private static AlarmIncident incident(long id) {
        LocalDateTime now = LocalDateTime.now();
        return new AlarmIncident(id, AlarmIncidentStatus.OPEN, SeverityLevel.CRITICAL, "svc", "prod",
                "SINGLE", "summary", now, null, false, null, null, null, null, now, now);
    }
}
