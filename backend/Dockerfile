# Matriz API image for local use with Docker Compose (see docker-compose.yml).
# Tests are not run here; use ./mvnw verify.

FROM eclipse-temurin:21.0.13_9-jdk AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/matriz-backend-*.jar app.jar

FROM eclipse-temurin:21.0.13_9-jre
WORKDIR /app
RUN useradd --system --no-create-home matriz
COPY --from=build /app/app.jar app.jar
USER matriz
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
