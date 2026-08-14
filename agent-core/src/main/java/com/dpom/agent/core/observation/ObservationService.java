package com.dpom.agent.core.observation;

import com.dpom.agent.core.persistence.ObservationDao;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 观察服务：记录调查过程中的证据。
 */
@Service
public class ObservationService {

    private final ObservationDao observationDao;

    /**
     * 构造器注入。
     *
     * @param observationDao 观察 DAO
     */
    public ObservationService(ObservationDao observationDao) {
        this.observationDao = observationDao;
    }

    /**
     * 记录一条观察（证据）。
     *
     * @param investigationId         调查 id
     * @param runId                   运行 id（可为空）
     * @param source                  来源
     * @param artifactRef             工件引用（可为空）
     * @param location                位置（可为空）
     * @param supportsHypothesisIds   支持的假设 id（逗号分隔，可为空）
     * @param contradictsHypothesisIds 反驳的假设 id（逗号分隔，可为空）
     * @param summary                 摘要
     * @param payloadJson             负载（可为空）
     * @return 观察 id
     */
    public long record(long investigationId, Long runId, String source, String artifactRef, String location,
                       String supportsHypothesisIds, String contradictsHypothesisIds, String summary,
                       String payloadJson) {
        return observationDao.insert(new Observation(null, investigationId, runId, source, artifactRef, location,
                supportsHypothesisIds, contradictsHypothesisIds, summary, payloadJson, null));
    }

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 观察（可为空）
     */
    public Optional<Observation> findById(long id) {
        return observationDao.findById(id);
    }

    /**
     * 按调查查询观察列表。
     *
     * @param investigationId 调查 id
     * @return 观察列表
     */
    public List<Observation> findByInvestigation(long investigationId) {
        return observationDao.findByInvestigationId(investigationId);
    }
}
