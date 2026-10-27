package doc.batch.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.batch.BatchDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * [메인 DB] Spring Batch 메타(BATCH_*), SHEDLOCK, 스케줄/이력(BATCH_SCHEDULE, BATCH_RUN_HIST)
 *  - @Primary : 이름 없는 @Transactional, 자동 구성이 이 DataSource/TM을 사용
 *  - @BatchDataSource : Spring Batch 메타 테이블 위치를 명시
 *
 * 주의: BatchApplication의 @MapperScan("doc.batch.**.mapper")는 삭제할 것 (DB별로 아래에서 각각 스캔)
 */
@Configuration
@MapperScan(basePackages = "doc.batch.controller.service.mapper",
            sqlSessionFactoryRef = "mainSqlSessionFactory")
public class MainDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("app.datasource.main")
    public DataSourceProperties mainDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @BatchDataSource
    @ConfigurationProperties("app.datasource.main.hikari")
    public HikariDataSource mainDataSource(
            @Qualifier("mainDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public SqlSessionFactory mainSqlSessionFactory(
            @Qualifier("mainDataSource") DataSource dataSource) throws Exception {
        return MyBatisSupport.build(dataSource, "classpath:mapper/main/**/*.xml");
    }

    @Bean
    @Primary
    public PlatformTransactionManager mainTransactionManager(
            @Qualifier("mainDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
