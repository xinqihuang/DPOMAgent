package com.dpom.agent.alarm.notification;

import com.dpom.agent.alarm.domain.NotificationRule;
import com.dpom.agent.alarm.domain.NotificationStatus;
import com.dpom.agent.alarm.persistence.NotificationRecordDao;
import com.dpom.agent.alarm.persistence.command.NotificationRecordInsert;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 邮件与 IM webhook 渠道发送及分派单测（MockRestServiceServer）。
 */
@ExtendWith(MockitoExtension.class)
class NotificationSenderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private NotificationRecordDao recordDao;

    @Test
    void emailSenderPostsToGatewayAndReturnsOk() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://mail-gw/email")).andExpect(jsonPath("$.to").value("a@b.com"))
                .andRespond(withSuccess());
        EmailNotificationSender sender = new EmailNotificationSender(builder.build(), objectMapper,
                "https://mail-gw/email");

        SendOutcome outcome = sender.send(message(1L, "EMAIL", "a@b.com"));

        assertThat(outcome.success()).isTrue();
        server.verify();
    }

    @Test
    void imWebhookSenderPostsToWebhookUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://hook/im")).andExpect(jsonPath("$.incidentId").value(1))
                .andRespond(withSuccess());
        ImWebhookNotificationSender sender = new ImWebhookNotificationSender(builder.build(), objectMapper);

        SendOutcome outcome = sender.send(message(1L, "IM_WEBHOOK", "https://hook/im"));

        assertThat(outcome.success()).isTrue();
        server.verify();
    }

    @Test
    void emailSenderFailsWhenGatewayNotConfigured() {
        EmailNotificationSender sender = new EmailNotificationSender(RestClient.create(), objectMapper, "");

        SendOutcome outcome = sender.send(message(1L, "EMAIL", "a@b.com"));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorMessage()).contains("未配置");
    }

    @Test
    void dispatchSendsAndRecordsPerChannel() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://mail-gw/email")).andRespond(withSuccess());
        server.expect(requestTo("https://hook/im")).andRespond(withSuccess());
        RestClient restClient = builder.build();
        EmailNotificationSender email = new EmailNotificationSender(restClient, objectMapper,
                "https://mail-gw/email");
        ImWebhookNotificationSender im = new ImWebhookNotificationSender(restClient, objectMapper);
        NotificationDispatchService dispatch = new NotificationDispatchService(List.of(email, im),
                recordDao, objectMapper);

        NotificationRule rule = new NotificationRule(1L, "r", null, null, null, null, null,
                "[{\"channel\":\"EMAIL\",\"recipient\":\"a@b.com\"},"
                        + "{\"channel\":\"IM_WEBHOOK\",\"recipient\":\"https://hook/im\"}]",
                true, LocalDateTime.now(), LocalDateTime.now());
        dispatch.dispatch(7L, List.of(rule), "主题", "正文");

        ArgumentCaptor<NotificationRecordInsert> captor = ArgumentCaptor.forClass(NotificationRecordInsert.class);
        org.mockito.Mockito.verify(recordDao, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(rec ->
                assertThat(rec.getStatus()).isEqualTo(NotificationStatus.SENT));
        server.verify();
    }

    private NotificationMessage message(long incidentId, String channel, String recipient) {
        ChannelTarget target = new ChannelTarget(
                com.dpom.agent.alarm.domain.NotificationChannel.valueOf(channel), recipient);
        return new NotificationMessage(incidentId, "subject", "body", target);
    }
}
