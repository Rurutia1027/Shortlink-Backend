# Use a lightweight JDK 17 base image
FROM eclipse-temurin:17-jdk

# Set working directory
WORKDIR /app

# Copy the fat jar into the container
COPY target/shortlink-flyway-1.0-SNAPSHOT-with-dependencies.jar app.jar

# Default Flyway configuration (override at runtime via -e)
ENV FLYWAY_DB_URL=jdbc:postgresql://postgres.shortlink.svc.cluster.local:5432/shortlink
ENV FLYWAY_DB_USER=admin
ENV FLYWAY_DB_PASSWORD=admin
ENV FLYWAY_SCHEMA_TABLE=flyway_schema_history
ENV FLYWAY_LOCATIONS=classpath:db/migration

# Set entrypoint to run the jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
