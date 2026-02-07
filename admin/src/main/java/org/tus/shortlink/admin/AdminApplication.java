package org.tus.shortlink.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {
        HibernateJpaAutoConfiguration.class
})
@EntityScan(basePackages = {
        "org.tus.shortlink.admin.entity",
        "org.tus.common.domain.persistence"
})
@ComponentScan(basePackages = {
        "org.tus.shortlink.admin",
        "org.tus.shortlink.identity"  // Scan Identity module for IdentityClient beans
})
public class AdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
