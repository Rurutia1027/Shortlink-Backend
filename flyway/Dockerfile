# Use a lightweight JDK 17 base image
FROM eclipse-temurin:17-jdk

# Set working directory
WORKDIR /app

# Copy the fat jar into the container
COPY target/shortlink-flyway-1.0-SNAPSHOT-with-dependencies.jar app.jar

# Set entrypoint to run the jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
