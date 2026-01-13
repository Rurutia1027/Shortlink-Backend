FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy prebuilt JAR from CI pipeline
COPY target/shortlink-service-1.0-SNAPSHOT.jar app.jar

EXPOSE 8081

# Set prod profile and port
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]