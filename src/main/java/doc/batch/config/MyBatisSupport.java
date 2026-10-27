package doc.batch.config;

import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * 다중 DataSource 환경에서는 MyBatis 자동 구성이 비활성화되므로
 * application.yml의 mybatis.* 설정이 적용되지 않는다. → 코드에서 직접 지정.
 */
final class MyBatisSupport {

    private MyBatisSupport() {}

    static SqlSessionFactory build(DataSource dataSource, String mapperLocations) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(mapperLocations));

        org.apache.ibatis.session.Configuration cfg = new org.apache.ibatis.session.Configuration();
        cfg.setMapUnderscoreToCamelCase(true);
        cfg.setJdbcTypeForNull(JdbcType.VARCHAR);   // 기존 yml 설정 유지 (메인 앱과 맞추려면 JdbcType.NULL)
        cfg.setLogImpl(StdOutImpl.class);           // 운영에서는 p6spy와 중복되므로 제거 권장
        factory.setConfiguration(cfg);

        return factory.getObject();
    }
}
