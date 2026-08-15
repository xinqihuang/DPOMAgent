package com.example.asset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 设备创建服务：事务内插入，insert 抛异常即回滚。 */
@Service
public class AssetService {
    private final AssetRepository repository;
    public AssetService(AssetRepository repository) { this.repository = repository; }
    @Transactional
    public void create() { repository.insert(); }
}
