package org.tus.shortlink.svc.config;

import jakarta.annotation.PostConstruct;
import org.hibernate.SessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.orm.hibernate5.LocalSessionFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.tus.common.domain.persistence.PersistenceService;
import org.tus.common.domain.persistence.QueryService;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@EnableTransactionManagement
public class ShortlinkPersistenceTestConfig {
    @Bean(initMethod = "start", destroyMethod = "stop")
    public PostgreSQLContainer<?> postgreSQLContainer() {
        return new PostgreSQLContainer<>("postgres:15")
                .withDatabaseName("shortlink")
                .withUsername("test")
                .withPassword("test");
    }

    @Bean
    public DataSource dataSource(PostgreSQLContainer<?> container) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(container.getDriverClassName());
        ds.setUrl(container.getJdbcUrl());
        ds.setUsername(container.getUsername());
        ds.setPassword(container.getPassword());
        return ds;
    }

    @Bean
    public LocalSessionFactoryBean sessionFactory(DataSource dataSource) {
        LocalSessionFactoryBean sessionFactory = new LocalSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);

        // Entity scan, includes biz module entity and
        // base entity located in persistence module
        sessionFactory.setPackagesToScan(
                "org.tus.shortlink.svc.entity",
                "org.tus.common.domain.persistence.entity"
        );

        Properties props = new Properties();
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");

        // Enable Entity -> DB Table in test mode
        props.put("hibernate.hbm2ddl.auto", "update");

        // Show SQL in test mode for debugging
        props.put("hibernate.show_sql", "true");
        props.put("hibernate.format_sql", "true");

        sessionFactory.setHibernateProperties(props);
        return sessionFactory;
    }

    @Bean
    public QueryService persistenceService(SessionFactory sessionFactory) {
        return new PersistenceService(sessionFactory);
    }

    @Bean
    public PlatformTransactionManager transactionManager(SessionFactory sessionFactory) {
        return new HibernateTransactionManager(sessionFactory);
    }

    @PostConstruct
    public void printDbConnection() {
        PostgreSQLContainer<?> pg = postgreSQLContainer();
        System.out.println("JDBC URL: " + pg.getJdbcUrl());
        System.out.println("Host: " + pg.getHost());
        System.out.println("Mapped port: " + pg.getMappedPort(5432));
    }
}