package com.example.asset;

import org.springframework.stereotype.Repository;

/** 设备仓储：insert 尚未实现真实落库，直接抛异常（根因点）。 */
@Repository
public class AssetRepository {
    public void insert() {
        throw new IllegalStateException("insert failed");
    }
}
