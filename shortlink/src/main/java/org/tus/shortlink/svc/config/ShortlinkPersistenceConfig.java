package org.tus.shortlink.svc.config;

import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.orm.hibernate5.LocalSessionFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.tus.common.domain.persistence.PersistenceService;
import org.tus.common.domain.persistence.QueryService;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
public class ShortlinkPersistenceConfig {
    /**
     * PostgreSQL DataSource (primary) for business data (t_link, etc.).
     * Marked as @Primary so Hibernate/JPA uses this instead of ClickHouse DataSource.
     */
    @Bean
    @Primary
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${spring.datasource.driver-class-name}") String driverClassName) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(driverClassName);
        ds.setUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }

    /**
     * Hibernate SessionFactory backed by PostgreSQL DataSource.
     * Schema is managed by Flyway, not Hibernate.
     */
    @Bean
    public LocalSessionFactoryBean sessionFactory(DataSource dataSource) {
        LocalSessionFactoryBean factory = new LocalSessionFactoryBean();
        factory.setDataSource(dataSource);

        factory.setPackagesToScan(
                "org.tus.shortlink.svc.entity",
                "org.tus.common.domain.persistence.entity"
        );

        Properties props = new Properties();
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.put("hibernate.hbm2ddl.auto", "none");

        factory.setHibernateProperties(props);
        return factory;
    }

    /**
     * PersistenceService implementation of QueryService.
     * Business layer should inject QueryService, not PersistenceService.
     */
    @Bean
    public QueryService queryService(SessionFactory sessionFactory) {
        return new PersistenceService(sessionFactory);
    }

    /**
     * Hibernate transaction manager.
     */
    @Bean
    public PlatformTransactionManager transactionManager(SessionFactory sessionFactory) {
        return new HibernateTransactionManager(sessionFactory);
    }
}
