FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy prebuilt Spring Boot fat JAR (with -exec classifier)
COPY target/shortlink-identity-1.0-SNAPSHOT-exec.jar app.jar

# Expose application port
EXPOSE 8083

# Application configuration via YAML / env in K8s Deployment
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8083

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
