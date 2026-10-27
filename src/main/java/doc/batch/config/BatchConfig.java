package doc.batch.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Boot가 자동 구성해주는 것들 (별도 설정 불필요):
 *   - JobRepository / JobLauncher (spring-boot-starter-batch, @BatchDataSource 로 지정한 메인 DB 사용)
 *
 * 다중 DB 로 바뀌면서 직접 구성하는 것들:
 *   - DataSource / SqlSessionFactory / TransactionManager → MainDataSourceConfig, BizDataSourceConfig
 *   - ShedLock LockProvider → 여기 (SHEDLOCK 테이블이 있는 메인 DB 를 명시)
 */
@Configuration
public class BatchConfig {

    @Bean
    public LockProvider lockProvider(@Qualifier("mainDataSource") DataSource dataSource) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build()
        );
    }
}
