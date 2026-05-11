# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
COPY command-service/pom.xml command-service/
COPY query-service/pom.xml query-service/
COPY monolith-reference/pom.xml monolith-reference/
COPY query-service/src query-service/src

RUN mvn -B -q -pl query-service -am -DskipTests package

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/query-service/target/query-service.jar /app/app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
