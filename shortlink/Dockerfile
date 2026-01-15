FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy prebuilt Spring Boot fat JAR
COPY target/shortlink-service-1.0-SNAPSHOT.jar app.jar

# Expose application port
EXPOSE 8081

# Application configuration via YAML / env in K8s Deployment
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8081

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
