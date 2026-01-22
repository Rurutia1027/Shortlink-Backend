package org.tus.shortlink.migration;

import org.flywaydb.core.Flyway;

/**
 * Standalone Flyway migration runner.
 * Configuration can be provided via:
 * 1. Environment variables (FLYWAY_*)
 * 2. System properties (-Dflyway.*)
 * 3. Default values (fallback)
 */
public class FlywayStandalone {

    // Configuration keys for environment variables
    private static final String ENV_DB_URL = "FLYWAY_DB_URL";
    private static final String ENV_DB_USER = "FLYWAY_DB_USER";
    private static final String ENV_DB_PASSWORD = "FLYWAY_DB_PASSWORD";
    private static final String ENV_SCHEMA_TABLE = "FLYWAY_SCHEMA_TABLE";
    private static final String ENV_LOCATIONS = "FLYWAY_LOCATIONS";
    private static final String ENV_BASELINE_ON_MIGRATE = "FLYWAY_BASELINE_ON_MIGRATE";
    private static final String ENV_OUT_OF_ORDER = "FLYWAY_OUT_OF_ORDER";

    // Configuration keys for system properties
    private static final String PROP_DB_URL = "flyway.db.url";
    private static final String PROP_DB_USER = "flyway.db.user";
    private static final String PROP_DB_PASSWORD = "flyway.db.password";
    private static final String PROP_SCHEMA_TABLE = "flyway.schema.table";
    private static final String PROP_LOCATIONS = "flyway.locations";
    private static final String PROP_BASELINE_ON_MIGRATE = "flyway.baseline.on.migrate";
    private static final String PROP_OUT_OF_ORDER = "flyway.out.of.order";

    // Default values
    // Note: These defaults should be overridden via environment variables in K8s/Docker
    private static final String DEFAULT_DB_URL =
            "jdbc:postgresql://postgres.shortlink.svc.cluster.local:5432/shortlink";
    private static final String DEFAULT_DB_USER = "admin";
    private static final String DEFAULT_DB_PASSWORD = "admin";
    private static final String DEFAULT_SCHEMA_TABLE = "flyway_schema_history";
    // Default locations - should be overridden per job (admin or shortlink)
    private static final String DEFAULT_LOCATIONS = "classpath:migration/db/shortlink";
    private static final boolean DEFAULT_BASELINE_ON_MIGRATE = false;
    private static final boolean DEFAULT_OUT_OF_ORDER = true;

    public static void main(String[] args) {
        try {
            // Load configuration from environment variables, system properties, or defaults
            FlywayConfig config = loadConfiguration();

            // Print configuration (masking sensitive data)
            printConfiguration(config);

            // Configure and run Flyway
            Flyway flyway = Flyway.configure()
                    .dataSource(
                            config.getDbUrl(),
                            config.getDbUser(),
                            config.getDbPassword())
                    .table(config.getSchemaTable())
                    .locations(config.getLocations())
                    .baselineOnMigrate(config.isBaselineOnMigrate())
                    .outOfOrder(config.isOutOfOrder())
                    .load();

            // Execute migrations
            flyway.migrate();

            System.out.println("✓ Flyway migrations completed successfully.");
            System.exit(0);
        } catch (Exception e) {
            System.err.println("✗ Flyway migration failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Load configuration from environment variables, system properties, or defaults.
     * Priority: System Property > Environment Variable > Default Value
     */
    private static FlywayConfig loadConfiguration() {
        FlywayConfig config = new FlywayConfig();

        config.setDbUrl(getConfig(ENV_DB_URL, PROP_DB_URL, DEFAULT_DB_URL));
        config.setDbUser(getConfig(ENV_DB_USER, PROP_DB_USER, DEFAULT_DB_USER));
        config.setDbPassword(getConfig(ENV_DB_PASSWORD, PROP_DB_PASSWORD, DEFAULT_DB_PASSWORD));
        config.setSchemaTable(getConfig(ENV_SCHEMA_TABLE, PROP_SCHEMA_TABLE, DEFAULT_SCHEMA_TABLE));
        config.setLocations(getConfig(ENV_LOCATIONS, PROP_LOCATIONS, DEFAULT_LOCATIONS));
        config.setBaselineOnMigrate(getBooleanConfig(
                ENV_BASELINE_ON_MIGRATE, PROP_BASELINE_ON_MIGRATE, DEFAULT_BASELINE_ON_MIGRATE));
        config.setOutOfOrder(getBooleanConfig(
                ENV_OUT_OF_ORDER, PROP_OUT_OF_ORDER, DEFAULT_OUT_OF_ORDER));

        return config;
    }

    /**
     * Get configuration value with priority: System Property > Environment Variable > Default
     */
    private static String getConfig(String envKey, String propKey, String defaultValue) {
        // Check system property first
        String value = System.getProperty(propKey);
        if (value != null && !value.isBlank()) {
            return value;
        }
        // Check environment variable
        value = System.getenv(envKey);
        if (value != null && !value.isBlank()) {
            return value;
        }
        // Return default
        return defaultValue;
    }

    /**
     * Get boolean configuration value
     */
    private static boolean getBooleanConfig(String envKey, String propKey, boolean defaultValue) {
        String value = getConfig(envKey, propKey, null);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value) || "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    /**
     * Print configuration (masking sensitive information)
     */
    private static void printConfiguration(FlywayConfig config) {
        System.out.println("Flyway Configuration:");
        System.out.println("  DB URL: " + config.getDbUrl());
        System.out.println("  DB User: " + config.getDbUser());
        System.out.println("  DB Password: " + maskPassword(config.getDbPassword()));
        System.out.println("  Schema Table: " + config.getSchemaTable());
        System.out.println("  Locations: " + config.getLocations());
        System.out.println("  Baseline On Migrate: " + config.isBaselineOnMigrate());
        System.out.println("  Out Of Order: " + config.isOutOfOrder());
        System.out.println();
    }

    /**
     * Mask password for logging
     */
    private static String maskPassword(String password) {
        if (password == null || password.length() <= 4) {
            return "****";
        }
        return password.substring(0, 2) + "****" + password.substring(password.length() - 2);
    }

    /**
     * Configuration holder class
     */
    private static class FlywayConfig {
        private String dbUrl;
        private String dbUser;
        private String dbPassword;
        private String schemaTable;
        private String locations;
        private boolean baselineOnMigrate;
        private boolean outOfOrder;

        // Getters and setters
        public String getDbUrl() {
            return dbUrl;
        }

        public void setDbUrl(String dbUrl) {
            this.dbUrl = dbUrl;
        }

        public String getDbUser() {
            return dbUser;
        }

        public void setDbUser(String dbUser) {
            this.dbUser = dbUser;
        }

        public String getDbPassword() {
            return dbPassword;
        }

        public void setDbPassword(String dbPassword) {
            this.dbPassword = dbPassword;
        }

        public String getSchemaTable() {
            return schemaTable;
        }

        public void setSchemaTable(String schemaTable) {
            this.schemaTable = schemaTable;
        }

        public String getLocations() {
            return locations;
        }

        public void setLocations(String locations) {
            this.locations = locations;
        }

        public boolean isBaselineOnMigrate() {
            return baselineOnMigrate;
        }

        public void setBaselineOnMigrate(boolean baselineOnMigrate) {
            this.baselineOnMigrate = baselineOnMigrate;
        }

        public boolean isOutOfOrder() {
            return outOfOrder;
        }

        public void setOutOfOrder(boolean outOfOrder) {
            this.outOfOrder = outOfOrder;
        }
    }
}
