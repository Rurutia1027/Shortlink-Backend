package org.tus.shortlink.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan(basePackages = {
        "org.tus.shortlink.admin.entity",
        "org.tus.common.domain.persistence"
})
// Temporarily removed exclusion to use Spring Boot's default JPA auto-configuration for table generation
// exclude = org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class
public class AdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
