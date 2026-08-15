package com.dpom.agent.web.metrics;

import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.llm.ModelClient;
import com.dpom.agent.common.logtemplate.LogTemplateMinerClient;
import com.dpom.agent.common.runtime.RuntimeEvidenceClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * 在原 bean 上包装适配器接口（就地包装，保持单一 bean，避免 @Primary 同类型自注入循环依赖与 @MockitoBean 歧义）。
 */
@Component
public class AdapterMeteringBeanPostProcessor implements BeanPostProcessor {

    private final AdapterMetrics metrics;

    public AdapterMeteringBeanPostProcessor(AdapterMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof MeteredModelClient || bean instanceof MeteredCodeGraphClient
                || bean instanceof MeteredLogTemplateMinerClient || bean instanceof MeteredRuntimeEvidenceClient) {
            return bean;
        }
        if (bean instanceof ModelClient modelClient) {
            return new MeteredModelClient(modelClient, metrics);
        }
        if (bean instanceof CodeGraphClient codeGraphClient) {
            return new MeteredCodeGraphClient(codeGraphClient, metrics);
        }
        if (bean instanceof LogTemplateMinerClient miner) {
            return new MeteredLogTemplateMinerClient(miner, metrics);
        }
        if (bean instanceof RuntimeEvidenceClient runtime) {
            return new MeteredRuntimeEvidenceClient(runtime, metrics);
        }
        return bean;
    }
}
