package com.dpom.agent.adapter.runtime;

import com.dpom.agent.common.runtime.ArtifactRef;
import com.dpom.agent.common.runtime.ObservationInput;
import com.dpom.agent.common.runtime.RuntimeEvidenceClient;
import com.dpom.agent.common.runtime.RuntimeEvidenceException;
import com.dpom.agent.common.runtime.RuntimeEvidenceQueryException;
import com.dpom.agent.common.runtime.RuntimeEvidenceTimeoutException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;

/**
 * DPOMBaseMCPServer 的 REST 客户端实现：把远端证据 DTO 转换为内部 ObservationInput。
 */
public class DpomBaseMcpClient implements RuntimeEvidenceClient {

    private final RestClient restClient;

    /**
     * 构造客户端。
     *
     * @param restClient 已配置 baseUrl 与超时的 RestClient
     */
    public DpomBaseMcpClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<ObservationInput> searchLogs(String serviceCode, String environment, String keyword, String timeRange) {
        RemoteEvidence[] array = get(RemoteEvidence[].class,
                "/api/v1/evidence/logs?serviceCode={sc}&environment={env}&keyword={kw}&timeRange={tr}",
                serviceCode, environment, keyword, timeRange);
        return toInputs(array);
    }

    @Override
    public List<ObservationInput> queryTrace(String traceId) {
        RemoteEvidence[] array = get(RemoteEvidence[].class, "/api/v1/evidence/traces/{traceId}", traceId);
        return toInputs(array);
    }

    @Override
    public List<ObservationInput> queryAlerts(String serviceCode, String environment, String timeRange) {
        RemoteEvidence[] array = get(RemoteEvidence[].class,
                "/api/v1/evidence/alerts?serviceCode={sc}&environment={env}&timeRange={tr}",
                serviceCode, environment, timeRange);
        return toInputs(array);
    }

    @Override
    public List<ObservationInput> queryMetrics(String serviceCode, String metricName, String timeRange) {
        RemoteEvidence[] array = get(RemoteEvidence[].class,
                "/api/v1/evidence/metrics?serviceCode={sc}&metricName={m}&timeRange={tr}",
                serviceCode, metricName, timeRange);
        return toInputs(array);
    }

    /**
     * 执行 GET 并统一映射超时与错误。
     */
    private <T> T get(Class<T> bodyType, String uriTemplate, Object... uriVariables) {
        try {
            return restClient.get()
                    .uri(uriTemplate, uriVariables)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new RuntimeEvidenceQueryException("运行时证据查询失败，状态码：" + response.getStatusCode().value());
                    })
                    .body(bodyType);
        } catch (ResourceAccessException e) {
            throw new RuntimeEvidenceTimeoutException("运行时证据服务超时", e);
        } catch (RuntimeEvidenceException e) {
            throw e;
        } catch (RestClientException e) {
            throw new RuntimeEvidenceQueryException("运行时证据查询失败", e);
        }
    }

    /**
     * 把远端证据数组转换为内部输入列表。
     */
    private List<ObservationInput> toInputs(RemoteEvidence[] array) {
        if (array == null) {
            return List.of();
        }
        return Arrays.stream(array)
                .map(evidence -> new ObservationInput(
                        new ArtifactRef(evidence.source(), evidence.artifactId(), evidence.location()),
                        evidence.summary(), evidence.payload()))
                .toList();
    }

    /** 远端证据 DTO（仅适配层可见）。 */
    public record RemoteEvidence(String source, String artifactId, String location,
                                 String summary, String payload) {
    }
}
