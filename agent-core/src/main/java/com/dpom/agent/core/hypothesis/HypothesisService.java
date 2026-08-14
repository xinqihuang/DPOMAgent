package com.dpom.agent.core.hypothesis;

import com.dpom.agent.core.persistence.HypothesisDao;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 假设服务：创建假设并维护其状态。
 */
@Service
public class HypothesisService {

    private final HypothesisDao hypothesisDao;

    /**
     * 构造器注入。
     *
     * @param hypothesisDao 假设 DAO
     */
    public HypothesisService(HypothesisDao hypothesisDao) {
        this.hypothesisDao = hypothesisDao;
    }

    /**
     * 创建假设（初始状态 PROPOSED）。
     *
     * @param investigationId 调查 id
     * @param description     假设描述
     * @return 假设 id
     */
    public long create(long investigationId, String description) {
        return hypothesisDao.insert(new Hypothesis(
                null, investigationId, null, description, HypothesisStatus.PROPOSED, null, null, null));
    }

    /**
     * 更新假设状态（否定证据保留：仅改状态，不删除观察）。
     *
     * @param id     假设 id
     * @param status 新状态
     */
    public void updateStatus(long id, HypothesisStatus status) {
        hypothesisDao.updateStatus(id, status);
    }

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 假设（可为空）
     */
    public Optional<Hypothesis> findById(long id) {
        return hypothesisDao.findById(id);
    }

    /**
     * 按调查查询假设列表。
     *
     * @param investigationId 调查 id
     * @return 假设列表
     */
    public List<Hypothesis> findByInvestigation(long investigationId) {
        return hypothesisDao.findByInvestigationId(investigationId);
    }
}
