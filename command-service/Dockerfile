# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
COPY command-service/pom.xml command-service/
COPY query-service/pom.xml query-service/
COPY monolith-reference/pom.xml monolith-reference/
COPY command-service/src command-service/src

RUN mvn -B -q -pl command-service -am -DskipTests package

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/command-service/target/command-service.jar /app/app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
