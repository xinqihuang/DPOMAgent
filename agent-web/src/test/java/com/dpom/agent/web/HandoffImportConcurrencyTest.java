package com.dpom.agent.web;

import com.dpom.agent.common.handoff.EvidenceHandoffStore;
import com.dpom.agent.common.handoff.InMemoryEvidenceHandoffStore;
import com.dpom.agent.core.handoff.DiagnosticEvidencePackage;
import com.dpom.agent.core.handoff.EvidenceHandoffService;
import com.dpom.agent.core.handoff.ImportResult;
import com.dpom.agent.core.handoff.PackageSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发幂等导入：两个线程导入同一 package，唯一键仲裁，只有一条 handoff_import。
 */
@SpringBootTest(properties = {
        "dpom.handoff.mode=development",
        "dpom.handoff.obs.enabled=true",
        "dpom.handoff.obs.bucket=b",
        "dpom.handoff.obs.prefix=p"
})
class HandoffImportConcurrencyTest {

    @Autowired
    private EvidenceHandoffService service;

    @Autowired
    private EvidenceHandoffStore store;

    @Autowired
    private PackageSerializer serializer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentImportOfSamePackageIsIdempotent() throws Exception {
        DiagnosticEvidencePackage pkg = new DiagnosticEvidencePackage(1, "p1", "svc", "env", "rel", "commit", "1h",
                Map.of("logs", List.of("template A count=1")), Map.of());
        store.store("p/p1.zip", serializer.serialize(pkg));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<ImportResult> f1 = pool.submit(() -> {
            start.await();
            return service.verifyAndImport("p/p1.zip", "svc", "rel", "commit");
        });
        Future<ImportResult> f2 = pool.submit(() -> {
            start.await();
            return service.verifyAndImport("p/p1.zip", "svc", "rel", "commit");
        });
        start.countDown();
        ImportResult r1 = f1.get();
        ImportResult r2 = f2.get();
        pool.shutdown();

        assertThat(List.of(r1.alreadyImported(), r2.alreadyImported())).containsExactlyInAnyOrder(false, true);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM handoff_import WHERE package_id = 'p1'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @TestConfiguration
    static class FakeStoreConfig {
        @Bean
        @Primary
        EvidenceHandoffStore fakeStore() {
            return new InMemoryEvidenceHandoffStore();
        }
    }
}
