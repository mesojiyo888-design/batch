package doc.batch.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Properties;

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

    /**
     * MyBatis DatabaseIdProvider - 운영(Oracle)/로컬 테스트(PostgreSQL) 겸용 매퍼를 위해
     * mapper XML에서 databaseId 속성으로 DB별 SQL 분기(예: 시퀀스 채번 방식)를 사용할 수 있게 한다.
     * mybatis-spring-boot-starter가 이 Bean을 자동 인식해 SqlSessionFactory에 적용한다.
     * (운영 Oracle 커넥션의 기존 동작에는 영향 없음 - databaseId="oracle" 분기만 그대로 사용됨)
     */
    @Bean
    public DatabaseIdProvider databaseIdProvider() {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties properties = new Properties();
        properties.setProperty("Oracle", "oracle");
        properties.setProperty("PostgreSQL", "postgresql");
        properties.setProperty("H2", "h2");
        provider.setProperties(properties);
        return provider;
    }
}
