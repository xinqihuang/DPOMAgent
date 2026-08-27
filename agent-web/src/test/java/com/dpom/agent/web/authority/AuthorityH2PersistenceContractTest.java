package com.dpom.agent.web.authority;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** 默认执行的 H2 权威持久化契约。 */
@SpringJUnitConfig(AuthorityPersistenceTestConfiguration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:authority_contract;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "mybatis.mapper-locations=classpath*:com/dpom/agent/core/persistence/mapper/*.xml",
        "management.endpoint.health.validate-group-membership=false"
})
class AuthorityH2PersistenceContractTest extends AbstractAuthorityPersistenceContract {

    @Override
    Resource schemaResource() {
        return new ClassPathResource("authority-h2-schema.sql");
    }
}
