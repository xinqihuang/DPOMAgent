package com.dpom.agent.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * energy-platform-demo 夹具契约测试：验证 Java/Spring 示例提供类/方法、caller/callee、Spring route 与影响面结构，
 * 供真实 CodeGraph E2E 与诊断回归套件使用（不依赖本机 CodeGraph）。
 */
class EnergyPlatformDemoFixtureTest {

    private static final Path FIXTURE = Path.of("..", "test-fixtures", "energy-platform-demo")
            .toAbsolutePath().normalize();

    @Test
    void assetServiceHasExpectedCallChainAndRoute() throws Exception {
        String controller = read("asset-service/src/main/java/com/example/asset/AssetController.java");
        String service = read("asset-service/src/main/java/com/example/asset/AssetService.java");
        String repository = read("asset-service/src/main/java/com/example/asset/AssetRepository.java");

        // 类/方法 + Spring route
        assertThat(controller).contains("@RestController", "@PostMapping(\"/devices\")", "createDevice");
        assertThat(service).contains("@Service", "@Transactional", "create");
        assertThat(repository).contains("@Repository", "insert");
        // caller/callee 链：Controller → Service → Repository
        assertThat(controller).contains("service.create()");
        assertThat(service).contains("repository.insert()");
    }

    @Test
    void otherServicesProvideRootCauseSymbols() throws Exception {
        String gateway = read("gateway-service/src/main/java/com/example/gateway/DownstreamClient.java");
        String telemetry = read("telemetry-service/src/main/java/com/example/telemetry/BatchPublisher.java");

        assertThat(gateway).contains("@Component", "call");
        assertThat(telemetry).contains("@Service", "flush");
    }

    private String read(String relative) throws Exception {
        Path file = FIXTURE.resolve(relative);
        assertThat(file).exists();
        return Files.readString(file);
    }
}
