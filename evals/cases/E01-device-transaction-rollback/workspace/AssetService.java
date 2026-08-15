package com.example.asset;

/**
 * E01 场景：资产管理服务（@Transactional），insert 抛异常时事务自动回滚。
 */
public class AssetService {

    private final AssetRepository repository = new AssetRepository();

    /**
     * 创建并插入；insert() 抛异常即触发回滚。
     */
    public void create() {
        repository.insert();
    }
}
