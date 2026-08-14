package com.dpom.agent.adapter.runtime;

import com.dpom.agent.common.runtime.ObservationInput;
import com.dpom.agent.common.runtime.RuntimeEvidenceQueryException;
import com.dpom.agent.common.runtime.RuntimeEvidenceTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 运行时证据客户端验收测试：success / empty / error / timeout。
 */
class DpomBaseMcpClientTest {

    private MockRestServiceServer server;

    private DpomBaseMcpClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DpomBaseMcpClient(builder.build());
    }

    /**
     * 正常返回证据。
     */
    @Test
    void searchLogsReturnsEvidence() {
        server.expect(requestTo("/api/v1/evidence/logs?serviceCode=asset-service&environment=prod&keyword=INSERT&timeRange=1h"))
                .andRespond(withSuccess(
                        "[{\"source\":\"logs\",\"artifactId\":\"log://asset/1\",\"location\":\"asset-service\",\"summary\":\"INSERT 失败\",\"payload\":\"{}\"}]",
                        MediaType.APPLICATION_JSON));

        List<ObservationInput> result = client.searchLogs("asset-service", "prod", "INSERT", "1h");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).artifactRef().source()).isEqualTo("logs");
        assertThat(result.get(0).summary()).isEqualTo("INSERT 失败");
        server.verify();
    }

    /**
     * 空结果。
     */
    @Test
    void returnsEmptyWhenNoEvidence() {
        server.expect(requestTo("/api/v1/evidence/traces/t1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.queryTrace("t1")).isEmpty();
        server.verify();
    }

    /**
     * 查询错误映射。
     */
    @Test
    void mapsQueryError() {
        server.expect(requestTo("/api/v1/evidence/alerts?serviceCode=asset-service&environment=prod&timeRange=1h"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.queryAlerts("asset-service", "prod", "1h"))
                .isInstanceOf(RuntimeEvidenceQueryException.class);
        server.verify();
    }

    /**
     * 超时映射。
     */
    @Test
    void mapsTimeout() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            Thread acceptor = new Thread(() -> {
                try (Socket ignored = socket.accept()) {
                    Thread.sleep(5000);
                } catch (Exception ignored) {
                    // 忽略
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(500);
            factory.setReadTimeout(100);
            RestClient restClient = RestClient.builder()
                    .baseUrl("http://localhost:" + socket.getLocalPort())
                    .requestFactory(factory)
                    .build();
            DpomBaseMcpClient timeoutClient = new DpomBaseMcpClient(restClient);

            assertThatThrownBy(() -> timeoutClient.queryMetrics("asset-service", "cpu", "1h"))
                    .isInstanceOf(RuntimeEvidenceTimeoutException.class);
        }
    }
}
