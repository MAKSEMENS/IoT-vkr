# syntax=docker/dockerfile:1.6
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY common/pom.xml common/
COPY event-ingestion-service/pom.xml event-ingestion-service/
COPY state-aggregation-service/pom.xml state-aggregation-service/
COPY anomaly-detection-service/pom.xml anomaly-detection-service/
COPY query-service/pom.xml query-service/
COPY iot-simulator/pom.xml iot-simulator/

RUN mvn -B -ntp -pl state-aggregation-service -am dependency:resolve dependency:resolve-plugins || true

COPY common/src common/src
COPY state-aggregation-service/src state-aggregation-service/src

RUN mvn -B -ntp -pl state-aggregation-service -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/state-aggregation-service/target/state-aggregation-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
