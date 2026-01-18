package org.tus.shortlink.migration;

import org.flywaydb.core.Flyway;

public class FlywayStandalone {
    private static final String DEFAULT_DB_URL =
            "jdbc:postgresql://postgres.shortlink.svc.cluster.local:5432/shortlink";
    private static final String DEFAULT_DB_USER = "admin";
    private static final String DEFAULT_DB_PASSWORD = "admin";
    private static final String DEFAULT_SCHEMA_TABLE = "flyway_schema_history";
    private static final String DEFAULT_LOCATIONS = "classpath:db/migration";

    public static void main(String[] args) {

        // Configure Flyway
        // TODO: wire these from YAML later (for now allow env/system overrides)
        String dbUrl = getConfig("FLYWAY_DB_URL", "flyway.db.url", DEFAULT_DB_URL);
        String dbUser = getConfig("FLYWAY_DB_USER", "flyway.db.user", DEFAULT_DB_USER);
        String dbPassword = getConfig("FLYWAY_DB_PASSWORD", "flyway.db.password", DEFAULT_DB_PASSWORD);
        String schemaTable = getConfig("FLYWAY_SCHEMA_TABLE", "flyway.schema.table", DEFAULT_SCHEMA_TABLE);
        String locations = getConfig("FLYWAY_LOCATIONS", "flyway.locations", DEFAULT_LOCATIONS);

        Flyway flyway = Flyway.configure()
                .dataSource(
                        dbUrl,
                        dbUser,
                        dbPassword)
                .table(schemaTable)
                .locations(locations)      // SQL migrations
                // "classpath:flyway-java-migrations") // Java-based migrations
                .baselineOnMigrate(false)
                .outOfOrder(true)
                .load();

        flyway.migrate();

        System.out.println("Flyway migrations completed successfully.");
        System.exit(0);
    }

    private static String getConfig(String envKey, String propKey, String defaultValue) {
        String value = System.getProperty(propKey);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = System.getenv(envKey);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
