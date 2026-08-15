package com.example.asset;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 能源资产管理入口。 */
@RestController
public class AssetController {
    private final AssetService service;
    public AssetController(AssetService service) { this.service = service; }
    @PostMapping("/devices")
    public void createDevice() { service.create(); }
}
