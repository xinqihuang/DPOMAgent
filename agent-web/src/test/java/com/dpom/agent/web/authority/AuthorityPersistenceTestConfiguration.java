package com.dpom.agent.web.authority;

import com.dpom.agent.core.persistence.authority.MyBatisInvestigationAuthorityStore;
import com.dpom.agent.core.diagnosissource.DiagnosisSourceBuilder;
import com.dpom.agent.core.diagnosissource.DiagnosisTerminalCommitService;
import com.dpom.agent.core.diagnosisprogress.AuthorityProgressIntentFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** 只装配权威仓储所需基础设施的测试上下文。 */
@TestConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration(exclude = FlywayAutoConfiguration.class)
@MapperScan("com.dpom.agent.core.persistence.authority")
@Import({MyBatisInvestigationAuthorityStore.class, AuthorityProgressIntentFactory.class, DiagnosisSourceBuilder.class,
        DiagnosisTerminalCommitService.class})
class AuthorityPersistenceTestConfiguration {
}
