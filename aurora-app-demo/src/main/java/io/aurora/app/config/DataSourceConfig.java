package io.aurora.app.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * MySQL is the system of record. PostgreSQL/pgvector is the vector index.
 * Keeping the two pools separate makes the VectorStorePort boundary explicit.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(@Value("${spring.datasource.url}") String url,
                                 @Value("${spring.datasource.username}") String username,
                                 @Value("${spring.datasource.password}") String password) {
        return build(url, username, password, "aurora-main");
    }

    @Bean("vectorDataSource")
    public DataSource vectorDataSource(@Value("${aurora.retrieval.pg.url}") String url,
                                       @Value("${aurora.retrieval.pg.username}") String username,
                                       @Value("${aurora.retrieval.pg.password}") String password) {
        return build(url, username, password, "aurora-vector");
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean("vectorJdbcTemplate")
    public JdbcTemplate vectorJdbcTemplate(@Qualifier("vectorDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    private DataSource build(String url, String username, String password, String poolName) {
        HikariDataSource dataSource = DataSourceBuilder.create()
                .url(url).username(username).password(password)
                .type(HikariDataSource.class).build();
        dataSource.setPoolName(poolName);
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(1);
        dataSource.setConnectionTimeout(5_000);
        return dataSource;
    }
}
