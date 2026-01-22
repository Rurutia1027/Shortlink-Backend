# Use a lightweight JDK 17 base image
FROM eclipse-temurin:17-jdk

# Set working directory
WORKDIR /app

# Copy the fat jar into the container
COPY target/shortlink-flyway-1.0-SNAPSHOT-with-dependencies.jar app.jar

# Default Flyway configuration (override at runtime via environment variables or -D system properties)
# Configuration priority: System Property (-D) > Environment Variable > Default Value
#
# These defaults are for reference only - should be overridden in K8s Job configurations:
#   - flyway-admin-job.yaml: uses classpath:migration/db/admin
#   - flyway-shortlink-job.yaml: uses classpath:migration/db/shortlink
#
# Example: Override via environment variables
#   docker run -e FLYWAY_DB_URL=jdbc:postgresql://localhost:5432/mydb \
#              -e FLYWAY_DB_USER=myuser \
#              -e FLYWAY_DB_PASSWORD=mypass \
#              -e FLYWAY_LOCATIONS=classpath:migration/db/admin \
#              shortlink-flyway:latest
#
# Example: Override via system properties
#   docker run shortlink-flyway:latest \
#     java -Dflyway.db.url=jdbc:postgresql://localhost:5432/mydb \
#          -Dflyway.db.user=myuser \
#          -Dflyway.db.password=mypass \
#          -Dflyway.locations=classpath:migration/db/shortlink \
#          -jar /app/app.jar
ENV FLYWAY_DB_URL=jdbc:postgresql://postgres.shortlink.svc.cluster.local:5432/shortlink
ENV FLYWAY_DB_USER=admin
ENV FLYWAY_DB_PASSWORD=admin
ENV FLYWAY_SCHEMA_TABLE=flyway_schema_history
ENV FLYWAY_LOCATIONS=classpath:migration/db/shortlink
ENV FLYWAY_BASELINE_ON_MIGRATE=false
ENV FLYWAY_OUT_OF_ORDER=true

# Set entrypoint to run the jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
