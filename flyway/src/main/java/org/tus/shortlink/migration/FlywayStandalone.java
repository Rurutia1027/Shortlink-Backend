package org.tus.shortlink.migration;

import org.flywaydb.core.Flyway;

public class FlywayStandalone {

    public static void main(String[] args) {

        // Configure Flyway
        // TODO: config options should be extracted and support pass via env parameters
        // so that, docker & yaml set options can overwrite configs
        Flyway flyway = Flyway.configure()
                .dataSource(
                        "jdbc:postgresql://postgres.shortlink.svc.cluster.local:5432/shortlink",
                        "admin",
                        "admin")
                .table("flyway_schema_history")
                .locations("classpath:db/migration")      // SQL migrations
                // "classpath:flyway-java-migrations") // Java-based migrations
                .baselineOnMigrate(false)
                .outOfOrder(true)
                .load();

        flyway.migrate();

        System.out.println("Flyway migrations completed successfully.");
        System.exit(0);
    }
}
