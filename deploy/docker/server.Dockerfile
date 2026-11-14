# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend /src/backend
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f /src/backend/pom.xml -B -DskipTests -pl mop-server -am package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/backend/mop-server/target/mop-server.jar /app/mop-server.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/mop-server.jar"]
