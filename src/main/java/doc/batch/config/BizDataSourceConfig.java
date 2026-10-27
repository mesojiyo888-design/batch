package doc.batch.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * [업무 DB] 배치가 실제로 읽고 쓰는 업무 데이터용 (두 번째 DataSource)
 *  - @Primary 없음 : 사용할 때 이름(Qualifier / transactionManager="bizTransactionManager")을 명시해야 함
 *  - 이 DB 매퍼는 doc.batch.mapper.biz 패키지, XML은 classpath:mapper/biz/ 아래에 둘 것
 *
 * Step에서 이 DB에 쓰는 경우 해당 Step의 transactionManager를 bizTransactionManager로 지정:
 *   stepBuilderFactory.get("x").<A, B>chunk(100)...transactionManager(bizTransactionManager).build();
 */
@Configuration
@MapperScan(basePackages = "doc.batch.mapper.biz",
            sqlSessionFactoryRef = "bizSqlSessionFactory")
public class BizDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource.biz")
    public DataSourceProperties bizDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("app.datasource.biz.hikari")
    public HikariDataSource bizDataSource(
            @Qualifier("bizDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    public SqlSessionFactory bizSqlSessionFactory(
            @Qualifier("bizDataSource") DataSource dataSource) throws Exception {
        return MyBatisSupport.build(dataSource, "classpath:mapper/biz/**/*.xml");
    }

    @Bean
    public PlatformTransactionManager bizTransactionManager(
            @Qualifier("bizDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
