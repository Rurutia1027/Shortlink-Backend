package org.tus.shortlink.admin.config;

import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.orm.hibernate5.LocalSessionFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.tus.common.domain.persistence.PersistenceService;
import org.tus.common.domain.persistence.QueryService;

import javax.sql.DataSource;
import java.util.Properties;

//@Configuration
public class AdminPersistenceConfig {

    @Value("${spring.jpa.hibernate.ddl-auto:none}")
    private String ddlAuto;

    /**
     * Hibernate SessionFactory backed by Spring Boot DataSource.
     * Schema can be managed by Hibernate (dev) or Flyway (prod).
     */
    @Bean
    public LocalSessionFactoryBean sessionFactory(DataSource dataSource) {
        LocalSessionFactoryBean factory = new LocalSessionFactoryBean();
        factory.setDataSource(dataSource);

        factory.setPackagesToScan(
                "org.tus.shortlink.admin.entity",
                "org.tus.common.domain.persistence.entity"
        );

        Properties props = new Properties();
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.put("hibernate.hbm2ddl.auto", ddlAuto);

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
