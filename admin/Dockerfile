FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy prebuilt Spring Boot fat JAR
COPY target/shortlink-admin-1.0-SNAPSHOT.jar app.jar

# Expose application port
EXPOSE 8082

# Application configuration via YAML / env in K8s Deployment
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8082

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
