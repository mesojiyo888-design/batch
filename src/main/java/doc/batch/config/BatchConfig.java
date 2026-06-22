package doc.batch.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Boot가 자동 구성해주는 것들 (별도 설정 불필요):
 *   - DataSource (application.yml)
 *   - JobRepository / JobLauncher (spring-boot-starter-batch)
 *   - MyBatis SqlSessionFactory (mybatis-spring-boot-starter)
 *   - TransactionManager
 *
 * 여기서는 ShedLock LockProvider만 등록
 */
@Configuration
public class BatchConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build()
        );
    }
}
