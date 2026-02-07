FROM eclipse-temurin:17-jdk
WORKDIR /app
# Copy fat JAR for Docker deployment
COPY target/shortlink-identity-service-1.0-SNAPSHOT.jar app.jar
EXPOSE 8083
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
