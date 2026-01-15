FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy prebuilt JAR from CI pipeline
COPY target/shortlink-service-1.0-SNAPSHOT.jar app.jar

EXPOSE 8081

# move ENV to yaml files
## Set prod profile and port move
#ENV SPRING_PROFILES_ACTIVE=prod
#ENV SERVER_PORT=8081

ENTRYPOINT ["java"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]